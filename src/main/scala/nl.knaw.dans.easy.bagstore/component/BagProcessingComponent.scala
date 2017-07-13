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

import java.io.{ IOException, InputStream }
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.{ BasicFileAttributes, PosixFilePermissions }
import java.nio.file.{ FileVisitResult, FileVisitor, Files, Path }

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils

import scala.collection.mutable
import scala.util.{ Failure, Success, Try }
import nl.knaw.dans.lib.error._

trait BagProcessingComponent extends DebugEnhancedLogging {
  this: FileSystemComponent with BagFacadeComponent =>

  val processor: BagProcessing

  trait BagProcessing {

    val stagingBaseDir: Path
    val outputBagPermissions: String

    // TODO: This function looks a lot like BagStoreContext.isVirtuallyValid.createLinks, refactor?
    def complete(bagDir: Path)(implicit baseDir: BaseDir): Try[Unit] = {
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
          setPermissions()(to).get
        }
      }

      for {
        virtuallyValid <- fileSystem.isVirtuallyValid(bagDir)
        _ = debug(s"input virtually-valid?: $virtuallyValid")
        if virtuallyValid
        mappings <- fileSystem.projectedToRealLocation(bagDir)
        _ <- copyFiles(mappings)
        _ <- bagFacade.removeFetchTxtFromTagManifests(bagDir)
        _ <- Try { Files.deleteIfExists(bagDir.resolve(bagFacade.FETCH_TXT_FILENAME)) }
        valid <- bagFacade.isValid(bagDir)
        _ = debug(s"result valid?: $valid")
        if valid
      } yield ()
    }

    def setFilePermissions(permissions: String = outputBagPermissions)(path: Path): Try[Path] = Try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
    }

    def setPermissions(permissions: String = outputBagPermissions)(bagDir: Path): Try[Path] = Try {
      logger.info(s"Setting bag permissions to: $permissions, bag directory: $bagDir")
      val posixFilePermissions = PosixFilePermissions.fromString(permissions)
      object SetPermissionsFileVisitor extends FileVisitor[Path] {
        override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
          logger.error(s"Could not visit file $file", exc)
          FileVisitResult.TERMINATE
        }

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          trace(file, attrs)
          if (logger.underlying.isDebugEnabled) logAttributes(file, attrs)
          Files.setPosixFilePermissions(file, posixFilePermissions)
          FileVisitResult.CONTINUE
        }

        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          trace(dir, attrs)
          debug(s"Entering directory: $dir, iwth attributes: $attrs")
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          trace(dir, exc)
          if(exc != null) {
            logger.error(s"Error when visiting directory: $dir", exc)
            FileVisitResult.TERMINATE
          }
          else FileVisitResult.CONTINUE
        }

        private def logAttributes(path: Path, attrs: BasicFileAttributes): Unit = {
          debug(s"FILE:             $path")
          debug(s"creationTime:     ${attrs.creationTime}")
          debug(s"fileKey:          ${attrs.fileKey}")
          debug(s"isDirectory:      ${attrs.isDirectory}")
          debug(s"isOther:          ${attrs.isOther}")
          debug(s"isRegularFile:    ${attrs.isRegularFile}")
          debug(s"isSymbolicLink:   ${attrs.isSymbolicLink}")
          debug(s"lastAccessTime:   ${attrs.lastAccessTime}")
          debug(s"lastModifiedTime: ${attrs.lastModifiedTime}")
          debug(s"size:             ${attrs.size}")
        }
      }

      Files.walkFileTree(bagDir, SetPermissionsFileVisitor)
    }

    def stageBagDir(dir: Path) = Try {
      trace(dir)
      val staging = Files.createTempFile(stagingBaseDir, "bagdir-staging-", "")
      Files.deleteIfExists(staging)
      FileUtils.copyDirectoryToDirectory(dir.toFile, staging.toFile)
      debug(s"Staging directory $dir into $staging")
      staging
    }

    def stageBagZip(path: Path): Try[Path] = Try {
      trace(path)
      val staging = Files.createTempFile(stagingBaseDir, "bagzip-staging-", "")
      Files.deleteIfExists(staging)
      Files.createDirectory(staging)
      val zf = new ZipFile(staging.resolve(path.getFileName).toFile)
      val parameters = new ZipParameters
      zf.addFolder(path.toFile, parameters)
      staging
    }

    def unzipBag(is: InputStream): Try[Path] = {
      Try {
        trace(is)
        val extractDir = Files.createTempFile(stagingBaseDir, "bagzip-staging-", "")
        Files.deleteIfExists(extractDir)
        Files.createDirectory(extractDir)
        val zip = extractDir.resolve("bag.zip")
        FileUtils.copyInputStreamToFile(is, zip.toFile)
        new ZipFile(zip.toFile) {
          setFileNameCharset(StandardCharsets.UTF_8.name)
        }.extractAll(extractDir.toAbsolutePath.toString)
        Files.delete(zip)
        extractDir
      }.recoverWith {
        case e: ZipException => Failure(NoBagException(e))
      }
    }

    def findBagDir(extractDir: Path): Try[Path] = Try {
      val files = listFiles(extractDir)
      if (files.size != 1) throw IncorrectNumberOfFilesInBagZipRootException(files.size)
      else if(!Files.isDirectory(files.head)) throw BagBaseNotFoundException()
      else files.head
    }

    def getReferenceBags(bagDir: Path): Try[Option[Path]] = Try {
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

    /**
     * Takes a virtually-valid Bag and a list of bag-ids of reference Bags. The Bag is searched for files that are already in one
     * of the Reference Bags. These files are removed from the Bag and included from one of the reference Bags through a
     * fetch.txt entry. This way the Bag stays virtually-valid while possibly taking up less storage space.
     *
     * @param bagDir the Bag to prune
     * @param refBag the reference Bags to search
     * @return
     */
    def prune(bagDir: Path, refBag: Seq[BagId])(implicit baseDir: BaseDir): Try[Unit] = {
      replaceRedundantFilesWithFetchReferences(bagDir, refBag.toList)
    }

    private def replaceRedundantFilesWithFetchReferences(bagDir: Path, refBags: List[BagId])(implicit baseDir: BaseDir): Try[Unit] = {
      trace(bagDir, refBags)
      val result = for {
        refBagLocations <- refBags.map(fileSystem.toLocation).collectResults
        algorithm <- getWeakestCommonSupportedAlgorithm(bagDir :: refBagLocations)
        _ <- if (algorithm.isDefined) Success(()) else Failure(new IllegalStateException("Bag and reference Bags have no common payload manifest algorithm"))
        checksumToUri <- getChecksumToUriMap(refBags, algorithm.get)
        fileChecksumMap <- bagFacade.getPayloadManifest(bagDir, algorithm.get)
        fileCoolUriMap = for {
          (path, checksum) <- fileChecksumMap
          uri <- checksumToUri.get(checksum)
        } yield path -> uri
        fetchList <- Try(fileCoolUriMap.foldLeft(mutable.ListBuffer.empty[FetchItem]) {
          case (acc, (path, uri)) =>
            val fileInNewBag = bagDir.resolve(path)
            fileSystem.fromUri(uri).flatMap(fileSystem.toLocation) match {
              case Success(file) if FileUtils.contentEquals(fileInNewBag.toFile, file.toFile) =>
                Files.delete(fileInNewBag)
                if (!Files.list(fileInNewBag.getParent).findAny().isPresent) Files.delete(fileInNewBag.getParent)
                acc += FetchItem(uri, Files.size(file), path)
              case Success(_) => acc // do nothing
              case Failure(e) => throw e
            }
        }.toList)
        _ <- if(fetchList.nonEmpty) bagFacade.writeFetchFile(bagDir, fetchList) else Success(())
      } yield ()
      debug(s"returning $result")
      result
    }

    private def getChecksumToUriMap(refBags: Seq[BagId], algorithm: Algorithm)(implicit baseDir: BaseDir): Try[Map[String, URI]] = {
      refBags.map(getChecksumToUriMap(_, algorithm)).collectResults.map(_.reduce(_ ++ _))
    }

    private def getChecksumToUriMap(refBag: BagId, algorithm: Algorithm)(implicit baseDir: BaseDir): Try[Map[String, URI]] = {
      trace(refBag, algorithm)
      for {
        refBagDir <- fileSystem.toLocation(refBag)
        manifest <- bagFacade.getPayloadManifest(refBagDir, algorithm)
        map <- manifest.map { case (pathInBag, checksum) =>
          fileSystem.toRealLocation(FileId(refBag, pathInBag))
            .flatMap(fileSystem.fromLocation)
            .map(checksum -> fileSystem.toUri(_))
        }.filter(_.isSuccess).collectResults.map(_.toMap)
      } yield map
    }

    private def getWeakestCommonSupportedAlgorithm(bagDirs: Seq[Path]): Try[Option[Algorithm]] = {
      getSupportedAlgorithmsPerBag(bagDirs).map(bagFacade.getWeakestCommonAlgorithm)
    }

    private def getSupportedAlgorithmsPerBag(bagDirs: Seq[Path]): Try[Set[Set[Algorithm]]] = {
      trace(bagDirs)
      bagDirs.map(bagFacade.getSupportedManifestAlgorithms).collectResults.map(_.toSet)
    }
  }
}
