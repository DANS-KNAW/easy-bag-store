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

import nl.knaw.dans.easy.bagstore.ArchiveStreamType.ArchiveStreamType
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

    def enumFiles(itemId: ItemId, includeDirectories: Boolean = true, forceInactive: Boolean = false): Try[Seq[FileId]] = {
      trace(itemId)
      val bagId = BagId(itemId.uuid)

      val queriedPath = itemId match {
        case fileId: FileId => fileId.path
        case _: BagId => Paths.get("")
      }

      for {
        _ <- fileSystem.checkBagExists(bagId)
        bagDir <- fileSystem.toLocation(bagId)
        _ <- validateThatBagDirIsNotHidden(bagDir, itemId, forceInactive)
        payloadFiles <- bagFacade.getPayloadFilePaths(bagDir).map(_.map(bagDir.relativize))
        payloadFileIds <- Try { payloadFiles.map(FileId(bagId, _)) }
        payloadDirs <- Try {
          if (includeDirectories) calcDirectoriesFromFileSet(payloadFiles)
          else Set.empty[Path]
        }
        payloadDirIds <- Try { payloadDirs.map(FileId(bagId, _, isDirectory = true)) }
        nonPayLoadPaths <- Try {
          walkFiles(bagDir)
            .filter(f => bagDir.resolve("data") != f
              && (includeDirectories || Files.isRegularFile(f))).toSet
        }
        nonPayloadIds <- Try { nonPayLoadPaths.map(f => FileId(bagId, bagDir.relativize(f), Files.isDirectory(f))) }
        allIds <- Try { payloadDirIds ++ payloadFileIds ++ nonPayloadIds }
        filteredIds <- Try {
          if (queriedPath == Paths.get("")) allIds
          else allIds.filter(_.path.startsWith(queriedPath))
        }
      } yield filteredIds.toSeq.sortBy(_.path)
    }

    /**
     * Copies an item to a specified output directory. If the output directory does not exist, it is first
     * created. The result can be a regular file or complete bag, depending on what `itemId`
     * points to. Copying of a single directory is not supported, as a bag directory may not be represented
     * in stored bag as a physical directory. Copying a directory can be achieved with @{link #copyToStream}.
     *
     * If the path for the result already exists, this function returns a failure. Also, if the
     * output directory exists as a regular file, the function will fail.
     *
     * The file and directory permissions on the result are changed recursively to the values configured in
     * `cli.output.bag-file-permissions` and `cli.output.bag-dir-permissions`.
     *
     * @param itemId         the item to copy
     * @param output         the directory to copy it to
     * @param skipCompletion if `true` no files will be fetched from other locations
     * @return
     */
    def copyToDirectory(itemId: ItemId, output: Path, skipCompletion: Boolean = false, forceInactive: Boolean = false): Try[(Path, BaseDir)] = {
      trace(itemId, output)
      if (Files.isRegularFile(output)) Failure(OutputNotADirectoryException(output))
      else {
        if (!Files.exists(output)) Files.createDirectories(output)
        fileSystem.checkBagExists(BagId(itemId.uuid)).flatMap { _ =>
          itemId match {
            case bagId: BagId =>
              fileSystem.toLocation(bagId)
                .map(path => {
                  val resultDir: BaseDir = validatePathAndResolveResultDirectory(itemId, output, forceInactive, path)
                  debug(s"Copying bag from $path to $output")
                  FileUtils.copyDirectory(path.toFile, resultDir.toFile)
                  if (!skipCompletion) bagProcessing.complete(resultDir)
                  bagProcessing.setPermissions(resultDir, bagProcessing.outputBagFilePermissions, bagProcessing.outputBagDirPermissions)
                  (output.resolve(path.getFileName), baseDir)
                })
            case fileId: FileId =>
              if (fileId.isDirectory) throw NoRegularFileException(itemId)
              else {
                fileSystem.toRealLocation(fileId)
                  .map(path => {
                    if (Files.isHidden(path) && !forceInactive) throw InactiveException(itemId, forceInactive)
                    val resultFile = output.resolve(path.getFileName).toAbsolutePath
                    if (Files.exists(resultFile)) throw OutputAlreadyExists(resultFile)
                    FileUtils.copyFile(path.toFile, resultFile.toFile)
                    Files.setPosixFilePermissions(resultFile, bagProcessing.outputBagFilePermissions)
                    (output.resolve(path.getFileName), baseDir)
                  })
              }
          }
        }
      }
    }

    /**
     * Writes the item pointed to by `itemId` as an archive-stream to `outputStream`. The entry paths in
     * the archive-stream will be relative to the item requested, including the item's file name. So if
     * a complete bag is requested, the entry paths will start with the name of the bag, if a directory
     * is requested they will start with that directory's  name, and if a single file is requested,
     * ''no'' directory entry will be created in the archive.
     *
     * @param itemId            the requested item
     * @param archiveStreamType the format of the outputstream (TAR, ZIP, etc)
     * @param outputStream      the output stream to write to
     * @return whether the call was successful
     */
    def copyToStream(itemId: ItemId, archiveStreamType: Option[ArchiveStreamType], outputStream: => OutputStream, forceInactive: Boolean = false): Try[Option[Path]] = {
      trace(itemId)
      val bagId = BagId(itemId.uuid)

      for {
        _ <- fileSystem.checkBagExists(bagId)
        bagDir <- fileSystem.toLocation(bagId)
        itemPath <- itemId.toFileId.map(f => bagDir.resolve(f.path)).orElse(Success(bagDir))
        fileIds <- enumFiles(itemId, forceInactive = true) // validation that the bagdir is active will be done later one
        fileSpecs <- createFileSpecs(bagDir, itemPath, fileIds)
        dirSpecs <- createDirectorySpecs(bagDir, itemPath, fileIds)
        allEntriesCount = fileSpecs.length + dirSpecs.length
        allEntries = () => (dirSpecs ++ fileSpecs).sortBy(_.entryPath) // only concat and sort if necessary, hence as a function here
        _ <- fileIsFound(allEntriesCount, itemId)
        _ <- validateThatBagDirIsNotHidden(bagDir, itemId, forceInactive) // if the bag is hidden, also don't return a specific item from the bag
        maybePath <- archiveStreamType.map(copyToArchiveStream(outputStream)(allEntries)(_).map(_ => Option.empty))
          .getOrElse(findFile(itemId, fileIds, allEntriesCount).map(Option(_)))
      } yield maybePath
    }

    private def fileIsFound(entriesCount: Int, itemId: ItemId): Try[Unit] = Try {
      if (entriesCount == 0)
        throw NoSuchItemException(itemId)
    }

    private def createFileSpecs(bagDir: BaseDir, itemPath: BaseDir, fileIds: Seq[FileId]): Try[Seq[EntrySpec]] = {
      fileIds.collect {
        case fileId if !fileId.isDirectory =>
          fileSystem.toRealLocation(fileId)
            .map(source => createEntrySpec(Some(source), bagDir, itemPath, fileId))
      }.collectResults
    }

    private def createDirectorySpecs(bagDir: BaseDir, itemPath: BaseDir, fileIds: Seq[FileId]): Try[Seq[EntrySpec]] = Try {
      fileIds.collect { case fileId if fileId.isDirectory => createEntrySpec(None, bagDir, itemPath, fileId) }
    }

    private def copyToArchiveStream(outputStream: => OutputStream)(entries: () => Seq[EntrySpec])(archiveStreamType: ArchiveStreamType): Try[Unit] = {
      new ArchiveStream(archiveStreamType, entries()).writeTo(outputStream)
    }

    private def findFile(itemId: ItemId, fileIds: Seq[FileId], entriesCount: Int): Try[Path] = {
      entriesCount match {
        case 0 => Failure(NoSuchItemException(itemId))
        case 1 => fileSystem.toRealLocation(fileIds.head)
        case _ => Failure(NoRegularFileException(itemId))
      }
    }

    private def validateThatBagDirIsNotHidden(bagDirPath: Path, itemId: ItemId, forceInactive: Boolean): Try[Unit] = Try {
      if (!forceInactive && Files.isHidden(bagDirPath)) throw InactiveException(itemId, forceInactive = forceInactive)
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
          maybeRefbags <- bagProcessing.getReferenceBags(path, bagId)
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
          _ = if (!skipStage) FileUtils.deleteDirectory(staging.toFile)
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
      bagProcessing.setPermissions(staging.resolve(bagName), fileSystem.bagFilePermissions, fileSystem.bagDirPermissions, includeTopDir = false)
        .map(Files.move(_, moved))
        // We cannot move bagDir if it is read-only, so set file permissions after moving.
        .map(Files.setPosixFilePermissions(_, fileSystem.bagDirPermissions))
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
      } yield Files.move(path, path.getParent.resolve(s".${ path.getFileName }"))
    }

    def reactivate(bagId: BagId): Try[Unit] = {
      for {
        _ <- fileSystem.checkBagExists(bagId)
        path <- fileSystem.toLocation(bagId)
        _ <- if (!Files.isHidden(path)) Failure(NotInactiveException(bagId))
             else Success(())
      } yield Files.move(path, path.getParent.resolve(s"${ path.getFileName.toString.substring(1) }"))
    }

    def locate(itemId: ItemId, resolveToFileDataLocation: Boolean = false): Try[Path] = {
      if (resolveToFileDataLocation) itemId.toFileId.flatMap(fileSystem.toRealLocation)
      else fileSystem.toLocation(itemId)
    }
  }

  private def validatePathAndResolveResultDirectory(itemId: ItemId, output: BaseDir, forceInactive: Boolean, path: BaseDir): Path = {
    if (Files.isHidden(path) && !forceInactive) throw InactiveException(itemId, forceInactive)
    val resultDir = output.resolve(path.getFileName).toAbsolutePath
    if (Files.exists(resultDir)) throw OutputAlreadyExists(resultDir)
    resultDir
  }

  object BagStore {
    def apply(dir: BaseDir): BagStore = new BagStore {
      implicit val baseDir: BaseDir = dir
    }
  }
}

