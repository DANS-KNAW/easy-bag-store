package nl.knaw.dans.easy.bagstore.command

import java.nio.file.Path
import java.util.UUID

import nl.knaw.dans.easy.bagstore.ItemId
import nl.knaw.dans.lib.error._

import scala.io.StdIn
import scala.language.postfixOps
import scala.language.reflectiveCalls

import scala.util.control.NonFatal
import scala.util.{ Success, Try }

object Command extends App with CommandWiring {

  type FeedBackMessage = String

  override val commandLine: CommandLineOptions = new CommandLineOptions(args)
  commandLine.verify()

  val bagStoreBaseDir: Option[Path] = commandLine.bagStoreBaseDir.toOption
    .orElse(commandLine.storeName.toOption.flatMap(name => bagStores.getStore(name).map(_.fileSystem.baseDir)))

  debug(s"Selected base-dir = $bagStoreBaseDir")

  val result: Try[FeedBackMessage] = commandLine.subcommand match {
    case Some(commandLine.list) => Try { s"Configured bag-stores:\n$listStores" }
    case Some(cmd @ commandLine.add) =>
      val bagUuid = cmd.uuid.toOption.map(UUID.fromString)

      val base = bagStoreBaseDir.getOrElse {
        bagStores.stores.toList match {
          case (_, store) :: Nil => store.fileSystem.baseDir
          case _ => promptForStore("Please, select which BagStore to add to.")
        }
      }
      getStore(base).get.add(cmd.bag(), bagUuid).map(bagId => s"Added Bag with bag-id: $bagId to BagStore: ${getStoreName(base)}")
    case Some(cmd @ commandLine.get) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        store <- bagStores.get(itemId, cmd.outputDir(), bagStoreBaseDir)
        storeName = getStoreName(store)
      } yield s"Retrieved item with item-id: $itemId to ${cmd.outputDir()} from BagStore: $storeName"
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
      } yield s"Marked ${cmd.bagId()} as inactive"
    case Some(cmd @ commandLine.reactivate) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- bagStores.reactivate(bagId, bagStoreBaseDir)
      } yield s"Removed inactive mark from ${cmd.bagId()}"
    case Some(cmd @ commandLine.prune) =>
      val base = bagStoreBaseDir.getOrElse {
        bagStores.stores.toList match {
          case (_, store) :: Nil => store.fileSystem.baseDir
          case _ => promptForStore("Please, select the BagStore containing the reference bags.")
        }
      }
      cmd.referenceBags.toOption
        .map(refBags => refBags.map(ItemId.fromString).map(_.flatMap(_.toBagId))
          .collectResults
          .flatMap(refBagIds => getStore(base).get.processor.prune(cmd.bagDir(), refBagIds))
          .map(_ => "Done pruning"))
        .getOrElse(Success("No reference Bags specified: nothing to do"))
    case Some(cmd @ commandLine.complete) =>
      val base = bagStoreBaseDir.getOrElse {
        bagStores.stores.toList match {
          case (_, store) :: Nil => store.fileSystem.baseDir
          case _ => promptForStore("Please, select the BagStore in which to resolved localhost references.")
        }
      }
      getStore(base).get.processor.complete(cmd.bagDir())
        .map(_ => s"Done completing ${cmd.bagDir()}")
    case Some(cmd @ commandLine.validate) =>
      val base = bagStoreBaseDir.getOrElse {
        bagStores.stores.toList match {
          case (_, store) :: Nil => store.fileSystem.baseDir
          case _ => promptForStore("Please, select the BagStore against which to check localhost references.")
        }
      }
      getStore(base).get.fileSystem.isVirtuallyValid(cmd.bagDir())
        .map(valid => s"Done validating. Result: virtually-valid = $valid")
    case _ => throw new IllegalArgumentException(s"Unknown command: ${commandLine.subcommand}")
      Try { "Unknown command" }
  }

  result.doIfSuccess(msg => println(s"OK: $msg"))
    .doIfFailure { case NonFatal(e) => println(s"FAILED: ${e.getMessage}") }

  private def listStores: String = {
    bagStores.stores.map { case (name, base) => s"- $name -> $base" }.mkString("\n")
  }

  private def getStoreName(p: Path): String = {
    bagStores.stores.find { case (_, base) => base == p }.map(_._1).getOrElse(p.toString)
  }

  private def getStore(p: Path): Option[BagStore] = {
    bagStores.stores.collectFirst { case (_, store) if store.fileSystem.baseDir == p => store }
  }

  private def promptForStore(msg: String): Path = {
    Stream.continually {
      val name = StdIn.readLine(s"$msg\nAvailable BagStores:\n$listStores\nSelect a name: ")
      val optStore = bagStores.getStore(name)
      if (optStore.isEmpty) print("Not found. ")
      optStore.map(_.fileSystem.baseDir)
    }.find(_.isDefined).flatten.get
  }
}
