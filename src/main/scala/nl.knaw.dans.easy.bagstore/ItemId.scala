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

import java.net.URLDecoder
import java.nio.file.{Path, Paths}
import java.util.UUID

import com.google.common.net.UrlEscapers

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

case class NoBagIdException(itemId: ItemId) extends Exception(s"item-id $itemId is not a bag-id")

case class NoFileIdException(itemId: ItemId) extends Exception(s"item-id $itemId is not a file-id")

abstract class ItemId() {
  def getUuid: UUID
}

object ItemId {
  def fromString(s: String): Try[ItemId] = {
    Try {
      s.split("/", 2) match {
        case Array(uuidStr) => BagId(UUID.fromString(uuidStr))
        case Array(uuidStr, path) =>
          FileId(BagId(UUID.fromString(uuidStr)), Paths.get(URLDecoder.decode(path, "UTF-8")))
      }
    }
  }


  def toBagId(itemId: ItemId): Try[BagId] = itemId match {
    case id: BagId => Success(id)
    case id => Failure(NoBagIdException(id))
  }

  def toFileId(itemId: ItemId): Try[FileId] = itemId match {
    case id: FileId => Success(id)
    case id => Failure(NoFileIdException(id))
  }


}

case class BagId(uuid: UUID) extends ItemId {
  override def toString: String = uuid.toString

  override def getUuid = uuid
}

case class FileId(bagId: BagId, path: Path) extends ItemId {
  private val pathEscaper = UrlEscapers.urlPathSegmentEscaper()

  override def toString: String = s"$bagId/${path.iterator().asScala.map(_.toString).map(pathEscaper.escape).mkString("/")}"

  override def getUuid = bagId.uuid
}

object FileId {
  def apply(uuid: UUID, path: Path): FileId = FileId(BagId(uuid), path)
}

