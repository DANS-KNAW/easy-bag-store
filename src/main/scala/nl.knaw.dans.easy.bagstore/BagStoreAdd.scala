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

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path}
import java.util.UUID

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

trait BagStoreAdd { this: BagStoreContext with DebugEnhancedLogging =>

  def add(bagDir: Path, uuid: Option[UUID] = None, skipStage: Boolean = false): Try[BagId] = {
    trace(bagDir)
    if (Files.isHidden(bagDir))
      Failure(CannotIngestHiddenBagDirectory(bagDir))
    else {
      val bagId = BagId(uuid.getOrElse {
        val newUuid = UUID.randomUUID()
        debug(s"generated UUID voor new Bag: $newUuid")
        newUuid
      })
      for {
        staged <- if (skipStage) Try { bagDir } else stageBagDir(bagDir)
        valid <- isVirtuallyValid(staged)
        if valid
        container <- toContainer(bagId)
        _ <- Try { Files.createDirectories(container) }
        _ <- makePathAndParentsInBagStoreGroupWritable(container)
        _ = debug(s"created container for Bag: $container")
        _ <- ingest(bagDir.getFileName, staged, container)
      } yield bagId
    }
  }

  private def ingest(bagName: Path, staged: Path, container: Path): Try[Unit] = {
    trace(bagName, staged, container)
    val moved = container.resolve(bagName)
    Try { Files.move(staged, moved) }
      .flatMap(setPermissions(bagPermissions))
      .recoverWith {
        case NonFatal(e) =>
          logger.error(s"Failed to move staged directory into container: $staged -> $moved", e)
          removeEmptyParentDirectoriesInBagStore(container)
          Failure(MoveToStoreFailedException(staged, container))
      }
  }

  private def makePathAndParentsInBagStoreGroupWritable(path: Path): Try[Unit] = {
    for {
      seq <- getPathsInBagStore(path)
      _ <- seq.map(makeGroupWritable).collectResults
    } yield ()
  }

  private def makeGroupWritable(path: Path): Try[Unit] = Try {
    val permissions = Files.getPosixFilePermissions(path).asScala
    Files.setPosixFilePermissions(path, permissions.union(Set(PosixFilePermission.GROUP_WRITE)).asJava)
  }

  private def removeEmptyParentDirectoriesInBagStore(container: Path): Try[Unit] = {
    getPathsInBagStore(container).flatMap(paths => removeDirectoriesIfEmpty(paths.reverse))
  }

  private def removeDirectoriesIfEmpty(paths: Seq[Path]): Try[Unit] = {
    paths.map(removeDirectoryIfEmpty).collectResults.map(_ => ())
  }

  private def removeDirectoryIfEmpty(path: Path): Try[Unit] = Try {
    if (!Files.list(path).iterator().hasNext) Files.delete(path)
  }

  private def getPathsInBagStore(path: Path): Try[Seq[Path]] = Try {
    val pathComponents = baseDir.relativize(path).iterator().asScala.toList
    pathComponents.indices.map(i => baseDir.resolve(pathComponents.slice(0, i + 1).mkString("/")))
  }
}
