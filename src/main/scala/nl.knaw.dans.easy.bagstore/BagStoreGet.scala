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
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

trait BagStoreGet {
  this: BagStoreContext with BagStoreOutputContext with BagStoreComplete with DebugEnhancedLogging =>

  def get(itemId: ItemId, output: Path, fromStore: Option[Path] = None): Try[Path] = {
    fromStore
      .map(_.toAbsolutePath)
      .map(get(itemId, output, _))
      .getOrElse(getFromAnyStore(itemId, output))
  }

  def get(itemId: ItemId, output: OutputStream, fromStore: Path): Try[Path] = {
    trace(itemId, output, fromStore)
    implicit val baseDir = fromStore.toAbsolutePath
    checkBagExists(BagId(itemId.getUuid)).flatMap { _ =>
      itemId match {
        case bagId: BagId =>
          toLocation(bagId).flatMap { path =>
              for {
                dirStaging <- stageBagDir(path)
                _ <- complete(dirStaging.resolve(path.getFileName), fromStore)
                zipStaging <- stageBagZip(dirStaging.resolve(path.getFileName))
                _ <- Try {Files.copy(zipStaging.resolve(path.getFileName), output)}
                _ <- Try { FileUtils.deleteDirectory(dirStaging.toFile) }
                _ <- Try { FileUtils.deleteDirectory(zipStaging.toFile) }
              } yield fromStore
          }
        case fileId: FileId =>
          toRealLocation(fileId).map(path => {
            debug(s"Copying $path to outputstream")
            Files.copy(path, output)
            fromStore
          })
      }
    }
  }

  def get(itemId: ItemId, output: Path, fromStore: Path): Try[Path] = {
    trace(itemId, output, fromStore)
    implicit val baseDir = fromStore.toAbsolutePath
    checkBagExists(BagId(itemId.getUuid)).flatMap { _ =>
      itemId match {
        case bagId: BagId => toLocation(bagId).map(path => {
          val target = if (Files.isDirectory(output)) output.resolve(path.getFileName)
                       else output
          if (Files.exists(target)) {
            debug("Target already exists")
            throw OutputAlreadyExists(target)
          }
          else {
            debug(s"Creating directory for output: $target")
            Files.createDirectory(target)
            debug(s"Copying bag from $path to $target")
            FileUtils.copyDirectory(path.toFile, target.toFile)
            Files.walk(output).iterator().asScala.foreach(setPermissions(outputBagPermissions))
            fromStore
          }
        })
        case fileId: FileId => toRealLocation(fileId).map(path => {
          val target = if (Files.isDirectory(output)) output.resolve(path.getFileName)
                       else output
          Files.copy(path, target)
          Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(outputBagPermissions))
          fromStore
        })
      }
    }
  }

  def getFromAnyStore(itemId: ItemId, output: Path): Try[Path] = {
    trace(itemId, output)
    // TODO: Find more efficient way of looking up?
    stores.values.toStream
      .find(checkBagExists(BagId(itemId.getUuid))(_).isSuccess)
      .map(get(itemId, output, _))
      .getOrElse(Failure(NoSuchBagException(BagId(itemId.getUuid))))
  }
}
