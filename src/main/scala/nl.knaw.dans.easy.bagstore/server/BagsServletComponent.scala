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

import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.easy.bagstore.component.BagStoresComponent
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.logging.servlet._
import org.joda.time.DateTime
import org.scalatra._

import scala.language.postfixOps
import scala.util.Failure
import scala.util.control.NonFatal

trait BagsServletComponent extends DebugEnhancedLogging {
  this: BagStoresComponent =>

  val bagsServlet: BagsServlet

  trait BagsServlet extends ScalatraServlet
    with ServletLogger
    with PlainLogFormatter
    with ServletUtils {

    get("/") {
      contentType = "text/plain"
      val (includeActive, includeInactive) = includedStates(params.get("state"))
      bagStores.enumBags(includeActive, includeInactive)
        .map(bagIds => Ok(bagIds.mkString("\n")))
        .getOrRecover(e => {
          logger.error("Unexpected type of failure", e)
          InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
        }) logResponse
    }

    get("/:uuid") {
      val uuidStr = params("uuid")
      val accept = request.getHeader("Accept")
      ItemId.fromString(uuidStr)
        .recoverWith {
          case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
        }
        .flatMap(_.toBagId)
        .flatMap(bagId => {
          if (accept == "text/plain") bagStores
            .enumFiles(bagId, includeDirectories = false)
            .map(files => Ok(files.toList.mkString("\n")))
          else bagStores
            .copyToStream(bagId, accept, response.outputStream)
            .map(_ => Ok())
        })
        .getOrRecover {
          case e: IllegalArgumentException => BadRequest(e.getMessage)
          case e: NoRegularFileException => BadRequest(e.getMessage)
          case e: NoSuchBagException => NotFound(e.getMessage)
          case e: InactiveException => Gone(e.getMessage)
          case e =>
            logger.error("Unexpected type of failure", e)
            InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
        } logResponse
    }

    get("/:uuid/*") {
      val uuidStr = params("uuid")
      (multiParams("splat") match {
        case Seq(path) =>
          ItemId.fromString(s"""$uuidStr/${ path }""")
            .recoverWith {
              case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
            }
            .flatMap(itemId => {
              debug(s"Retrieving item $itemId")
              bagStores.copyToStream(itemId, request.header("Accept").flatMap(acceptToArchiveStreamType), response.outputStream)
            })
            .map(_ => Ok())
            .getOrRecover {
              case e: IllegalArgumentException => BadRequest(e.getMessage)
              case e: NoRegularFileException => BadRequest(e.getMessage)
              case e: NoSuchBagException => NotFound(e.getMessage)
              case e: NoSuchFileItemException => NotFound(e.getMessage)
              case e: NoSuchItemException => NotFound(e.getMessage)
              case e: InactiveException => Gone(e.getMessage)
              case NonFatal(e) =>
                logger.error("Error retrieving bag", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            }
        case p =>
          logger.error(s"Unexpected path: $p")
          InternalServerError("Unexpected path")
      }) logResponse
    }
  }
}
