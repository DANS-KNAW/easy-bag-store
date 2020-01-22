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

import java.net.URI
import java.nio.file._
import java.nio.file.attribute.{ BasicFileAttributes, PosixFilePermission }
import java.util.stream.{ Stream => JStream }
import java.util.{ UUID, Set => JSet }

import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.string._
import org.apache.commons.io.FileUtils

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

trait FileSystemComponent extends DebugEnhancedLogging {
  this: BagFacadeComponent =>

  val fileSystem: FileSystem

  /**
   * A path to a bag in a store
   */
  type BagPath = Path

  trait FileSystem {
    val localBaseUri: URI
    val uuidPathComponentSizes: Seq[Int]
    val bagFilePermissions: JSet[PosixFilePermission]
    val bagDirPermissions: JSet[PosixFilePermission]

    /**
     * @return Lazily populated JStream with bags in this base directory
     */
    def walkStore(implicit baseDir: BaseDir): JStream[BagPath] = {
      Files.walk(baseDir, uuidPathComponentSizes.size, FileVisitOption.FOLLOW_LINKS)
        .filter(baseDir.relativize(_).getNameCount == uuidPathComponentSizes.size)
    }

    def fromLocation(path: Path)(implicit baseDir: BaseDir): Try[ItemId] = {
      Try {
        val p = baseDir.relativize(path.toAbsolutePath)
        val nameCount = p.getNameCount
        val uuidPartCount = uuidPathComponentSizes.size
        val bagBaseIndex = uuidPartCount
        val filePathIndex = bagBaseIndex + 1

        assertUuidPartitionedCorrectly(p.subpath(0, uuidPartCount))
        val uuidStr = formatUuidStrCanonically((0 until uuidPartCount).map(p.getName).mkString)
        val bagId = BagId(getUUID(uuidStr))
        if (filePathIndex < nameCount) FileId(bagId, p.subpath(filePathIndex, p.getNameCount))
        else bagId
      }
    }

    private def assertUuidPartitionedCorrectly(uuidPath: Path): Unit = {
      assert(uuidPath.asScala.map(_.toString.length) == uuidPathComponentSizes, "UUID-part slashed incorrectly")
    }

    private def formatUuidStrCanonically(s: String): String = {
      List(s.slice(0, 8), s.slice(8, 12), s.slice(12, 16), s.slice(16, 20), s.slice(20, 32)).mkString("-")
    }

    /**
     * Creates an item-id from a URI. Returns a `Failure` if the URI is not a well-formed item-uri, for example because
     * it doesn't have a prefix that matches the base-uri, or it doesn't have UUID-part. In any case, this function does
     * *not* try to ascertain whether the resulting `item-id` is valid (i.e. can be resolved to an actual item).
     *
     * If the item-uri is a non-persistent file-uri the function will resolve it. Therefore, as a partial exception to
     * the rule stated at the end of the previous paragraph, the function *will* fail if the item-uri points into a
     * non-existent bag-sequence. The reason for this is that it has to find out the latest revision number in order to
     * resolve the non-persistent file-uri. The rule still holds insofar that the function does *not* check whether the
     * latest revision actually contains the particular file pointed to.
     *
     * @param uri the item-uri
     * @return the item-id
     */
    def fromUri(uri: URI): Try[ItemId] = {
      object UriComponents {
        def unapply(uri: URI): Option[(String, String, String, Int, Path, String, String)] = Some((
          uri.getScheme,
          uri.getUserInfo,
          uri.getHost,
          uri.getPort,
          Paths.get(uri.getPath),
          uri.getQuery,
          uri.getFragment))
      }

      val UriComponents(scheme, _, host, port, path, _, _) = uri
      val UriComponents(baseUriScheme, _, baseUriHost, baseUriPort, baseUriPath, _, _) = localBaseUri

      if (scheme == baseUriScheme && host == baseUriHost && port == baseUriPort && (baseUriPath.toString == "" || path.startsWith(baseUriPath))) {
        // The path part after the base-uri is basically the item-id, but in a Path object.
        val itemIdPath = if (baseUriPath.toString.nonEmpty) baseUriPath.relativize(path)
                         else path
        if (itemIdPath.toString.isBlank)
          Failure(IncompleteItemUriException("base-uri by itself is not an item-uri"))
        else {
          val uuidStr = formatUuidStrCanonically(itemIdPath.getName(0).toString.filterNot(_ == '-'))
          uuidStr.toUUID.toTry match {
            case Success(uuid) =>
              val bagId = BagId(uuid)
              if (itemIdPath.getNameCount > 1)
                Try(FileId(bagId, itemIdPath.subpath(1, itemIdPath.getNameCount)))
              else if (uri.toString.endsWith("/"))
                     Try(FileId(bagId, Paths.get("")))
              else
                Try(bagId)
            case Failure(e) => Failure(new IllegalArgumentException(e.getMessage))
          }
        }
      }
      else Failure(NoItemUriException(uri, localBaseUri))
    }

