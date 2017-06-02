/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.nio.file.{ Path, Paths }
import java.util.UUID

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils
import org.eclipse.jetty.ajp.Ajp13SocketConnector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.joda.time.DateTime
import org.scalatra._
import org.scalatra.servlet.ScalatraListener

import scala.util.Try
import scala.util.control.NonFatal

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
  // TODO: Refactor away repetitive code in this class
  import app._
  val externalBaseUri = new URI(properties.getString("daemon.external-base-uri"))

  get("/") {
    contentType = "text/plain"
    Ok(s"EASY Bag Store is running ...\nAvaiable stores at <${externalBaseUri.resolve("stores")}>")
  }

  get("/stores") {
    contentType = "text/plain"
    stores.map(s => externalBaseUri.resolve("stores/").resolve(s"${s._1}")).map(uri => s"<$uri>").mkString("\n")
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

  // TODO: Boolean -> type aliases.
  private def includedStates(state: Option[String]): (Boolean, Boolean) = {
    state match {
      case Some("all") => (true, true)
      case Some("inactive") => (false, true)
      case _ => (true, false)
    }
  }

  get("/bags/:uuid") {
    val uuid = params("uuid")
    contentType = "text/plain"
    ItemId.fromString(uuid)
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

  get("/stores/:bagstore/bags") {
    val bagstore = params("bagstore")
    stores.get(bagstore)
      .map(base => {
        val (includeActive, includeInactive) = includedStates(params.get("state"))
        enumBags(includeActive, includeInactive, base)
          .map(bagIds => Ok(bagIds.mkString("\n")))
          .onError(e => {
            logger.error("Unexpected type of failure", e)
            InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
          })
      }).getOrElse(NotFound(s"No such bag-store $bagstore"))
  }

  get("/stores/:bagstore/bags/:uuid") {
    val bagstore = params("bagstore")
    val uuid = params("uuid")
    stores.get(bagstore)
      .map(base => {
        ItemId.fromString(uuid)
          .flatMap {
            case bagId: BagId =>
              debug(s"Retrieving item $bagId")
              request.getHeader("Accept") match {
                case "application/zip" => app.getWithOutputStream(bagId, base, response.outputStream).map(_ => Ok())
                case "text/plain" | "*/*" | null =>
                  enumFiles(bagId, base).map(files => Ok(files.toList.mkString("\n")))
                case _ => Try { NotAcceptable() }
              }
            case id =>
              logger.error(s"Asked for a bag-id but got something else: $id")
              Try { InternalServerError() }
          }.onError {
          case e: NoSuchBagException => NotFound(e.getMessage)
            case NonFatal(e) =>
              logger.error("Unexpected type of failure", e)
              InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
        }
      }).getOrElse(NotFound(s"No such bag-store $bagstore"))
  }

  get("/stores/:bagstore/bags/:uuid/*") {
    val bagstore = params("bagstore")
    val uuid = params("uuid")
    stores.get(bagstore)
      .map(base => ItemId.fromString(s"""$uuid/${ multiParams("splat").head }""")
        .flatMap(itemId => {
          debug(s"Retrieving item $itemId")
          app.getWithOutputStream(itemId, base, response.outputStream)
        })
        .map(_ => Ok())
        .onError {
          case e: NoSuchBagException => NotFound(e.getMessage)
          case e: NoSuchFileException => NotFound(e.getMessage)
          case NonFatal(e) =>
            logger.error("Error retrieving bag", e)
            InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
        })
      .getOrElse(NotFound(s"No such bag-store $bagstore"))
  }

  put("/stores/:bagstore/bags/:uuid") {
    val bagstore = params("bagstore")
    val uuid = params("uuid")
    withBagStore(bagstore) { base =>
      putBag(request.getInputStream, base, uuid)
        .map(bagId => Created(headers = Map("Location" -> appendUriPathToExternalBaseUri(toUri(bagId), bagstore).toASCIIString)))
        .onError {
          case e: IllegalArgumentException if e.getMessage.contains("Invalid UUID string") => BadRequest(s"Invalid UUID: $uuid")
          case _: NumberFormatException => BadRequest(s"Invalid UUID: $uuid")
          case e: BagIdAlreadyAssignedException => BadRequest(e.getMessage)
          case e =>
            logger.error("Unexpected type of failure", e)
            InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
        }
    }
  }

  // TODO: use in all REST methods
  private def withBagStore(name: String)(f: Path => ActionResult): ActionResult = {
    stores.get(name).map(f).getOrElse(NotFound(s"No such bag-store: $name"))
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

  private def getUuidFromString(uuid: String): Try[UUID] = Try {
    val uuidStr = uuid.filterNot(_ == '-')
    UUID.fromString(formatUuidStrCanonically(uuidStr))
  }
}
