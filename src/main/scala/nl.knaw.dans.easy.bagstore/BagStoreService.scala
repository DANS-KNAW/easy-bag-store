/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.bagstore

import java.io.InputStream
import java.net.URI
import java.nio.file.Paths
import java.util.UUID

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.eclipse.jetty.ajp.Ajp13SocketConnector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.joda.time.DateTime
import org.scalatra._
import org.scalatra.servlet.ScalatraListener

import scala.util.{Failure, Success, Try}

class BagStoreService extends BagStoreApp with DebugEnhancedLogging {
  import logger._

  info(s"base directory: $baseDir")
  info(s"base URI: $baseUri")
  info(s"file permissions for bag files: $bagPermissions")
  info(s"file permissions for exported files: $outputBagPermissions")
  validateSettings()

  private val port = properties.getInt("daemon.http.port")
  val server = new Server(port)
  val context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
  context.addEventListener(new ScalatraListener())
  server.setHandler(context)
  info(s"HTTP port is $port")

  if (properties.containsKey("daemon.ajp.port")) {
    val ajp = new Ajp13SocketConnector()
    val ajpPort = properties.getInt("daemon.ajp.port")
    ajp.setPort(ajpPort)
    server.addConnector(ajp)
    info(s"AJP port is $ajpPort")
  }

  def start(): Try[Unit] = Try {
    info("Starting HTTP service ...")
    server.start()
  }

  def stop(): Try[Unit] = Try {
    info("Stopping HTTP service ...")
    server.stop()
  }

  def destroy(): Try[Unit] = Try {
    server.destroy()
  }
}

object BagStoreService extends App with DebugEnhancedLogging {
  import logger._
  val service = new BagStoreService()
  Runtime.getRuntime.addShutdownHook(new Thread("service-shutdown") {
    override def run(): Unit = {
      info("Stopping service ...")
      service.stop()
      info("Cleaning up ...")
      service.destroy()
      info("Service stopped.")
    }
  })
  service.start()
  info("Service started ...")
}

class BagStoreServlet extends ScalatraServlet with BagStoreApp with DebugEnhancedLogging {
  val externalBaseUri = new URI(properties.getString("daemon.external-base-uri"))

  get("/") {
    contentType = "text/plain"
    Try {
      enumBags().iterator.toList.mkString("\n")
    } match {
      case Success(bagIds) => Ok(bagIds)
      case Failure(e) =>
        logger.error("Unexpected type of failure", e)
        InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
    }
  }

  get("/:uuid") {
    contentType = "text/plain"
    ItemId.fromString(params("uuid"))
      .flatMap(ItemId.toBagId)
      .map(enumFiles)
      .map(_.mkString("\n")) match {
      case Success(fileIds) => Ok(fileIds)
      case Failure(e: NoSuchBagException) => NotFound()
      case Failure(e) =>
        logger.error("Unexpected type of failure", e)
        InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
    }
  }

  // TODO: implement content-negatiation: text/plain for enumFiles, application/zip for zipped bag

  put("/:uuid") {
    putBag(request.getInputStream, params("uuid"))
    match {
      case Success(bagId) => Created(headers = Map("Location" -> appendUriPathToExternalBaseUri(toUri(bagId)).toASCIIString))
      case Failure(e: IllegalArgumentException) if e.getMessage.contains("Invalid UUID string") => BadRequest("Invalid UUID")
      case Failure(e: NumberFormatException) => BadRequest("Invalid UUID")
      case Failure(e: BagIdAlreadyAssignedException) => BadRequest(e.getMessage)
      case Failure(e) =>
        logger.error("Unexpected type of failure", e)
        InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
    }
  }

  private def appendUriPathToExternalBaseUri(uri: URI): URI = {
    new URI(externalBaseUri.getScheme, externalBaseUri.getAuthority, Paths.get(externalBaseUri.getPath, uri.getPath).toString, null, null)
  }

  private def putBag(is: InputStream, uuidStr: String): Try[BagId] = {
    for {
      uuid <- getUuidFromString(params("uuid"))
      _ <- checkBagDoesNotExist(BagId(uuid))
      staged <- stageBagZip(request.getInputStream)
      bagId <- add(staged, Some(uuid), skipStage = true)
    } yield bagId
  }

  private def getUuidFromString(s: String): Try[UUID] = Try {
    val uuidStr = params("uuid").filterNot(_ == '-')
    UUID.fromString(formatUuidStrCanonically(uuidStr))
  }
}