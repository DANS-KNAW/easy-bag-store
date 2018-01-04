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
package nl.knaw.dans.easy.bagstore.service

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.daemon.{ Daemon, DaemonContext }

import scala.util.control.NonFatal

class ServiceStarter extends Daemon with DebugEnhancedLogging {

  var service: ServiceWiring = _

  def init(context: DaemonContext): Unit = {
    logger.info("Initializing service...")

    service = new ServiceWiring {}

    logger.info("Service initialized.")
  }

  def start(): Unit = {
    logger.info("Starting service...")

    service.server.start()
      .doIfSuccess(_ => logger.info("Service started."))
      .doIfFailure {
        case NonFatal(e) => logger.info(s"Service startup failed: ${ e.getMessage }", e)
      }
      .unsafeGetOrThrow
  }

  def stop(): Unit = {
    logger.info("Stopping service...")
    service.server.stop()
      .flatMap(_ => service.bagFacade.stop())
      .doIfSuccess(_ => logger.info("Cleaning up ..."))
      .doIfFailure {
        case NonFatal(e) => logger.error(s"Service stop failed: ${ e.getMessage }", e)
      }
      .unsafeGetOrThrow
  }

  def destroy(): Unit = {
    service.server.destroy()
      .doIfSuccess(_ => logger.info("Service stopped."))
      .doIfFailure {
        case e => logger.error(s"Service destroy failed: ${ e.getMessage }", e)
      }
      .unsafeGetOrThrow
  }
}
