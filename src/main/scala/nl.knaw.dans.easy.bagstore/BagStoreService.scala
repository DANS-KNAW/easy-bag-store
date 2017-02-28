/**
 * Copyright (C) 2016-17 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
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
import java.net.{URI, URLConnection}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils
import org.eclipse.jetty.ajp.Ajp13SocketConnector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.joda.time.DateTime
import org.scalatra._
import org.scalatra.servlet.ScalatraListener

import collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class BagStoreService extends BagStoreApp {
  import logger._

  info(s"file permissions for bag files: $bagPermissions")
  info(s"file permissions for exported files: $outputBagPermissions")
  validateSettings()

  private val port = properties.getInt("daemon.http.port")
  val server = new Server(port)
  val context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
  context.setAttribute(CONTEXT_ATTRIBUTE_KEY_BAGSTORE_APP, this)
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

case class BagStoreServlet(app: BagStoreApp) extends ScalatraServlet with DebugEnhancedLogging {
  import app._
  val externalBaseUri = new URI(properties.getString("daemon.external-base-uri"))

  get("/") {
    contentType = "text/plain"
    Ok(s"EASY Bag Store is running ...\nAvaiable stores at <${externalBaseUri.resolve("stores")}>")
  }

  get("/stores") {
    contentType = "text/plain"
    stores.map(s => externalBaseUri.resolve("stores").resolve(s"${s._1}")).map(uri => s"<$uri>").mkString("\n")
  }

  get("/bags") {
    contentType = "text/plain"
    val (includeActive, includeInactive) = includedStates(params.get("state"))
    enumBags(includeActive, includeInactive)
      .map(bagIds => Ok(bagIds.mkString("\n")))
      .onError(e => {
        logger.error("Unexpected type of failure", e)
        InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
      })
  }

  private def includedStates(state: Option[String]): (Boolean, Boolean) = {
    state match {
      case Some("all") => (true, true)
      case Some("inactive") => (false, true)
      case _ => (true, false)
    }
  }

  get("/bags/:uuid") {
    contentType = "text/plain"
    ItemId.fromString(params("uuid"))
      .flatMap(_.toBagId)
      .flatMap(enumFiles(_))
      .map(bagIds => Ok(bagIds.mkString("\n")))
      .onError {
        case _: NoSuchBagException => NotFound()
        case e =>
          logger.error("Unexpected type of failure", e)
          InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
      }
  }

  get("/stores/:bagstore/bags/:uuid") {
    stores.get(params("bagstore"))
      .map(base => {
        ItemId.fromString(params("uuid"))
          .flatMap {
            case bagId: BagId =>
              debug(s"Retrieving item $bagId")
              request.getHeader("Accept") match {
                case "application/zip" => app.get(bagId, response.outputStream, base).map(_ => Ok())
                case "text/plain" | "*/*" | null =>
                  enumFiles(bagId, base).map(files => Ok(files.toList.mkString("\n")))
                case _ => Try { NotAcceptable() }
              }
            case id =>
              logger.error(s"Asked for a bag-id but got something else: $id")
              Try { InternalServerError() }
          }.onError {
            case NoSuchBagException(bagId) => NotFound()
            case NonFatal(e) => logger.error("Unexpected type of failure", e)
              InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
        }
      }).getOrElse(NotFound(s"No such bag-store ${params("bagstore")}"))
  }

  get("/stores/:bagstore/bags/:uuid/*") {
    stores.get(params("bagstore"))
      .map(base => {
        ItemId.fromString(s"""${ params("uuid") }/${ multiParams("splat").head }""")
          .flatMap {
            itemId: ItemId =>
              debug(s"Retrieving item $itemId")
              app.get(itemId, response.outputStream, base)
          }.onError {
          case NoSuchBagException(bagId) => NotFound()
          case NonFatal(e) => logger.error("Error retrieving bag", e)
            InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
        }
      }).getOrElse(NotFound(s"No such bag-store ${params("bagstore")}"))
  }

  put("/stores/:bagstore/bags/:uuid") {
    stores.get(params("bagstore"))
        .map(base => {
          putBag(request.getInputStream, base, params("uuid"))
            .map(bagId => Created(headers = Map("Location" -> appendUriPathToExternalBaseUri(toUri(bagId), params("bagstore")).toASCIIString)))
            .onError {
              case e: IllegalArgumentException if e.getMessage.contains("Invalid UUID string") => BadRequest(s"Invalid UUID: ${params("uuid")}")
              case _: NumberFormatException => BadRequest(s"Invalid UUID: ${params("uuid")}")
              case e: BagIdAlreadyAssignedException => BadRequest(e.getMessage)
              case e =>
                logger.error("Unexpected type of failure", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            }
        }).getOrElse(NotFound(s"No such bag-store ${params("bagstore")}"))
  }

  private def appendUriPathToExternalBaseUri(uri: URI, store: String): URI = {
    new URI(externalBaseUri.getScheme, externalBaseUri.getAuthority, Paths.get(externalBaseUri.getPath, "stores", store, "bags", uri.getPath).toString, null, null)
  }

  private def putBag(is: InputStream, baseDir: Path, uuidStr: String): Try[BagId] = {
    for {
      uuid <- getUuidFromString(uuidStr)
      _ <- checkBagDoesNotExist(BagId(uuid))
      staging <- stageBagZip(is)
      staged <- findBagDir(staging)
      bagId <- add(staged, baseDir, Some(uuid), skipStage = true)
      _ <- Try { FileUtils.deleteDirectory(staging.toFile) }
    } yield bagId
  }

  private def getUuidFromString(s: String): Try[UUID] = Try {
    val uuidStr = params("uuid").filterNot(_ == '-')
    UUID.fromString(formatUuidStrCanonically(uuidStr))
  }
}
