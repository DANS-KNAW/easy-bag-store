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

import org.apache.commons.daemon.{Daemon, DaemonContext}
import org.slf4j.{Logger, LoggerFactory}

class ServiceStarter extends Daemon {
  var log: Logger = _ // Not loading logger yet, to avoid possibility of errors before init is called
  var bagStoreService: BagStoreServiceComponent = _ // Idem

  def init(ctx: DaemonContext) = {
    log = LoggerFactory.getLogger(getClass)
    log.info("Initializing service...")
    bagStoreService = BagStoreServiceComponent
    log.info("Service initialized.")
  }

  def start(): Unit = {
    log.info("Starting service...")
    bagStoreService.startup()
    log.info("Service started.")
  }

  def stop(): Unit = {
    log.info("Stopping service...")
    bagStoreService.shutdown()
  }

  def destroy(): Unit = {
    bagStoreService.destroy()
    log.info("Service stopped.")
  }
}
