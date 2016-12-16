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

trait BagStoreEnum extends BagStoreContext {

  def enumBags(includeVisible: Boolean = true, includeHidden: Boolean = false): Stream[BagId] = {
    Files.walk(baseDir, uuidPathComponentSizes.size).iterator().asScala
      .map(baseDir.relativize)
      .withFilter(_.getNameCount == uuidPathComponentSizes.size)
      .map(p => fromLocation(baseDir.resolve(p)).flatMap(ItemId.toBagId))
      .map(_.get) // TODO: is there a better way to fail fast ?
      .withFilter { b =>
      val hiddenBag = isHidden(b).get
      hiddenBag && includeHidden || !hiddenBag && includeVisible
    }.toStream
  }

  def enumFiles(bagId: BagId): Stream[FileId] = {
    toLocation(bagId)
      .flatMap(path =>
        bagFacade.getPayloadFilePaths(path)
          .map(ppaths => (Files.list(path).iterator().asScala.withFilter(Files.isRegularFile(_)).toSet | ppaths).map(p => FileId(bagId, p)).toStream)).get
  }
}
