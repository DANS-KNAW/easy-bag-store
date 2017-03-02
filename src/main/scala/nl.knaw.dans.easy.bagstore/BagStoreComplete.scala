/**
 * Copyright (C) 2016-17 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
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

import java.nio.file.{Files, Path}

import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import scala.util.Try

trait BagStoreComplete { this: BagFacadeComponent with BagStoreOutputContext with BagStoreContext with DebugEnhancedLogging =>

  // TODO: This function looks a lot like BagStoreContext.isVirtuallyValid.createLinks, refactor?
  def complete(bagDir: Path, fromStore: Path): Try[Unit] = {
    implicit val baseDir = fromStore

    trace(bagDir)
    def copyFiles(mappings: Seq[(Path, Path)]): Try[Unit] = Try {
      debug(s"copying ${mappings.size} files to projected locations")
      mappings.foreach { case (to, from) =>
        if (!Files.exists(to.getParent)) {
          debug(s"creating missing parent directory: ${to.getParent}")
          Files.createDirectories(to.getParent)
        }
        debug(s"copy $from -> $to")
        Files.copy(from, to)
        setPermissions(outputBagPermissions)(to).get
      }
    }

    for {
      virtuallyValid <- isVirtuallyValid(bagDir)
      _ = debug(s"input virtually-valid?: $virtuallyValid")
      if virtuallyValid
      mappings <- mapProjectedToRealLocation(bagDir)
      _ <- copyFiles(mappings)
      _ <- bagFacade.removeFetchTxtFromTagManifests(bagDir)
      _ <- Try { Files.deleteIfExists(bagDir.resolve(bagFacade.FETCH_TXT_FILENAME)) }
      valid <- bagFacade.isValid(bagDir)
      _ = debug(s"result valid?: $valid")
      if valid
    } yield ()
  }
}
