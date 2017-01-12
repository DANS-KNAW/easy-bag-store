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

import java.nio.file.Files

import scala.collection.JavaConverters._
import scala.util.Try

trait BagStoreEnumComponent {
  this: BagFacadeComponent
    with BagStoreContextComponent =>

  val enum: BagStoreEnum

  trait BagStoreEnum {

    def enumBags(includeVisible: Boolean = true, includeHidden: Boolean = false): Try[Stream[BagId]] = Try {
      Files.walk(context.baseDir, context.uuidPathComponentSizes.size).iterator().asScala.toStream
        .map(context.baseDir.relativize)
        .withFilter(_.getNameCount == context.uuidPathComponentSizes.size)
        .map(p => context.fromLocation(context.baseDir.resolve(p)).flatMap(_.toBagId).get) // TODO: is there a better way to fail fast ?
        .filter(bagId => {
          val hiddenBag = context.isHidden(bagId).get
          hiddenBag && includeHidden || !hiddenBag && includeVisible
        })
    }

    def enumFiles(bagId: BagId): Try[Stream[FileId]] = {
      for {
        path <- context.toLocation(bagId)
        ppaths <- bagFacade.getPayloadFilePaths(path)
      } yield Files.list(path).iterator().asScala
        .withFilter(Files.isRegularFile(_))
        .map(path.relativize)
        .toSet
        .union(ppaths)
        .map(FileId(bagId, _))
        .toStream
    }
  }
}
