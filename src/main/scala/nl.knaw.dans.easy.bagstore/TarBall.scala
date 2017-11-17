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

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.io.FileUtils
import resource.ManagedResource

import scala.util.Try

/**
 * Specification for an entry in the TAR file.
 *
 * @param filePath  optional path to an existing file or directory, if None a directory entry will be created
 * @param entryPath the path of the entry in the TAR file
 */
case class EntrySpec(filePath: Option[Path], entryPath: String)

/**
 * Object representing a TAR ball, providing a function to write it to an output stream.
 *
 * @param files the files in the TAR ball
 */
class TarBall(files: Seq[EntrySpec]) {
  private val TAR_BLOCKSIZE = 1024 * 10 // TAR files are rounded up to blocks of 10K

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
    implicit val buffer: Array[Byte] = new Array[Byte](TAR_BLOCKSIZE)
    createTarOutputStream(outputStream).map {
      _.acquireAndGet {
        tarStream =>
          files.foreach(addFileToTarStream(tarStream))
          tarStream.finish()
      }
    }
  }

  private def createTarOutputStream(output: => OutputStream): Try[ManagedResource[TarArchiveOutputStream]] = Try {
    resource.managed(new ArchiveStreamFactory("UTF-8")
      .createArchiveOutputStream(ArchiveStreamFactory.TAR, output)
      .asInstanceOf[TarArchiveOutputStream])
  }

  private def addFileToTarStream(tarStream: TarArchiveOutputStream)(entrySpec: EntrySpec)(implicit buffer: Array[Byte]): Try[Unit] = Try {
    val entry = tarStream.createArchiveEntry(entrySpec.filePath.map(_.toFile).orNull, entrySpec.entryPath)
    tarStream.putArchiveEntry(entry)
    entrySpec.filePath.foreach(
      file =>
        if (Files.isRegularFile(file)) copyFileToTarStream(tarStream, file))
    tarStream.closeArchiveEntry()
  }

  private def copyFileToTarStream(tarStream: TarArchiveOutputStream, file: Path)(implicit buffer: Array[Byte]): Unit = {
    resource.managed(FileUtils.openInputStream(file.toFile)) acquireAndGet {
      is =>
        var read = is.read(buffer)
        while (read > 0) {
          tarStream.write(buffer, 0, read)
          read = is.read(buffer)
        }
    }
  }
}
