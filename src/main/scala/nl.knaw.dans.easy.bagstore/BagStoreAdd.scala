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

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path}
import java.util.UUID

import nl.knaw.dans.lib.error.TraversableTryExtensions
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait BagStoreAdd { this: BagStoreContext with BagStorePrune with BagFacadeComponent with DebugEnhancedLogging =>

  def add(bagDir: Path, toStore: Path, uuid: Option[UUID] = None, skipStage: Boolean = false): Try[BagId] = {
    implicit val baseDir = toStore

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
        staging <- if (skipStage) Try { bagDir.getParent } else stageBagDir(bagDir)
        maybeRefbags <- getReferenceBags(staging.resolve(bagDir.getFileName))
        _ = debug(s"refbags tempfile: $maybeRefbags")
        valid <- isVirtuallyValid(staging.resolve(bagDir.getFileName))
        if valid
        _ <- maybeRefbags.map(pruneWithReferenceBags(staging.resolve(bagDir.getFileName))).getOrElse(Success(()))
        _ = debug("bag succesfully pruned")
        container <- toContainer(bagId)
        _ <- Try { Files.createDirectories(container) }
        _ <- makePathAndParentsInBagStoreGroupWritable(container)
        _ = debug(s"created container for Bag: $container")
        _ <- ingest(bagDir.getFileName, staging, container)
        _ <- Try { FileUtils.deleteDirectory(staging.toFile) }
      } yield bagId
    }
  }

  private def getReferenceBags(bagDir: Path): Try[Option[Path]] = Try {
    trace(bagDir)
    val refbags = bagDir.resolve("refbags.txt")
    if (Files.exists(refbags)) {
      // copy to tempDir
      val tempRefbags = Files.createTempFile(stagingBaseDir, "refbags-", "")
      Files.deleteIfExists(tempRefbags)
      Files.move(refbags, tempRefbags)
      assert(!Files.exists(refbags), s"$refbags should have been moved to $tempRefbags, however, it appears to still be present here")

      // remove refbags.txt from all tagmanifests (if it was present there)
      bagFacade.removeFromTagManifests(bagDir, "refbags.txt")

      Some(tempRefbags)
    }
    else None
  }

  private def pruneWithReferenceBags(bagDir: Path)(refbags: Path)(implicit baseDir: Path): Try[Unit] = {
    trace(bagDir, refbags)
    for {
      refs <- Try { Files.readAllLines(refbags).asScala.map(UUID.fromString _ andThen BagId) }
      _ <- prune(bagDir, baseDir, refs:_*)
      _ <- Try { Files.delete(refbags) }
    } yield ()
  }

  private def ingest(bagName: Path, staging: Path, container: Path)(implicit baseDir: Path): Try[Unit] = {
    trace(bagName, staging, container)
    val moved = container.resolve(bagName)
    setPermissions(bagPermissions)(staging.resolve(bagName))
      .map(Files.move(_, moved))
      .map(_ => ())
      .recoverWith {
        case NonFatal(e) =>
          logger.error(s"Failed to move staged directory into container: ${staging.resolve(bagName)} -> $moved", e)
          removeEmptyParentDirectoriesInBagStore(container)
          Failure(MoveToStoreFailedException(staging.resolve(bagName), container))
      }
  }

  private def makePathAndParentsInBagStoreGroupWritable(path: Path)(implicit baseDir: Path): Try[Unit] = {
    for {
      seq <- getPathsInBagStore(path)
      _ <- seq.map(makeGroupWritable).collectResults
    } yield ()
  }

  private def makeGroupWritable(path: Path): Try[Unit] = Try {
    val permissions = Files.getPosixFilePermissions(path).asScala
    Files.setPosixFilePermissions(path, permissions.union(Set(PosixFilePermission.GROUP_WRITE)).asJava)
  }

  private def removeEmptyParentDirectoriesInBagStore(container: Path)(implicit baseDir: Path): Try[Unit] = {
    getPathsInBagStore(container).flatMap(paths => removeDirectoriesIfEmpty(paths.reverse))
  }

  private def removeDirectoriesIfEmpty(paths: Seq[Path]): Try[Unit] = {
    paths.map(removeDirectoryIfEmpty).collectResults.map(_ => ())
  }

  private def removeDirectoryIfEmpty(path: Path): Try[Unit] = Try {
    if (listFiles(path).isEmpty) Files.delete(path)
  }

  private def getPathsInBagStore(path: Path)(implicit baseDir: Path): Try[Seq[Path]] = Try {
    val pathComponents = baseDir.relativize(path).asScala.toSeq
    pathComponents.indices.map(i => baseDir.resolve(pathComponents.slice(0, i + 1).mkString("/")))
  }
}
