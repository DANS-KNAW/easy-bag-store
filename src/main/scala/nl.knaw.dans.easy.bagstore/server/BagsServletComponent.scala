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

import nl.knaw.dans.easy.bagstore.component.BagStoresComponent
import nl.knaw.dans.easy.bagstore.{ ItemId, NoSuchBagException, NoSuchFileItemException }
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.joda.time.DateTime
import org.scalatra._

import scala.util.control.NonFatal
import scala.util.{ Failure, Try }

trait BagsServletComponent extends DebugEnhancedLogging {
  this: BagStoresComponent =>

  val bagsServlet: BagsServlet

  trait BagsServlet extends ScalatraServlet with ServletUtils {

    get("/") {
      contentType = "text/plain"
      val (includeActive, includeInactive) = includedStates(params.get("state"))
      bagStores.enumBags(includeActive, includeInactive)
        .map(bagIds => Ok(bagIds.mkString("\n")))
        .getOrRecover(e => {
          logger.error("Unexpected type of failure", e)
          InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
        })
    }

    get("/:uuid") {
      contentType = "text/plain"
      val uuidStr = params("uuid")
      ItemId.fromString(uuidStr)
        .recoverWith {
          case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
        }
        .flatMap(_.toBagId)
        .flatMap(bagStores.enumFiles(_, None, includeDirectories = false))
        .map(bagIds => Ok(bagIds.mkString("\n")))
        .getOrRecover {
          case e: IllegalArgumentException => BadRequest(e.getMessage)
          case e: NoSuchBagException => NotFound(e.getMessage)
          case e =>
            logger.error("Unexpected type of failure", e)
            InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
        }
    }

    get("/:uuid/*") {
      val uuidStr = params("uuid")
      multiParams("splat") match {
        case Seq(path) =>
          ItemId.fromString(s"""$uuidStr/${ path }""")
            .recoverWith {
              case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
            }
            .flatMap(itemId => {
              debug(s"Retrieving item $itemId")
              bagStores.getStream(itemId, response.outputStream)
            })
            .map(_ => Ok())
            .getOrRecover {
              case e: IllegalArgumentException => BadRequest(e.getMessage)
              case e: NoSuchBagException => NotFound(e.getMessage)
              case e: NoSuchFileItemException => NotFound(e.getMessage)
              case NonFatal(e) =>
                logger.error("Error retrieving bag", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            }
      }
    }
  }
}
