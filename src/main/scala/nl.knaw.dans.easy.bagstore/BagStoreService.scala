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

import scala.util.{ Failure, Success, Try }

trait BagStoreServiceComponent extends BagStoreLifeCycle with DebugEnhancedLogging {
  this: BagStoreApp =>

  def startup(): Try[Unit] = {
    bagStoreServer.start()
  }

  def shutdown(): Try[Unit] = {
    bagStoreServer.stop()
  }

  def destroy(): Try[Unit] = {
    bagStoreServer.destroy()
  }

  val bagStoreServer: BagStoreServer

  class BagStoreServer() {
    import logger._

    info(s"base directory: ${context.baseDir}")
    info(s"base URI: ${context.baseUri}")
    info(s"file permissions for bag files: ${context.bagPermissions}")
    info(s"file permissions for exported files: ${outputContext.outputBagPermissions}")
    validateSettings()

    private val port = properties.getInt("daemon.http.port")
    val server = new Server(port)
    val servletContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    servletContext.addEventListener(new ScalatraListener())
    server.setHandler(servletContext)
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
}

// singleton for BagStoreServer and BagStoreServlet
object BagStoreServiceComponent extends BagStoreServiceComponent with BagStoreApp {
  val bagStoreServer: BagStoreServer = new BagStoreServer
}

object BagStoreService extends App with DebugEnhancedLogging {
  import logger._
  val service = BagStoreServiceComponent
  Runtime.getRuntime.addShutdownHook(new Thread("service-shutdown") {
    override def run(): Unit = {
      val shutdown = for {
        _ <- Try { info("Stopping service ...") }
        _ <- service.shutdown()
        _ <- Try { info("Cleaning up ...") }
        _ <- service.destroy()
      } yield ()

      shutdown match {
        case Success(_) => info("Service stopped.")
        case Failure(e) => error("Error while stopping the service", e)
      }
    }
  })

  service.startup() match {
    case Success(_) => info("Service started ...")
    case Failure(e) => error("Service did not start", e); System.exit(-1)
  }
}

class BagStoreServlet extends ScalatraServlet with DebugEnhancedLogging {
  val service = BagStoreServiceComponent
  import service._

  val externalBaseUri = new URI(properties.getString("daemon.external-base-uri"))

  get("/") {
    contentType = "text/plain"
    enum.enumBags()
      .map(bagIds => Ok(bagIds.mkString("\n")))
      .onError(e => {
        logger.error("Unexpected type of failure", e)
        InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
      })
  }

  get("/:uuid") {
    contentType = "text/plain"
    ItemId.fromString(params("uuid"))
      .flatMap(_.toBagId)
      .flatMap(enum.enumFiles)
      .map(bagIds => Ok(bagIds.mkString("\n")))
      .onError {
        case _: NoSuchBagException => NotFound()
        case e =>
          logger.error("Unexpected type of failure", e)
          InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
      }
  }

  // TODO: implement content-negatiation: text/plain for enumFiles, application/zip for zipped bag

  put("/:uuid") {
    putBag(request.getInputStream, params("uuid"))
      .map(bagId => Created(headers = Map("Location" -> appendUriPathToExternalBaseUri(context.toUri(bagId)).toASCIIString)))
      .onError {
        case e: IllegalArgumentException if e.getMessage.contains("Invalid UUID string") => BadRequest("Invalid UUID")
        case _: NumberFormatException => BadRequest("Invalid UUID")
        case e: BagIdAlreadyAssignedException => BadRequest(e.getMessage)
        case e =>
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
      _ <- context.checkBagDoesNotExist(BagId(uuid))
      staged <- context.stageBagZip(request.getInputStream)
      bagId <- add.add(staged, Some(uuid), skipStage = true)
    } yield bagId
  }

  private def getUuidFromString(s: String): Try[UUID] = Try {
    val uuidStr = params("uuid").filterNot(_ == '-')
    UUID.fromString(context.formatUuidStrCanonically(uuidStr))
  }
}
