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
package nl.knaw.dans.easy.bagstore

import java.nio.file.Path
import java.util.UUID

import nl.knaw.dans.lib.error._

import scala.util.control.NonFatal
import scala.util.{ Success, Try }

object Command extends App with BagStoreApp {
  import scala.language.reflectiveCalls
  type FeedBackMessage = String

  val opts = CommandLineOptions(args, properties)
  opts.verify()
  private val bagStoreBaseDir = opts.bagStoreBaseDir.toOption.orElse(
    opts.storeName.toOption.flatMap(stores.get))

  debug(s"Selected base-dir = $bagStoreBaseDir")

  val result: Try[FeedBackMessage] = opts.subcommand match {
    case Some(opts.list) => Try {
      s"Configured bag-stores:\n$listStores"
    }
    case Some(cmd @ opts.add) =>
      val bagUuid = cmd.uuid.toOption.map(UUID.fromString)

      val base = bagStoreBaseDir match {
        case Some(p) => p
        case None =>
          if(stores.size == 1) stores.head._2
          else promptForStore("Please, select which BagStore to add to.")
      }
      add(cmd.bag(), base, bagUuid).map(bagId => s"Added Bag with bag-id: $bagId to BagStore: ${getStoreName(base)}")
    case Some(cmd @ opts.get) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        store <- get(itemId, cmd.outputDir(), bagStoreBaseDir)
        storeName = getStoreName(store)
      } yield s"Retrieved item with item-id: $itemId to ${cmd.outputDir()} from BagStore: $storeName"
    case Some(cmd @ opts.enum) =>
      cmd.bagId.toOption
        .map(s => for {
            itemId <- ItemId.fromString(s)
            bagId <- itemId.toBagId
            files <- enumFiles(bagId, bagStoreBaseDir)
          } yield files.foreach(println(_)))
        .getOrElse {
          val includeActive = cmd.all() || !cmd.inactive()
          val includeInactive = cmd.all() || cmd.inactive()
          enumBags(includeActive, includeInactive, bagStoreBaseDir).map(_.foreach(println(_)))
        }
        .map(_ => "Done enumerating" + bagStoreBaseDir.map(b => s" (limited to BagStore: ${ getStoreName(b) })").getOrElse(""))
    case Some(cmd @ opts.deactivate) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- deactivate(bagId, bagStoreBaseDir)
      } yield s"Marked ${cmd.bagId()} as inactive"
    case Some(cmd @ opts.reactivate) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- reactivate(bagId, bagStoreBaseDir)
      } yield s"Removed inactive mark from ${cmd.bagId()}"
    case Some(cmd @ opts.prune) =>
      val base = bagStoreBaseDir match {
        case Some(p) => p
        case None =>
          if(stores.size == 1) stores.head._2
          else promptForStore("Please, select the BagStore containing the reference bags.")
      }
      cmd.referenceBags.toOption
        .map(refBags => refBags.map(ItemId.fromString).map(_.flatMap(_.toBagId))
          .collectResults
          .flatMap(refBagIds => prune(cmd.bagDir(), base, refBagIds: _*))
          .map(_ => "Done pruning"))
        .getOrElse(Success("No reference Bags specified: nothing to do"))
    case Some(cmd @ opts.complete) =>
      val base = bagStoreBaseDir match {
        case Some(p) => p
        case None =>
          if(stores.size == 1) stores.head._2
          else promptForStore("Please, select the BagStore in which to resolved localhost references.")
      }
      complete(cmd.bagDir(), base)
        .map(_ => s"Done completing ${cmd.bagDir()}")
    case Some(cmd @ opts.validate) =>
      // TODO: apply this pattern throughout this file.
      val base = bagStoreBaseDir.getOrElse {
          if(stores.size == 1) stores.head._2
          else promptForStore("Please, select the BagStore against which to check localhost references.")
      }
      isVirtuallyValid(cmd.bagDir())(base)
        .map(valid => s"Done validating. Result: virtually-valid = $valid")
    case Some(_ @ opts.runService) => runAsService()
    case _ => throw new IllegalArgumentException(s"Unknown command: ${opts.subcommand}")
      Try { "Unknown command" }
  }

  result.doIfSuccess(msg => println(s"OK: $msg"))
    .doIfFailure { case NonFatal(e) => println(s"FAILED: ${e.getMessage}") }

  private def listStores: String = {
    stores.map {
      case (shortname, base) => s"- $shortname -> $base"
    }.mkString("\n")
  }

  private def getStoreName(p: Path): String = {
    stores.find { case (_, base) => base == p }.map(_._1).getOrElse(p.toString)
  }

  private def promptForStore(msg: String): Path = {
    Stream.continually {
        val name = scala.io.StdIn.readLine(s"$msg\nAvailable BagStores:\n$listStores\nSelect a name: ")
        val optStore = stores.get(name)
        if (optStore.isEmpty) print("Not found. ")
        optStore
    }.find(_.isDefined).flatten.get
  }

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
