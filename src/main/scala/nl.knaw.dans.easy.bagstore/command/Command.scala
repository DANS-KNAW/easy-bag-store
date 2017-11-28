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
package nl.knaw.dans.easy.bagstore.command

import java.nio.file.Path
import java.util.UUID

import nl.knaw.dans.easy.bagstore.ItemId
import nl.knaw.dans.easy.bagstore.service.ServiceWiring
import nl.knaw.dans.easy.bagstore.TryExtensions2
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import scala.annotation.tailrec
import scala.io.StdIn
import scala.language.{ postfixOps, reflectiveCalls }
import scala.util.control.NonFatal
import scala.util.{ Success, Try }

object Command extends App with CommandLineOptionsComponent with ServiceWiring with DebugEnhancedLogging {

  type FeedBackMessage = String

  override val commandLine: CommandLineOptions = new CommandLineOptions(args) {
    verify()
  }

  val bagStoreBaseDir: Option[Path] = commandLine.bagStoreBaseDir.toOption
    .orElse(commandLine.storeName.toOption.flatMap(name => bagStores.getStore(name).map(_.baseDir)))

  debug(s"Selected base-dir = $bagStoreBaseDir")

  val result: Try[FeedBackMessage] = commandLine.subcommand match {
    case Some(commandLine.list) => Try { s"Configured bag-stores:\n$listStores" }
    case Some(cmd @ commandLine.add) =>
      val bagUuid = cmd.uuid.toOption.map(UUID.fromString)

      val (name, store) = bagStoreBaseDir.flatMap(getStore).getOrElse {
        bagStores.stores.toList match {
          case nameAndStore :: Nil => nameAndStore
          case _ => promptForStore("Please, select which BagStore to add to.")
        }
      }
      store.add(cmd.bag(), bagUuid).map(bagId => s"Added Bag with bag-id: $bagId to BagStore: $name")
    case Some(cmd @ commandLine.get) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        store <- bagStores.get(itemId, cmd.outputDir(), bagStoreBaseDir)
        storeName = getStoreName(store)
      } yield s"Retrieved item with item-id: $itemId to ${ cmd.outputDir() } from BagStore: $storeName"
    case Some(cmd @ commandLine.enum) =>
      cmd.bagId.toOption
        .map(s => for {
          itemId <- ItemId.fromString(s)
          bagId <- itemId.toBagId
          files <- bagStores.enumFiles(bagId, bagStoreBaseDir)
        } yield files.foreach(println(_)))
        .getOrElse {
          val includeActive = cmd.all() || !cmd.inactive()
          val includeInactive = cmd.all() || cmd.inactive()
          bagStores.enumBags(includeActive, includeInactive, bagStoreBaseDir).map(_.foreach(println(_)))
        }
        .map(_ => "Done enumerating" + bagStoreBaseDir.map(b => s" (limited to BagStore: ${ getStoreName(b) })").getOrElse(""))
    case Some(cmd @ commandLine.deactivate) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- bagStores.deactivate(bagId, bagStoreBaseDir)
      } yield s"Marked ${ cmd.bagId() } as inactive"
    case Some(cmd @ commandLine.reactivate) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- bagStores.reactivate(bagId, bagStoreBaseDir)
      } yield s"Removed inactive mark from ${ cmd.bagId() }"
    case Some(cmd @ commandLine.prune) =>
      implicit val base: BagPath = bagStoreBaseDir.getOrElse {
        bagStores.stores.toList match {
          case (_, store) :: Nil => store.baseDir
          case _ =>
            val (_, store) = promptForStore("Please, select the BagStore containing the reference bags.")
            store.baseDir
        }
      }
      cmd.referenceBags.toOption
        .map(refBags => refBags.map(ItemId.fromString).map(_.flatMap(_.toBagId))
          .collectResults
          .flatMap(refBagIds => bagProcessing.prune(cmd.bagDir(), refBagIds))
          .map(_ => "Done pruning"))
        .getOrElse(Success("No reference Bags specified: nothing to do"))
    case Some(cmd @ commandLine.complete) =>
      implicit val base: BagPath = bagStoreBaseDir.getOrElse {
        bagStores.stores.toList match {
          case (_, store) :: Nil => store.baseDir
          case _ =>
            val (_, store) = promptForStore("Please, select the BagStore containing the reference bags.")
            store.baseDir
        }
      }
      bagProcessing.complete(cmd.bagDir()).map(_ => s"Done completing ${ cmd.bagDir() }")
    case Some(cmd @ commandLine.locate) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        location <- bagStores.locate(itemId, bagStoreBaseDir)
      } yield location.toString
    case Some(cmd @ commandLine.validate) =>
      implicit val base: BagPath = bagStoreBaseDir.getOrElse {
        bagStores.stores.toList match {
          case (_, store) :: Nil => store.baseDir
          case _ =>
            val (_, store) = promptForStore("Please, select the BagStore containing the reference bags.")
            store.baseDir
        }
      }
      fileSystem.isVirtuallyValid(cmd.bagDir())
        .map(res => s"Done validating. Result: " + res.fold(msg => s"not virtually valid; Messages: '$msg'", _ => "virtually-valid"))
    case Some(_ @ commandLine.runService) => runAsService()
    case _ => Try { s"Unknown command: ${ commandLine.subcommand }" }
  }

  result.doIfSuccess(msg => println(s"OK: $msg"))
    .doIfFailure { case e => logger.error(e.getMessage, e) }
    .doIfFailure { case NonFatal(e) => println(s"FAILED: ${ e.getMessage }") }

  bagFacade.stop().unsafeGetOrThrow

  private def listStores: String = {
    bagStores.stores.map { case (name, base) => s"- $name -> ${ base.baseDir }" }.mkString("\n")
  }

  private def getStoreName(p: Path): String = {
    bagStores.stores.collectFirst { case (name, base) if base == p => name }.getOrElse(p.toString)
  }

  private def getStore(p: Path): Option[(String, BagStore)] = {
    bagStores.stores.find { case (_, store) => store.baseDir == p }
  }

  @tailrec
  private def promptForStore(msg: String): (String, BagStore) = {
    val name = StdIn.readLine(s"$msg\nAvailable BagStores:\n$listStores\nSelect a name: ")
    bagStores.getStore(name) match {
      case Some(store) => (name, store)
      case None => promptForStore(msg)
    }
  }

  private def runAsService(): Try[FeedBackMessage] = Try {
    Runtime.getRuntime.addShutdownHook(new Thread("service-shutdown") {
      override def run(): Unit = {
        logger.info("Stopping service ...")
        (for {
          _ <- server.stop()
          _ <- bagFacade.stop()
          _ = logger.info("Cleaning up ...")
          _ <- server.destroy()
        } yield logger.info("Service stopped.")).unsafeGetOrThrow
      }
    })

    server.start()
    logger.info("Service started ...")
    Thread.currentThread.join()
    "Service terminated normally."
  }
}
