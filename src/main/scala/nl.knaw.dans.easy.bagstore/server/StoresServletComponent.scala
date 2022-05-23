/*
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
import java.nio.file.Files
import java.util.UUID

import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.easy.bagstore.component.{ BagStoresComponent, FileSystemComponent }
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.string._
import nl.knaw.dans.lib.logging.servlet._
import nl.knaw.dans.lib.logging.servlet.masked.MaskedAuthorizationHeader
import org.joda.time.DateTime
import org.scalatra._

import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

trait StoresServletComponent {
  this: BagStoresComponent with FileSystemComponent =>

  val storesServlet: StoresServlet

  trait StoresServlet extends ScalatraServlet
    with ServletUtils
    with BagStoreAuthenticationSupport
    with ServletLogger
    with PlainLogFormatter
    with MaskedAuthorizationHeader
    with LogResponseBodyOnError {

    val externalBaseUri: URI

    get("/") {
      contentType = "text/plain"
      Ok(bagStores.storeShortnames
        .keys
        .map(store => s"<${ externalBaseUri.resolve(s"stores/$store") }>")
        .mkString("\n")
      )
    }

    get("/:bagstore") {
      contentType = "text/plain"
      val bagstore = params("bagstore")
      bagStores.getBaseDirByShortname(bagstore)
        .map(_ => Ok {
          s"""Bag store '$bagstore'.
             |Bags for this store at <${ externalBaseUri.resolve(s"stores/$bagstore/bags") }>
             |""".stripMargin
        })
        .getOrElse(NotFound(s"No such bag-store: $bagstore"))
    }

    get("/:bagstore/bags") {
      val bagstore = params("bagstore")
      bagStores.getBaseDirByShortname(bagstore)
        .map(baseDir => {
          val (includeActive, includeInactive) = includedStates(params.get("state"))
          bagStores.enumBags(includeActive, includeInactive, Some(baseDir))
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
      val accept = request.getHeader("Accept")
      bagStores.getBaseDirByShortname(bagstore)
        .map(baseDir => ItemId.fromString(uuidStr)
          .recoverWith {
            // TODO: Is this mapping from IAE to IAE really necessary??
            case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"Invalid UUID string: $uuidStr"))
          }
          .flatMap(_.toBagId)
          .flatMap(bagId => {
            if (accept == "text/plain") bagStores
              .enumFiles(bagId, includeDirectories = false, Some(baseDir))
              .map(files => Ok(files.toList.mkString("\n")))
            else bagStores
              .copyToStream(bagId, accept, response.outputStream, Some(baseDir))
              .map(_ => Ok())
          })
          .getOrRecover {
            case e: NoBagIdException => InternalServerError(e.getMessage)
            case e: InactiveException => Gone(e.getMessage)
            case e: IllegalArgumentException => BadRequest(e.getMessage)
            case e: NoRegularFileException => BadRequest(e.getMessage)
            case e: NoSuchItemException => NotFound(e.getMessage)
            case e: NoSuchBagException => NotFound(e.getMessage)
            case e: NoSuchFileItemException => NotFound(e.getMessage)
            case NonFatal(e) =>
              logger.error("Error retrieving bag", e)
              InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
          })
        .getOrElse(NotFound(s"No such bag-store: $bagstore"))
    }

    get("/:bagstore/bags/:uuid/*") {
      val bagstore = params("bagstore")
      val uuidStr = params("uuid")
      multiParams("splat") match {
        case Seq(path) =>
          bagStores.getBaseDirByShortname(bagstore)
            .map(baseDir => ItemId.fromString(s"""$uuidStr/${ path }""")
              .recoverWith {
                case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"Invalid UUID string: $uuidStr"))
              }
              .flatMap(itemId => {
                debug(s"Retrieving item $itemId")
                bagStores.copyToStream(itemId, request.header("Accept").flatMap(acceptToArchiveStreamType), response.outputStream, Some(baseDir))
              })
              .map {
                case Some(filePath) =>
                  response.setContentLengthLong(Files.size(filePath))
                  Ok(filePath.toFile)
                case None => Ok()
              }
              .getOrRecover {
                case e: IllegalArgumentException => BadRequest(e.getMessage)
                case e: NoRegularFileException => BadRequest(e.getMessage)
                case e: NoSuchItemException => NotFound(e.getMessage)
                case e: NoSuchBagException => NotFound(e.getMessage)
                case e: NoSuchFileItemException => NotFound(e.getMessage)
                case e: InactiveException => Gone(e.getMessage)
                case NonFatal(e) =>
                  logger.error("Error retrieving bag", e)
                  InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
              })
            .getOrElse(NotFound(s"No such bag-store: $bagstore"))
        case p =>
          logger.error(s"Unexpected path: $p")
          InternalServerError("Unexpected path")
      }
    }

    put("/:bagstore/bags/:uuid") {
      trace(())
      basicAuth()
      debug("Authenticated")
      val bagStore = params("bagstore")
      val uuidStr = params("uuid")
      val requestContentType = Option(request.getHeader("Content-Type"))
      bagStores.getBaseDirByShortname(bagStore)
        .map(base => {
          Try(getUUID(uuidStr))
            .flatMap(validateContentTypeHeader(requestContentType, _))
            .flatMap(bagStores.putBag(request.getInputStream, base, _))
            .map(bagId => Created(headers = Map(
              "Location" -> externalBaseUri.resolve(s"stores/$bagStore/bags/${ fileSystem.toUri(bagId).getPath }").toASCIIString
            )))
            .getOrRecover {
              case e: CompositeException if e.throwables.exists(_.isInstanceOf[IncorrectNumberOfFilesInBagZipRootException]) => BadRequest(e.getMessage())
              case e: UnsupportedMediaTypeException => UnsupportedMediaType(e.getMessage)
              case e: IllegalArgumentException => BadRequest(e.getMessage)
              case e: UUIDError => BadRequest(e.getMessage)
              case e: BagIdAlreadyAssignedException => BadRequest(e.getMessage)
              case e: NoBagException => BadRequest(e.getMessage)
              case e: InvalidBagException => BadRequest(e.getMessage)
              case e: IncorrectNumberOfFilesInBagZipRootException => BadRequest(e.getMessage)
              case e =>
                logger.error("Unexpected type of failure", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            }
        })
        .getOrElse(NotFound(s"No such bag-store: $bagStore"))
    }
  }

  private def validateContentTypeHeader(requestContentType: Option[String], uuid: UUID): Try[UUID] = {
    requestContentType.withFilter(_.equalsIgnoreCase("application/zip"))
      .map(_ => Success(uuid))
      .getOrElse(Failure(UnsupportedMediaTypeException(requestContentType.getOrElse("none"), "application/zip")))
  }
}
