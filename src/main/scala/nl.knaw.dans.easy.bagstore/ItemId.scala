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
  // FIXME the exact same pattern is also used in dans-bag-lib, share value?
  val uuidRegex = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"

  def fromString(s: String): Try[ItemId] = Try {
    s.split("/", 2) match {
      case Array(uuidStr) => BagId(UUID.fromString(validateUuid(uuidStr.toLowerCase)))
      case Array(uuidStr, path) =>
        FileId(UUID.fromString(validateUuid(uuidStr.toLowerCase())), Paths.get(URLDecoder.decode(path, "UTF-8")))
    }
  }

  private def validateUuid(uuidAsString: String): String = {
    val uuid = uuidAsString.trim.toLowerCase
    if (uuid.length > 36) { //FIXME length of 36 is implicitly checked in pattern below
      throw new IllegalArgumentException(s"An UUID should not contain more than 36 characters, this UUID has ${uuid.length}")
    }
    /*if (!uuid.matches(uuidRegex)) {
      throw new IllegalArgumentException(s"The UUID $uuidAsString is not formatted correctly")
    }*/
    uuid
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