    /**
     * Returns the location of the Bag's container directory.
     *
     * @param id the item-id
     * @return
     */
    def toContainer(id: ItemId)(implicit baseDir: BaseDir): Try[Path] = Try {
      def uuidToPath(uuid: UUID): Path = {
        // TODO what if this list is empty? pattern match will fail!
        // this will only happen when uuidPathComponentSizes is empty
        val (head :: tail, _) = uuidPathComponentSizes.foldLeft((List.empty[String], uuid.toString.filterNot(_ == '-'))) {
          case ((acc, rest), size) =>
            val (next, newRest) = rest.splitAt(size)
            (acc :+ next, newRest)
        }
        Paths.get(head, tail: _*)
      }

      baseDir.resolve(uuidToPath(id.uuid))
    }

    /**
     * Returns the location in the file system for a given item. Since the bag's base directory name is not part of
     * the bag-id, only one directory is allowed under the UUID-part of the path which must be the bag directory. If there is more
     * than one directory, the function fails indicating a corrupt BagStore.
     *
     * When the item is a file it may not actually be located there, but only virtually, meaning that it is projected
     * at this location via a `fetch.txt` file. Use FileSystemComponent#getFetchUri to get the actual location.
     *
     * @param id the item-id
     * @return the item-location
     */
    def toLocation(id: ItemId)(implicit baseDir: BaseDir): Try[Path] = {
      for {
        container <- toContainer(id)
        path <- Try {
          val containedFiles = listFiles(container)
          assert(containedFiles.size == 1, s"Corrupt BagStore, container with less or more than one file: $container")
          val bagDir = container.resolve(containedFiles.head)

          id match {
            case _: BagId => bagDir
            case FileId(_, path, _) => bagDir.resolve(path)
          }
        }
        _ = debug(s"Item $id located at $path")
      } yield path
    }

    /**
     * Returns the path at which the File with the specified file-id is actually stored in the BagStore.
     * The file-id must refer to a regular file and not a directory. (Directories may have many "real"
     * locations, so it is not clear which one to return.)
     *
     * @param fileId id of the file to look for
     */
    def toRealLocation(fileId: FileId)(implicit baseDir: BaseDir): Try[Path] = {
      require(!fileId.isDirectory, "file-id must refer to a regular file")
      for {
        path <- toLocation(fileId)
        realPath <- if (Files.exists(path)) Success(path)
                    else getFetchUri(fileId)
                      .flatMap(_.map(fromUri).getOrElse(Failure(NoSuchFileItemException(fileId))))
                      .flatMap { case fileId: FileId => toRealLocation(fileId) }
      } yield realPath
    }

    private def getFetchUri(fileId: FileId)(implicit baseDir: BaseDir): Try[Option[URI]] = {
      for {
        bagDir <- toLocation(fileId.bagId)
        items <- bagFacade.getFetchItems(bagDir)
      } yield items.find(item => bagDir.relativize(item.path) == fileId.path).map(_.uri)
    }

    /**
     * Returns the item-uri for a given item-id.
     *
     * @param id the item-id
     * @return the item-uri
     */
    def toUri(id: ItemId): URI = localBaseUri.resolve("/" + id.toString)

    def projectedToRealLocation(bagDir: Path)(implicit baseDir: BaseDir): Try[Seq[(Path, Path)]] = {
      for {
        items <- bagFacade.getFetchItems(bagDir)
        mapping <- items.map(item => {
          for {
            id <- fromUri(item.uri)
            fileId <- id.toFileId
            location <- toRealLocation(fileId)
          } yield (bagDir.toAbsolutePath.resolve(item.path), location)
        } recoverWith {
          case nsfe: NoSuchFileException => Failure(new IllegalArgumentException(s"Local-file-uri found in fetch.txt can not be found in the bag-store: ${ nsfe.getMessage }"))
        }).collectResults
      } yield mapping
    }

