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
import org.joda.time.DateTime
import org.scalatra._

import scala.util.Failure
import scala.util.control.NonFatal

trait FilesizesServletComponent {
  this: BagStoresComponent =>

  val filesizesServlet: FilesizesServlet

  trait FilesizesServlet extends ScalatraServlet with ServletUtils {

    get("/:uuid/*") {
      val uuidStr = params("uuid")
      multiParams("splat") match {
        case Seq(path) =>
          ItemId.fromString(s"""$uuidStr/${ path }""")
            .recoverWith {
              case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
            }
            .flatMap(itemId => bagStores.getSize(itemId))
            .map(size => Ok(body = size))
            .getOrRecover {
              case e: IllegalArgumentException => BadRequest(e.getMessage)
              case e: NoSuchBagException => NotFound(e.getMessage)
              case e: NoSuchItemException => NotFound(e.getMessage)
              case e: NoRegularFileException => NotFound(e.getMessage)
              case NonFatal(e) =>
                logger.error("Error retrieving bag", e)
                InternalServerError(s"[${ new DateTime() }] Unexpected type of failure. Please consult the logs")
            }
        case p =>
          logger.error(s"Unexpected path: $p")
          InternalServerError("Unexpected path")
      }
    }
  }
}
