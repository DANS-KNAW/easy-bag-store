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
import java.nio.file.{ Files, Path, Paths }
import java.util.UUID

import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.lib.error._
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

    def enumFiles(itemId: ItemId, includeDirectories: Boolean = true): Try[Seq[FileId]] = {
      trace(itemId)
      val bagId = BagId(itemId.uuid)

      val queriedPath = itemId match {
        case fileId: FileId => fileId.path
        case bagId: BagId => Paths.get("")
      }

      for {
        _ <- fileSystem.checkBagExists(bagId)
        bagDir <- fileSystem.toLocation(bagId)
        payloadFiles <- bagFacade.getPayloadFilePaths(bagDir).map(_.map(bagDir.relativize))
        payloadFileIds <- Try { payloadFiles.map(FileId(bagId, _)) }
        payloadDirs <- Try {
          if (includeDirectories) calcDirectoriesFromFileSet(payloadFiles)
          else Set.empty[Path]
        }
        payloadDirIds <- Try { payloadDirs.map(FileId(bagId, _ , isDirectory = true))}
        nonPayLoadPaths <- Try {
          walkFiles(bagDir)
            .filter(f => bagDir.resolve("data") != f
              && (includeDirectories || Files.isRegularFile(f))).toSet
        }
        nonPayloadIds <- Try { nonPayLoadPaths.map(f => FileId(bagId, bagDir.relativize(f), Files.isDirectory(f)))}
        allIds <- Try { payloadDirIds ++ payloadFileIds ++ nonPayloadIds }
        filteredIds <- Try { if (queriedPath == Paths.get("")) allIds else allIds.filter(_.path.startsWith(queriedPath)) }
      } yield filteredIds.toSeq.sortBy(_.path)
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

    /**
     * Writes the item pointed to by `itemId` as a TAR-stream to `outputStream`. The entry paths in
     * the TAR-stream will be relative to the item requested, including the item's file name. So if
     * a complete bag is requested, the entry paths will start with the name of the bag, if a directory
     * is requested they will start with that directory's  name, and if a single file is requested,
     * ''no'' directory entry will be created in the TAR.
     *
     * @param itemId the requested item
     * @param outputStream the output stream to write to
     * @return whether the call was successful
     */
    def getAsTar(itemId: ItemId, outputStream: => OutputStream): Try[Unit] = {
      trace(itemId, outputStream)
      val bagId = BagId(itemId.uuid)

      fileSystem.checkBagExists(bagId).flatMap { _ =>
        for {
          bagDir <- fileSystem.toLocation(bagId)
          itemPath <- itemId.toFileId.map(f => bagDir.resolve(f.path)).orElse(Success(bagDir))
          fileIds <- enumFiles(itemId)
          fileSpecs <- fileIds.filter(!_.isDirectory).map {
            fileId =>
              fileSystem
                .toRealLocation(fileId)
                .map(source => createEntrySpec(Some(source), bagDir, itemPath, fileId))
          }.collectResults
          dirSpecs <- Try {
            fileIds.filter(_.isDirectory).map {
              dir =>
                createEntrySpec(None, bagDir, itemPath, dir)
            }
          }
          allEntries <- Try { (dirSpecs ++ fileSpecs).sortBy(_.entryPath) }
          _ <- Try { new TarBall(allEntries).writeTo(outputStream) }
        } yield ()
      }
    }

    def getAsTar2(itemId: ItemId, outputStream: => OutputStream, startByte: Long = 0L, endByte: Long = Long.MaxValue): Try[Unit] = {
      trace(itemId, outputStream)
      val bagId = BagId(itemId.uuid)

      fileSystem.checkBagExists(bagId).flatMap { _ =>
        for {
          bagDir <- fileSystem.toLocation(bagId)
          itemPath <- itemId.toFileId.map(f => bagDir.resolve(f.path)).orElse(Success(bagDir))
          fileIds <- enumFiles(itemId)
          fileSpecs <- fileIds.filter(!_.isDirectory).map {
            fileId =>
              fileSystem
                .toRealLocation(fileId)
                .map(source => createEntrySpec(Some(source), bagDir, itemPath, fileId))
          }.collectResults
          dirSpecs <- Try {
            fileIds.filter(_.isDirectory).map {
              dir =>
                createEntrySpec(None, bagDir, itemPath, dir)
            }
          }
          allEntries <- Try { (dirSpecs ++ fileSpecs).sortBy(_.entryPath) }
          _ <- Try { new TarBall(allEntries).writeTo(outputStream) }
        } yield ()
      }
    }

    private def createEntrySpec(source: Option[Path], bagDir: Path, itemPath: Path, fileId: FileId): EntrySpec = {
      if (itemPath == bagDir) EntrySpec(source, Paths.get(bagDir.getFileName.toString, fileId.path.toString).toString)
      else EntrySpec(source, Paths.get(itemPath.getFileName.toString, bagDir.relativize(itemPath).relativize(fileId.path).toString).toString)
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
          (valid, msg) <- fileSystem.isVirtuallyValid(path).recover { case _: BagReaderException => (false, "Could not read bag") }
          _ <- if (valid) Success(())
               else Failure(InvalidBagException(bagId, msg))
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
