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

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils

import scala.collection.JavaConverters._
import scala.util.Try

trait BagStoreGetComponent {
  this: BagStoreContextComponent with BagStoreOutputContext =>

  val get: BagStoreGet

  trait BagStoreGet {
    def get(itemId: ItemId, output: Path): Try[Unit] = {
      itemId match {
        case bagId: BagId => context.toLocation(bagId).map(path => {
          val target = if (Files.isDirectory(output)) output.resolve(path.getFileName) else output
          Files.createDirectory(target)
          FileUtils.copyDirectory(path.toFile, target.toFile)
          Files.walk(output).iterator().asScala.foreach(context.setPermissions(outputBagPermissions))
        })
        case fileId: FileId => context.toRealLocation(fileId).map(path => {
          val target = if (Files.isDirectory(output)) output.resolve(path.getFileName) else output
          Files.copy(path, target)
          Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(outputBagPermissions))
        })
      }
    }
  }
}
