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

import java.util.UUID

import nl.knaw.dans.easy.bagstore.ArchiveStreamType._
import nl.knaw.dans.easy.bagstore.service.ServiceWiring
import nl.knaw.dans.easy.bagstore.{ BaseDir, ItemId, TryExtensions2 }
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

  val bagStoreBaseDir: Option[BaseDir] = commandLine.bagStoreBaseDir.toOption
    .orElse(commandLine.storeName.toOption.flatMap(name => bagStores.getBaseDirByShortname(name)))

  debug(s"Selected base-dir = $bagStoreBaseDir")

  val result: Try[FeedBackMessage] = commandLine.subcommand match {
    case Some(commandLine.list) => Try { s"Configured bag-stores:\n$listStores" }
    case Some(cmd @ commandLine.add) =>
      val bagUuid = cmd.uuid.toOption.map(UUID.fromString)
      val baseDir = bagStoreBaseDir.getOrElse(promptForStore("Please, select which bag store to add to."))
      BagStore(baseDir).add(cmd.bag(), bagUuid, skipStage = cmd.move()).map(bagId => s"Added bag with bag-id: $bagId to bag store: $baseDir")
    case Some(cmd @ commandLine.get) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        (path, store) <- bagStores.copyToDirectory(itemId, cmd.outputDir(), cmd.skipCompletion(), bagStoreBaseDir)
        storeName = getStoreName(store)
      } yield s"Retrieved item with item-id: $itemId to ${ path } from bag store: $storeName"
    case Some(cmd @ commandLine.stream) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        _ <- bagStores.copyToStream(itemId, cmd.format.toOption, Console.out, bagStoreBaseDir)
      } yield s"Retrieved item with item-id: $itemId to stream."
      // TODO: Also report from which bag store, as with get
    case Some(cmd @ commandLine.enum) =>
      cmd.bagId.toOption
        .map(s => for {
          itemId <- ItemId.fromString(s)
          files <- bagStores.enumFiles(itemId, !cmd.excludeDirectories(), bagStoreBaseDir)
        } yield files.foreach(println(_)))
        .getOrElse {
          val includeActive = cmd.all() || !cmd.inactive()
          val includeInactive = cmd.all() || cmd.inactive()
          bagStores.enumBags(includeActive, includeActive, bagStoreBaseDir).map(_.foreach(println(_)))
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
      implicit val baseDir: BaseDir = bagStoreBaseDir.getOrElse(promptForStore("Please, select which bag store to add to."))
      cmd.referenceBags.toOption
        .map(refBags => refBags.map(ItemId.fromString).map(_.flatMap(_.toBagId))
          .collectResults
          .flatMap(refBagIds => bagProcessing.prune(cmd.bagDir(), refBagIds))
          .map(_ => "Done pruning"))
        .getOrElse(Success("No reference Bags specified: nothing to do"))
    case Some(cmd @ commandLine.complete) =>
      implicit val baseDir: BaseDir = bagStoreBaseDir.getOrElse(promptForStore("Please, select in the context of which bag store to complete."))
      bagProcessing.complete(cmd.bagDir().toAbsolutePath, cmd.keepFetchTxt()).map(_ => s"Done completing ${ cmd.bagDir() }")
    case Some(cmd @ commandLine.locate) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        location <- bagStores.locate(itemId, cmd.fileDataLocation(), bagStoreBaseDir)
        _ <- Try { Console.out.println(location.toString) }
      } yield s"Located item $itemId"
    case Some(cmd @ commandLine.validate) =>
      implicit val baseDir: BaseDir = bagStoreBaseDir.getOrElse(promptForStore("Please, select in the context of which bag store to validate."))
      fileSystem.isVirtuallyValid(cmd.bagDir())
        .map(res => s"Done validating. Result: " + res.fold(msg => s"not virtually valid; Messages: '$msg'", _ => "virtually-valid"))
    case Some(_ @ commandLine.runService) => runAsService()
    case _ => Try { s"Unknown command: ${ commandLine.subcommand }" }
  }

  result.doIfSuccess(msg => Console.err.println(s"OK: $msg"))
    .doIfFailure { case e => logger.error(e.getMessage, e) }
    .doIfFailure { case NonFatal(e) => Console.err.println(s"FAILED: ${ e.getMessage }") }

  bagFacade.stop().unsafeGetOrThrow

  private def listStores: String = {
    bagStores.storeShortnames.map { case (name, base) => s"- $name -> $base" }.mkString("\n")
  }

  private def getStoreName(p: BaseDir): String = {
    bagStores.storeShortnames.collectFirst { case (name, base) if base == p => name }.getOrElse(p.toString)
  }

  @tailrec
  private def promptForStore(msg: String): BaseDir = {
    if (bagStores.storeShortnames.size > 1) {
      val name = StdIn.readLine(
        s"""$msg

           |Available BagStores:
           |$listStores

           |Select a name: """
          .stripMargin)
      bagStores.
        getBaseDirByShortname(name) match {
        case Some(store) => store
        case
          None
        => promptForStore(msg)
      }
    }
    else bagStores.storeShortnames.values.head
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
