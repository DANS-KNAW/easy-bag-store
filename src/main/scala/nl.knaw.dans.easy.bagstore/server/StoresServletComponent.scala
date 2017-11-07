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
package nl.knaw.dans.easy.bagstore.server

import java.net.URI
import java.util.UUID

import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.easy.bagstore.component.{ BagStoresComponent, FileSystemComponent }
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.joda.time.DateTime
import org.scalatra._

import scala.util.control.NonFatal
import scala.util.{ Failure, Try }

trait StoresServletComponent extends DebugEnhancedLogging {
  this: BagStoresComponent with FileSystemComponent =>

  val storesServlet: StoresServlet

  trait StoresServlet extends ScalatraServlet with ServletUtils {

    val externalBaseUri: URI

    get("/") {
      contentType = "text/plain"
      bagStores.stores
        .keys
        .map(store => s"<${ externalBaseUri.resolve(s"stores/$store") }>")
        .mkString("\n")
    }

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
      val uuidStr = params("uuid")
      bagStores.getStore(bagstore)
        .map(base => {
          ItemId.fromString(uuidStr)
            .recoverWith {
              case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
            }
            .flatMap {
              case bagId: BagId =>
                debug(s"Retrieving item $bagId")
                request.getHeader("Accept") match {
                  case "application/zip" => base.get(bagId, response.outputStream)//.map(_ => Ok())
                  case "text/plain" | "*/*" | null => base.enumFiles(bagId).map(files => Ok(files.toList.mkString("\n")))
                  case _ => Try { NotAcceptable() }
                }
              case id =>
                logger.error(s"Asked for a bag-id but got something else: $id")
                Try { InternalServerError() }
            }
            .getOrRecover {
              case e: IllegalArgumentException => BadRequest(e.getMessage)
              case e: NoSuchBagException => NotFound(e.getMessage)
              case NonFatal(e) =>
                logger.error("Unexpected type of failure", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            }
        })
        .getOrElse(NotFound(s"No such bag-store: $bagstore"))
    }

    get("/:bagstore/bags/:uuid/*") {
      val bagstore = params("bagstore")
      val uuidStr = params("uuid")
      multiParams("splat") match {
        case Seq(path) =>
          bagStores.getStore(bagstore)
            .map(base => ItemId.fromString(s"""$uuidStr/${ path }""")
              .recoverWith {
                case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
              }
              .flatMap(itemId => {
                debug(s"Retrieving item $itemId")
                base.get(itemId, response.outputStream)
              })
              .map(_ => Ok())
              .getOrRecover {
                case e: IllegalArgumentException => BadRequest(e.getMessage)
                case e: NoSuchBagException => NotFound(e.getMessage)
                case e: NoSuchFileException => NotFound(e.getMessage)
                case NonFatal(e) =>
                  logger.error("Error retrieving bag", e)
                  InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
              })
            .getOrElse(NotFound(s"No such bag-store: $bagstore"))
      }
    }

    put("/:bagstore/bags/:uuid") {
      val bagstore = params("bagstore")
      val uuidStr = params("uuid")
      bagStores.getStore(bagstore)
        .map(base => {
          Try { UUID.fromString(uuidStr) }
            .recoverWith {
              case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
            }
            .flatMap(uuid => {
              implicit val baseDir: BaseDir = base.baseDir
              bagStores.putBag(request.getInputStream, base, uuid)
            })
            .map(bagId => Created(headers = Map(
              "Location" -> externalBaseUri.resolve(s"stores/$bagstore/bags/${ fileSystem.toUri(bagId).getPath }").toASCIIString
            )))
            .getOrRecover {
              case e: IllegalArgumentException => BadRequest(e.getMessage)
              case e: BagIdAlreadyAssignedException => BadRequest(e.getMessage)
              case e: NoBagException => BadRequest(e.getMessage)
              case e: InvalidBagException => BadRequest(e.getMessage)
              case e =>
                logger.error("Unexpected type of failure", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            }
        })
        .getOrElse(NotFound(s"No such bag-store: $bagstore"))
    }
  }
}
