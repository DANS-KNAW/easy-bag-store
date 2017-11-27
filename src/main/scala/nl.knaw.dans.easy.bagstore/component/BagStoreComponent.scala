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
package nl.knaw.dans.easy.bagstore.component

import java.io.OutputStream
import java.nio.file.{ Files, Path }
import java.util.UUID

import nl.knaw.dans.easy.bagstore._
import org.apache.commons.io.FileUtils
import resource._

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

trait BagStoreComponent {
  this: FileSystemComponent with BagProcessingComponent with BagFacadeComponent =>

  trait BagStore {

    implicit val baseDir: BaseDir

    def enumBags(includeActive: Boolean = true, includeInactive: Boolean = false): Try[Seq[BagId]] = Try {
      trace(includeActive, includeInactive)

      managed(fileSystem.walkStore)
        .acquireAndGet(_.iterator().asScala.toStream
          // TODO: is there a better way to fail fast than using .get?
          .map(p => fileSystem.fromLocation(p).flatMap(_.toBagId).get)
          .filter(bagId => {
            // TODO: is there a better way to fail fast than using .get?
            val hiddenBag = isHidden(bagId).get
            (hiddenBag && includeInactive) || (!hiddenBag && includeActive)
          })
          .toList)
    }

    private def isHidden(bagId: BagId): Try[Boolean] = fileSystem.toLocation(bagId).map(Files.isHidden)

    def enumFiles(bagId: BagId): Try[Seq[FileId]] = {
      trace(bagId)

      for {
        _ <- fileSystem.checkBagExists(bagId)
        path <- fileSystem.toLocation(bagId)
        payloadPaths <- bagFacade.getPayloadFilePaths(path)
        _ = debug(s"Payload files: $payloadPaths")
      } yield walkFiles(path)
        .filter(p => Files.isRegularFile(p) &&
          (path.resolve("data").relativize(p).toString startsWith ".."))
        .toSet
        .union(payloadPaths)
        .map(path.relativize)
        .map(FileId(bagId, _))
        .toSeq
    }

    def get(itemId: ItemId, output: => OutputStream): Try[Unit] = {
      trace(itemId)

      for {
        _ <- fileSystem.checkBagExists(BagId(itemId.uuid))
        _ <- itemId match {
          case bagId: BagId =>
            for {
              location <- fileSystem.toLocation(bagId)
              stagingDir <- bagProcessing.stageBagDir(location)
              stagedBag = stagingDir.resolve(location.getFileName)
              _ <- bagProcessing.complete(stagedBag)
              zipStaging <- bagProcessing.stageBagZip(stagedBag)
              _ = Files.copy(zipStaging.resolve(location.getFileName), output)
              _ = FileUtils.deleteDirectory(stagingDir.toFile)
              _ = FileUtils.deleteDirectory(zipStaging.toFile)
            } yield ()
          case fileId: FileId =>
            fileSystem.toRealLocation(fileId)
              .map(path => {
                debug(s"Copying $path to outputstream")
                Files.copy(path, output)
              })
        }
      } yield ()
    }

    def get(itemId: ItemId, output: Path): Try[Path] = {
      trace(itemId, output)
      fileSystem.checkBagExists(BagId(itemId.uuid)).flatMap { _ =>
        itemId match {
          case bagId: BagId =>
            fileSystem.toLocation(bagId)
              .map(path => {
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
                  Files.walk(output).iterator().asScala.foreach(bagProcessing.setPermissions(_))
                  baseDir
                }
              })
          case fileId: FileId =>
            fileSystem.toRealLocation(fileId)
              .map(path => {
                val target = if (Files.isDirectory(output)) output.resolve(path.getFileName)
                             else output
                Files.copy(path, target)
                bagProcessing.setFilePermissions()(target)
                baseDir
              })
        }
      }
    }

    def add(bagDir: Path, uuid: Option[UUID] = None, skipStage: Boolean = false): Try[BagId] = {
      trace(bagDir, uuid, skipStage)

      if (Files.isHidden(bagDir))
        Failure(CannotIngestHiddenBagDirectoryException(bagDir))
      else {
        val bagId = BagId(uuid.getOrElse {
          val newUUID = UUID.randomUUID()
          debug(s"generated UUID for new bag: $newUUID")
          newUUID
        })

        for {
          staging <- if (skipStage) Try { bagDir.getParent }
                     else bagProcessing.stageBagDir(bagDir)
          path = staging.resolve(bagDir.getFileName)
          maybeRefbags <- bagProcessing.getReferenceBags(path)
          _ = debug(s"refbags tempfile: $maybeRefbags")
          valid <- fileSystem.isVirtuallyValid(path).recover { case _: BagReaderException => Left("Could not read bag") }
          _ <- valid.fold(msg => Failure(InvalidBagException(bagId, msg)), _ => Success(()))
          _ <- maybeRefbags.map(pruneWithReferenceBags(path)).getOrElse(Success(()))
          _ = debug("bag succesfully pruned")
          container <- fileSystem.toContainer(bagId)
          _ = Files.createDirectories(container)
          _ <- fileSystem.makePathAndParentsInBagStoreGroupWritable(container)
          _ = debug(s"created container for Bag: $container")
          _ <- ingest(bagDir.getFileName, staging, container)
          _ = FileUtils.deleteDirectory(staging.toFile)
        } yield bagId
      }
    }

    private def pruneWithReferenceBags(bagDir: Path)(refbags: Path): Try[Unit] = {
      trace(bagDir, refbags)
      for {
        refs <- Try { Files.readAllLines(refbags).asScala.map(UUID.fromString _ andThen BagId) }
        _ <- bagProcessing.prune(bagDir, refs)
        _ <- Try { Files.delete(refbags) }
      } yield ()
    }

    private def ingest(bagName: Path, staging: Path, container: Path): Try[Unit] = {
      trace(bagName, staging, container)
      val moved = container.resolve(bagName)
      bagProcessing.setPermissions(staging.resolve(bagName))
        .map(Files.move(_, moved))
        .map(_ => ())
        .recoverWith {
          case NonFatal(e) =>
            logger.error(s"Failed to move staged directory into container: ${ staging.resolve(bagName) } -> $moved", e)
            fileSystem.removeEmptyParentDirectoriesInBagStore(container)
            Failure(MoveToStoreFailedException(staging.resolve(bagName), container))
        }
    }

    def deactivate(bagId: BagId): Try[Unit] = {
      for {
        _ <- fileSystem.checkBagExists(bagId)
        path <- fileSystem.toLocation(bagId)
        _ <- if (Files.isHidden(path)) Failure(AlreadyInactiveException(bagId))
             else Success(())
      } yield {
        val newPath = path.getParent.resolve(s".${ path.getFileName }")
        Files.move(path, newPath)
      }
    }

    def reactivate(bagId: BagId): Try[Unit] = {
      for {
        _ <- fileSystem.checkBagExists(bagId)
        path <- fileSystem.toLocation(bagId)
        _ <- if (!Files.isHidden(path)) Failure(NotInactiveException(bagId))
             else Success(())
      } yield {
        val newPath = path.getParent.resolve(s"${ path.getFileName.toString.substring(1) }")
        Files.move(path, newPath)
      }
    }

    def locate(itemId: ItemId): Try[Path] = {
      fileSystem.toLocation(itemId)
    }
  }
}
