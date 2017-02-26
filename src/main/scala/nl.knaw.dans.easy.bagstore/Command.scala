/**
 * Copyright (C) 2016-17 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
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

import java.util.UUID

import nl.knaw.dans.lib.error.TraversableTryExtensions

import scala.util.{Success, Try}

object Command extends App with BagStoreApp {
  import scala.language.reflectiveCalls
  type FeedBackMessage = String

  val opts = CommandLineOptions(args, properties)
  opts.verify()
  implicit val baseDir = baseDir2

  // optBaseDir = van de cmdline indien opgegeven, anders van stores als er maar één is, anders None

  val result: Try[FeedBackMessage] = opts.subcommand match {
    case Some(opts.list) => Try {
      "Configured bag-stores:\n" +
      stores.map {
        case (shortname, base) => s"- $shortname -> $base"
      }.mkString("\n")
    }
    case Some(cmd @ opts.add) =>
      val bagUuid = cmd.uuid.toOption.map(UUID.fromString)
      // als optBaseDir None is -> vraag interactief
      add(cmd.bag(), baseDir2, bagUuid).map(bagId => s"Added Bag with bag-id: $bagId")
    case Some(cmd @ opts.get) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        store <- get(itemId, cmd.outputDir(), opts.bagStoreBaseDir.toOption)
        storeName = stores.find { case (name, base) => base == store }.map(_._1).getOrElse(store)
      } yield s"Retrieved item with item-id: $itemId to ${cmd.outputDir()} from BagStore: $storeName"
    case Some(cmd @ opts.enum) =>
      cmd.bagId.toOption
        .map(s => for {
            itemId <- ItemId.fromString(s)
            bagId <- itemId.toBagId
            files <- enumFiles(bagId)
          } yield files.foreach(println(_)))
        .getOrElse {
          val includeActive = cmd.all() || !cmd.inactive()
          val includeInactive = cmd.all() || cmd.inactive()
          enumBags(includeActive, includeInactive).map(_.foreach(println(_)))
        }
        .map(_ => "Done enumerating")
    case Some(cmd @ opts.`deactivate`) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- deactivate(bagId)
      } yield s"Marked ${cmd.bagId()} as deleted"
    case Some(cmd @ opts.`reactivate`) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- reactivate(bagId)
      } yield s"Removed deleted mark from ${cmd.bagId()}"
    case Some(cmd @ opts.prune) =>
      // als optBaseDir None is -> vraag interactief. Opmerking: bags must not use local references to other bag stores.
      cmd.referenceBags.toOption
        .map(refBags => refBags.map(ItemId.fromString).map(_.flatMap(_.toBagId))
          .collectResults
          .flatMap(refBagIds => prune(cmd.bagDir(), /* baseDir, */ refBagIds: _*))
          .map(_ => "Done pruning"))
        .getOrElse(Success("No reference Bags specified: nothing to do"))
    case Some(cmd @ opts.complete) =>
      complete(cmd.bagDir())
        .map(_ => s"Done completing ${cmd.bagDir()}")
    case Some(cmd @ opts.validate) =>
      isVirtuallyValid(cmd.bagDir())
        .map(valid => s"Done validating. Result: virtually-valid = $valid")
    case Some(cmd @ opts.runService) => runAsService()
    case _ => throw new IllegalArgumentException(s"Unknown command: ${opts.subcommand}")
      Try { "Unknown command" }
  }

  result.map(msg => println(s"OK: $msg"))
    .onError(e => println(s"FAILED: ${e.getMessage}"))

  private def runAsService(): Try[FeedBackMessage] = Try {
    import logger._
    val service = new BagStoreService()
    Runtime.getRuntime.addShutdownHook(new Thread("service-shutdown") {
      override def run(): Unit = {
        info("Stopping service ...")
        service.stop()
        info("Cleaning up ...")
        service.destroy()
        info("Service stopped.")
      }
    })
    service.start()
    info("Service started ...")
    Thread.currentThread.join()
    "Service terminated normally."
  }
}
