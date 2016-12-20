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

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.joda.time.DateTime
import org.scalatra._
import org.scalatra.servlet.ScalatraListener

import scala.util.{Failure, Success, Try}

class BagStoreService extends BagStoreApp with DebugEnhancedLogging {
  import logger._

  private val port = properties.getInt("daemon.port")
  val server = new Server(port)
  val context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
  context.addEventListener(new ScalatraListener())
  server.setHandler(context)
  info(s"listening on port $port")

  def start(): Try[Unit] = Try {
    server.start()
  }

  def stop(): Try[Unit] = Try {
    server.stop()
  }

  def destroy(): Try[Unit] = Try {
    server.destroy()
  }
}

object BagStoreService extends App {
  new BagStoreService().start()
  println("Service started ...")
}

class BagStoreServlet extends ScalatraServlet with BagStoreApp with DebugEnhancedLogging {

  get("/") {
    // Enumerate all bag-ids
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
    // Check MD-5
    // Unzip/tar body (directly to staging area?)
    // prune
    // ADD(body, uuid)
  }

}