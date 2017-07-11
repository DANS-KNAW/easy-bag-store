package nl.knaw.dans.easy.bagstore.server

import java.net.URI
import java.nio.file.Paths

import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.easy.bagstore.component.BagStoresComponent
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.joda.time.DateTime
import org.scalatra._

import scala.util.Try
import scala.util.control.NonFatal

trait StoresServletComponent extends DebugEnhancedLogging {
  this: BagStoresComponent =>

  val storesServlet: StoresServlet

  trait StoresServlet extends ScalatraServlet with ServletUtils {

    val externalBaseUri: URI

    get("/:bagstore/bags") {
      val bagstore = params("bagstore")
      bagStores.getStore(bagstore)
        .map(base => {
          val (includeActive, includeInactive) = includedStates(params.get("state"))
          base.enumBags(includeActive, includeInactive)
            .map(bagIds => Ok(bagIds.mkString("\n")))
            .getOrRecover(e => {
              logger.error(s"Unexpected type of failure: ${ e.getMessage }", e)
              InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            })
        })
        .getOrElse(NotFound(s"No such bag-store: $bagstore"))
    }

    get("/:bagstore/bags/:uuid") {
      val bagstore = params("bagstore")
      val uuid = params("uuid")
      bagStores.getStore(bagstore)
        .map(base => {
          ItemId.fromString(uuid)
            .flatMap {
              case bagId: BagId =>
                debug(s"Retrieving item $bagId")
                request.getHeader("Accept") match {
                  case "application/zip" => base.get(bagId, response.outputStream).map(_ => Ok())
                  case "text/plain" | "*/*" | null => base.enumFiles(bagId).map(files => Ok(files.toList.mkString("\n")))
                  case _ => Try { NotAcceptable() }
                }
              case id =>
                logger.error(s"Asked for a bag-id but got something else: $id")
                Try { InternalServerError() }
            }
            .getOrRecover {
              case e: NoSuchBagException => NotFound(e.getMessage)
              case NonFatal(e) =>
                logger.error("Unexpected type of failure", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            }
        })
        .getOrElse(NotFound(s"No such bag-store $bagstore"))
    }

    get("/:bagstore/bags/:uuid/*") {
      val bagstore = params("bagstore")
      val uuid = params("uuid")
      bagStores.getStore(bagstore)
        .map(base => ItemId.fromString(s"""$uuid/${ multiParams("splat").head }""")
          .flatMap(itemId => {
            debug(s"Retrieving item $itemId")
            base.get(itemId, response.outputStream)
          })
          .map(_ => Ok())
          .getOrRecover {
            case e: NoSuchBagException => NotFound(e.getMessage)
            case e: NoSuchFileException => NotFound(e.getMessage)
            case NonFatal(e) =>
              logger.error("Error retrieving bag", e)
              InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
          })
        .getOrElse(NotFound(s"No such bag-store $bagstore"))
    }

    put("/:bagstore/bags/:uuid") {
      val bagstore = params("bagstore")
      val uuid = params("uuid")
      bagStores.getStore(bagstore)
        .map(base =>
          bagStores.putBag(request.getInputStream, base, uuid)
            .map(bagId => Created(headers = Map("Location" -> appendUriPathToExternalBaseUri(base.fileSystem.toUri(bagId), bagstore).toASCIIString)))
            .getOrRecover {
              case e: IllegalArgumentException if e.getMessage.contains("Invalid UUID string") => BadRequest(s"Invalid UUID: $uuid")
              case _: NumberFormatException => BadRequest(s"Invalid UUID: $uuid")
              case e: BagIdAlreadyAssignedException => BadRequest(e.getMessage)
              case e =>
                logger.error("Unexpected type of failure", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            })
        .getOrElse(NotFound(s"No such bag-store: $bagstore"))
    }

    private def appendUriPathToExternalBaseUri(uri: URI, store: String): URI = {
      new URI(
        externalBaseUri.getScheme,
        externalBaseUri.getAuthority,
        Paths.get(externalBaseUri.getPath, "stores", store, "bags", uri.getPath).toString,
//        externalBaseUri.resolve("stores").resolve(store).resolve("bags").resolve(uri.getPath).toString, // TODO is this the same as above?
        null,
        null)
    }
  }
}
