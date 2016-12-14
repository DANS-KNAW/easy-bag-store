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

import scala.util.Try

trait BagStoreComplete extends BagStoreContext {

  // TODO: This function looks a lot like BagStoreContext.isVirtuallyValid.createLinks, refactor?
  def complete(bagDir: Path): Try[Unit] = {
    trace(bagDir)
    def copyFiles(mappings: Seq[(Path, Path)]): Try[Unit] = Try {
      mappings.foreach {
        case (to, from) =>
          if (!Files.exists(to.getParent))
            Files.createDirectories(to.getParent)
          Files.copy(from, to)
          Files.setPosixFilePermissions(to, PosixFilePermissions.fromString("rwxr-xr--")) // TODO: make configurable
      }
    }

    for {
      virtuallyValid <- isVirtuallyValid(bagDir)
      if virtuallyValid
      mappings <- mapProjectedToRealLocation(bagDir)
      _ <- copyFiles(mappings)
      _ <- bagFacade.removeFetchTxtFromTagManifests(bagDir)
      _ <- Try { Files.delete(bagDir.resolve(bagFacade.FETCH_TXT_FILENAME)) }
      valid <- bagFacade.isValid(bagDir)
      if valid
    } yield ()
  }
}