    def isVirtuallyValid(bagDir: Path)(implicit baseDir: BaseDir): Try[Either[String, Unit]] = {
      val fetchTxt = bagDir.resolve(bagFacade.FETCH_TXT_FILENAME)
      if (Files.exists(fetchTxt))
        for {
          tempDir <- Try { Files.createTempDirectory("virtual-bag-") }
          workBag <- symlinkCopy(bagDir, tempDir.resolve(bagDir.getFileName))
          mappings <- projectedToRealLocation(workBag)
          validTagManifests <- bagFacade.hasValidTagManifests(workBag)
          _ = debug(s"valid tagmanifests: $validTagManifests")
          _ <- removeFetchTxtAndTagManifests(workBag)
          _ <- createSymLinks(mappings)
          validWorkBag <- bagFacade.isValid(workBag)
          _ = debug(validWorkBag.fold(msg => s"invalid bag: $msg", _ => "valid bag"))
          _ <- Try { FileUtils.deleteDirectory(tempDir.toFile) }
        } yield {
          validTagManifests.fold(
            tmMsg => validWorkBag.fold(bagMsg => Left(s"$tmMsg\n$bagMsg"), _ => Left(tmMsg)),
            _ => validWorkBag.fold(Left(_), Right(_))
          )
        }
      else
        bagFacade.isValid(bagDir)
    }

    private def removeFetchTxtAndTagManifests(bagDir: Path): Try[Unit] = Try {
      import scala.collection.JavaConverters._
      resource.managed(Files.list(bagDir)).acquireAndGet {
        _.iterator().asScala.filter(
          f => f.getFileName.toString == bagFacade.FETCH_TXT_FILENAME
            || f.getFileName.toString.startsWith("tagmanifest-")).foreach(Files.delete)
      }
    }

    /**
     * Creates a copy of a directory tree, in which every 'regular' file is a symlink back to the source tree.
     *
     * @param src    the root of the tree to copy
     * @param target the root of the copy
     * @return
     */
    private def symlinkCopy(src: Path, target: Path): Try[Path] = Try {
      Files.walkFileTree(src, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.createSymbolicLink(target.resolve(src.relativize(file)), file.toAbsolutePath)
          FileVisitResult.CONTINUE
        }

        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.createDirectory(target.resolve(src.relativize(dir)))
          FileVisitResult.CONTINUE
        }
      })
      target
    }

    private def createSymLinks(mappings: Seq[(Path, Path)]): Try[Unit] = Try {
      mappings.foreach { case (link, to) =>
        if (!Files.exists(link.getParent))
          Files.createDirectories(link.getParent)
        Files.createSymbolicLink(link, to)
      }
    }

    def checkBagExists(bagId: BagId)(implicit baseDir: BaseDir): Try[Unit] = {
      trace(bagId)
      toContainer(bagId)
        .flatMap {
          /*
           * If the container exists, the Bag must exist. This function does not check for corruption of the BagStore.
           */
          case f if Files.exists(f) && Files.isDirectory(f) =>
            debug("Returning success (bag exists)")
            Success(())
          case _ =>
            debug("Return failure (bag does not exist)")
            Failure(NoSuchBagException(bagId))
        }
    }

    // FIXME: only used in BagStoreComponent
    def makePathAndParentsInBagStoreGroupWritable(path: Path)(implicit baseDir: BaseDir): Try[Unit] = {
      for {
        seq <- getPathsInBagStore(path)
        _ <- seq.map(makeGroupWritable).collectResults
      } yield ()
    }

    private def getPathsInBagStore(path: Path)(implicit baseDir: BaseDir): Try[Seq[Path]] = Try {
      val pathComponents = baseDir.relativize(path).asScala.toSeq
      pathComponents.indices.map(i => baseDir.resolve(pathComponents.slice(0, i + 1).mkString("/")))
    }

    private def makeGroupWritable(path: Path): Try[Unit] = Try {
      val permissions = Files.getPosixFilePermissions(path).asScala
      Files.setPosixFilePermissions(path, permissions.union(Set(PosixFilePermission.GROUP_WRITE)).asJava)
    }

    // FIXME: only used in BagStoreComponent
    def removeEmptyParentDirectoriesInBagStore(container: Path)(implicit baseDir: BaseDir): Try[Unit] = {
      for {
        paths <- getPathsInBagStore(container)
        _ <- paths.reverse
          .map(path => Try { if (listFiles(path).isEmpty) Files.delete(path) })
          .collectResults
          .map(_ => ())
      } yield ()
    }
  }
}
