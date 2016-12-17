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

import java.util.UUID

import scala.util.{Failure, Success, Try}

object Command extends App with BagStoreApp {
  import scala.language.reflectiveCalls

  val opts = CommandLineOptions(args, properties)
  opts.verify()
  override implicit val baseDir = opts.bagStoreBaseDir().toAbsolutePath

  val result: Try[String] = opts.subcommand match {
    case Some(cmd@opts.add) =>
      val bagUuid = cmd.uuid.toOption.map(UUID.fromString)
      add(cmd.bag(), bagUuid).map {
        bagId => s"Added Bag with bag-id: $bagId"
      }
    case Some(cmd@opts.get) =>
      for {
        itemId <- ItemId.fromString(cmd.itemId())
        _ <- Try {
          get(itemId, cmd.outputDir())
        }
      } yield s"Retrieved item with item-id: $itemId to ${cmd.outputDir()}"
    case Some(cmd@opts.enum) => Try {
      cmd.bagId.toOption
        .map {
          s =>
            for {
              itemId <- ItemId.fromString(s)
              bagId <- ItemId.toBagId(itemId)
            } yield enumFiles(bagId)
              .iterator.foreach(println(_))
        } getOrElse {
          val includeVisible = cmd.all() || !cmd.hidden()
          val includeHidden = cmd.all() || cmd.hidden()
          enumBags(includeVisible, includeHidden).iterator.foreach(println(_))
      }
      "Finished enumerating"
    }
    case Some(cmd@opts.hide) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- ItemId.toBagId(itemId)
        _ <- delete(bagId)
      } yield s"Marked ${cmd.bagId()} as deleted"
    case Some(cmd@opts.reveal) =>
      for {
        itemId <- ItemId.fromString(cmd.bagId())
        bagId <- ItemId.toBagId(itemId)
        _ <- undelete(bagId)
      } yield s"Removed deleted mark from ${cmd.bagId()}"
    case Some(cmd@opts.prune) =>
      import nl.knaw.dans.lib.error._
      cmd.referenceBags.toOption.map(refBags =>
        refBags.map(ItemId.fromString).map(_.flatMap(ItemId.toBagId))
          .collectResults
          .flatMap(refBagIds => prune(cmd.bagDir(), refBagIds: _*))
          .map(_ => "Done pruning"))
        .getOrElse(Success("No reference Bags specified: nothing to do"))
    case Some(cmd@opts.complete) =>
      complete(cmd.bagDir())
          .map(_ => s"Done completing ${cmd.bagDir()}")
    case Some(cmd@opts.validate) =>
      isVirtuallyValid(cmd.bagDir())
          .map(valid => s"done validating. Result: virtually-valid = $valid")
    case _ => throw new IllegalArgumentException(s"Unknown command: ${opts.subcommand}")
      Try {
        "Unknown command"
      }
  }

  result match {
    case Success(msg) => println(s"OK: $msg")
    case Failure(e) => println(s"FAILED: ${e.getMessage}")
  }
}
