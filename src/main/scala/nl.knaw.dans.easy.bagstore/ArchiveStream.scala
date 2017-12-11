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

import java.io.OutputStream
import java.nio.file.{ Files, Path }

import org.apache.commons.compress.archivers.{ ArchiveOutputStream, ArchiveStreamFactory }
import org.apache.commons.io.FileUtils
import resource.ManagedResource

import scala.language.implicitConversions
import scala.util.Try

object ArchiveStreamType extends Enumeration {
  type ArchiveStreamType = Value
  val TAR, ZIP = Value
}

import nl.knaw.dans.easy.bagstore.ArchiveStreamType._

/**
 * Specification for an entry in the archive file.
 *
 * @param sourcePath  optional path to an existing file or directory, if None a directory entry will be created
 * @param entryPath the path of the entry in the archive file
 */
case class EntrySpec(sourcePath: Option[Path], entryPath: String)

/**
 * Object representing a TAR ball, providing a function to write it to an output stream.
 *
 * @param files the files in the TAR ball
 */
class ArchiveStream(streamType: ArchiveStreamType, files: Seq[EntrySpec]) {
  private val BLOCKSIZE = 1024 * 10 // TAR files are rounded up to blocks of 10K,

  implicit private def toArchiveStreamFactory(streamType: ArchiveStreamType.Value): String = {
    streamType match {
      case TAR => ArchiveStreamFactory.TAR
      case ZIP => ArchiveStreamFactory.ZIP
    }
  }

  /**
   * Writes the files to an output stream.
   *
   * @param outputStream the output stream to write to
   * @return
   */
  def writeTo(outputStream: => OutputStream): Try[Unit] = {
    /**
     * We create the buffer here, as it is possible that two threads will call `writeTo` simultaneously.
     * Invocations of `writeTo` cannot share the buffer, as this would obviously lead to corrupted
     * TAR streams.
     */
    implicit val buffer: Array[Byte] = new Array[Byte](BLOCKSIZE)
    createArchiveOutputStream(outputStream).map {
      _.acquireAndGet {
        tarStream =>
          files.foreach(addFileToTarStream(tarStream))
          tarStream.finish()
      }
    }
  }

  private def createArchiveOutputStream(output: => OutputStream): Try[ManagedResource[ArchiveOutputStream]] = Try {
    resource.managed(new ArchiveStreamFactory("UTF-8")
      .createArchiveOutputStream(streamType, output)
      .asInstanceOf[ArchiveOutputStream])
  }

  private def addFileToTarStream(os: ArchiveOutputStream)(entrySpec: EntrySpec)(implicit buffer: Array[Byte]): Try[Unit] = Try {
    val entry = os.createArchiveEntry(entrySpec.sourcePath.map(_.toFile).orNull, entrySpec.entryPath)
    os.putArchiveEntry(entry)
    entrySpec.sourcePath.foreach(
      file =>
        if (Files.isRegularFile(file)) copyFileToStream(os, file))
    os.closeArchiveEntry()
  }

  private def copyFileToStream(os: OutputStream, file: Path)(implicit buffer: Array[Byte]): Unit = {
    resource.managed(FileUtils.openInputStream(file.toFile)) acquireAndGet {
      is =>
        var read = is.read(buffer)
        while (read > 0) {
          os.write(buffer, 0, read)
          read = is.read(buffer)
        }
    }
  }
}
