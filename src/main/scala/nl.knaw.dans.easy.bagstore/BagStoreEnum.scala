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

import java.nio.file.{FileVisitOption, Files, Path}

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}
import nl.knaw.dans.lib.error._

trait BagStoreEnum { this: BagFacadeComponent with BagStoreContext =>

  // TODO: support huge numbers of bags. (The stream should then probably NOT be converted in to a List anymore!)
  def enumBags(includeActive: Boolean = true, includeInactive: Boolean = false, fromStore: Option[Path] = None): Try[Seq[BagId]] =  {
    fromStore.map(enumBags(includeActive, includeInactive, _)).getOrElse(
        stores.map {
          case (_, base) => enumBags(includeActive, includeInactive, base)
        }.collectResults.map(_.reduce(_ ++ _)))
  }

  def enumBags(includeActive: Boolean, includeInactive: Boolean, fromStore: Path): Try[Seq[BagId]] = Try {
    implicit val baseDir = fromStore

    resource.managed(Files.walk(baseDir, uuidPathComponentSizes.size, FileVisitOption.FOLLOW_LINKS)).acquireAndGet {
      _.iterator().asScala.toStream
        .map(baseDir.relativize)
        .withFilter(_.getNameCount == uuidPathComponentSizes.size)
        .map(p => fromLocation(baseDir.resolve(p)).flatMap(_.toBagId).get) // TODO: is there a better way to fail fast ?
        .filter(bagId => {
        val hiddenBag = isHidden(bagId).get
        hiddenBag && includeInactive || !hiddenBag && includeActive
      }).toList
    }
  }

  def enumFiles(bagId: BagId, fromStore: Option[Path] = None): Try[Stream[FileId]] = {
    fromStore
      .map(enumFiles(bagId, _)).getOrElse(
      stores.toStream.map(_._2)
        .find(checkBagExists(bagId)(_).isSuccess)
        .map(enumFiles(bagId, _)).getOrElse(Failure(NoSuchBagException(bagId))))
  }

  def enumFiles(bagId: BagId, fromStore: Path): Try[Stream[FileId]] = {
    implicit val baseDir = fromStore

    checkBagExists(bagId).flatMap { _ =>
        for {
          path <- toLocation(bagId)
          ppaths <- bagFacade.getPayloadFilePaths(path)
        } yield listFiles(path)
          .withFilter(Files.isRegularFile(_))
          .map(path.relativize)
          .toSet
          .union(ppaths)
          .map(FileId(bagId, _))
          .toStream
    }
  }
}
