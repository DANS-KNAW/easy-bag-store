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

import java.net.URLDecoder
import java.nio.file.{ Path, Paths }
import java.util.UUID

import com.google.common.net.UrlEscapers

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

abstract class ItemId(val uuid: UUID) {
  def getBagId = BagId(uuid)

  def toBagId: Try[BagId]

  def toFileId: Try[FileId]
}

object ItemId {

  def fromString(s: String): Try[ItemId] = Try {
    s.split("/", 2) match {
      case Array(uuidStr) => BagId(UUID.fromString(validateUuidLength(uuidStr)))
      case Array(uuidStr, path) =>
        FileId(UUID.fromString(validateUuidLength(uuidStr)), Paths.get(URLDecoder.decode(path, "UTF-8")))
    }
  }

  private def validateUuidLength(uuidAsString: String): String = {
        if (uuidAsString.length > 36) {
      throw new IllegalArgumentException(s"A UUID should not contain more than 36 characters, this UUID has ${uuidAsString.length}")
    }
    uuidAsString
  }
}

case class BagId(override val uuid: UUID) extends ItemId(uuid) {
  override def toString: String = uuid.toString

  override def toBagId: Try[BagId] = Success(this)

  override def toFileId: Try[FileId] = Failure(NoFileIdException(this))
}

// FIXME: isDirectory cannot always be known in advance
case class FileId(bagId: BagId, path: Path, isDirectory: Boolean = false) extends ItemId(bagId.uuid) {
  private val pathEscaper = UrlEscapers.urlPathSegmentEscaper()

  override def toString: String = {
    s"$bagId/${ path.asScala.map(_.toString).map(pathEscaper.escape).mkString("/") }"
  }

  override def toBagId: Try[BagId] = Failure(NoBagIdException(this))

  override def toFileId: Try[FileId] = Success(this)
}

object FileId {
  def apply(uuid: UUID, path: Path): FileId = FileId(BagId(uuid), path)
}

