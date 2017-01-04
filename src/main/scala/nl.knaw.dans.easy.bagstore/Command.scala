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

import java.net.URI
import java.nio.file.Path
import java.util.UUID

import nl.knaw.dans.lib.error.TraversableTryExtensions

import scala.util.{Success, Try}

object Command extends App with BagStoreApp {
  import scala.language.reflectiveCalls

  val opts = CommandLineOptions(args, properties)
  opts.verify()

  override protected def context0: BagStoreContext = {
    val oldContext = super.context0
    new BagStoreContext {
      override val baseDir: Path = opts.bagStoreBaseDir().toAbsolutePath
      override val baseUri: URI = oldContext.baseUri
      override val stagingBaseDir: Path = oldContext.stagingBaseDir
      override val uuidPathComponentSizes: Seq[Int] = oldContext.uuidPathComponentSizes
      override val bagPermissions: String = oldContext.bagPermissions
    }
  }
  override lazy val context = context0

  val result: Try[String] = opts.subcommand match {
    case Some(cmd @ opts.add) =>
      val bagUuid = cmd.uuid.toOption.map(UUID.fromString)
      add.add(cmd.bag(), bagUuid).map(bagId => s"Added Bag with bag-id: $bagId")
    case Some(cmd @ opts.get) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        _ <- Try { get.get(itemId, cmd.outputDir()) }
      } yield s"Retrieved item with item-id: $itemId to ${cmd.outputDir()}"
    case Some(cmd @ opts.enum) =>
      cmd.bagId.toOption
        .map(s => for {
            itemId <- ItemId.fromString(s)
            bagId <- itemId.toBagId
            files <- enum.enumFiles(bagId)
          } yield files.foreach(println(_)))
        .getOrElse {
          val includeVisible = cmd.all() || !cmd.hidden()
          val includeHidden = cmd.all() || cmd.hidden()
          enum.enumBags(includeVisible, includeHidden).map(_.foreach(println(_)))
        }
        .map(_ => "Done enumerating")
    case Some(cmd @ opts.delete) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- delete.delete(bagId)
      } yield s"Marked ${cmd.bagId()} as deleted"
    case Some(cmd @ opts.undelete) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- itemId.toBagId
        _ <- delete.undelete(bagId)
      } yield s"Removed deleted mark from ${cmd.bagId()}"
    case Some(cmd @ opts.prune) =>
      cmd.referenceBags.toOption
        .map(refBags => refBags.map(ItemId.fromString).map(_.flatMap(_.toBagId))
          .collectResults
          .flatMap(refBagIds => prune.prune(cmd.bagDir(), refBagIds: _*))
          .map(_ => "Done pruning"))
        .getOrElse(Success("No reference Bags specified: nothing to do"))
    case Some(cmd @ opts.complete) =>
      complete.complete(cmd.bagDir())
        .map(_ => s"Done completing ${cmd.bagDir()}")
    case Some(cmd @ opts.validate) =>
      context.isVirtuallyValid(cmd.bagDir())
        .map(valid => s"Done validating. Result: virtually-valid = $valid")
    case _ => throw new IllegalArgumentException(s"Unknown command: ${opts.subcommand}")
      Try { "Unknown command" }
  }

  result.map(msg => println(s"OK: $msg"))
    .onError(e => println(s"FAILED: ${e.getMessage}"))
}
