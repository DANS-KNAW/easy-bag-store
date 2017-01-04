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

import java.io.InputStream
import java.net.URI
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import net.lingala.zip4j.core.ZipFile
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

/**
 * Provides the bag-store context, which consists of:
 *
 * - a known base directory (base-dir)
 * - a known base URI (base-uri)
 * - a few simple rules for mapping IDs and URIs to locations and v.v.
 *
 * See project's README for details.
 */
trait BagStoreContext { this: BagFacadeComponent with DebugEnhancedLogging =>
  implicit val baseDir: Path
  // Must be absolute.
  implicit val baseUri: URI
  implicit val stagingBaseDir: Path
  implicit val uuidPathComponentSizes: Seq[Int]
  implicit val bagPermissions: String

  /**
   * Creates an item-id from a location inside the bag-store. Returns a `Failure` if the path does not point
   * to a well-formed item-location, for example because it is not located under the base-dir or has no UUID-part.
   * The path does *not* have to point to an actual file on the file system. Specifically, the file may be
   * included in bag through a `fetch.txt` reference. In any case, this function does *not* try to ascertain
   * whether the resulting `item-id` is valid (i.e. can be resolved to an actual item).
   *
   * @param path the path for which to construct an item-id
   * @return the item-id
   */
  protected def fromLocation(path: Path): Try[ItemId] = {
    Try {
      val p = baseDir.relativize(path.toAbsolutePath)
      val nameCount = p.getNameCount
      val uuidPartCount = uuidPathComponentSizes.size
      val bagBaseIndex = uuidPartCount
      val filePathIndex = bagBaseIndex + 1

      assertUuidPartitionedCorrectly(p.subpath(0, uuidPartCount))
      val uuidStr = formatUuidStrCanonically((0 until uuidPartCount).map(p.getName).mkString)
      assertUuidValid(uuidStr)
      val bagId = BagId(UUID.fromString(uuidStr))
      if (filePathIndex < nameCount) FileId(bagId, p.subpath(filePathIndex, p.getNameCount))
      else bagId
    }
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
  protected def fromUri(uri: URI): Try[ItemId] = {
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
    val UriComponents(baseUriScheme, _, baseUriHost, baseUriPort, baseUriPath, _, _) = baseUri

    if (scheme == baseUriScheme && host == baseUriHost && port == baseUriPort && (baseUriPath.toString == "" || path.startsWith(baseUriPath))) {
      // The path part after the base-uri is basically the item-id, but in a Path object.
      val itemIdPath = if (baseUriPath.toString != "") baseUriPath.relativize(path) else path
      if (StringUtils.isBlank(itemIdPath.toString))
        Failure(IncompleteItemUriException("base-uri by itself is not an item-uri"))
      else {
        val uuidStr = formatUuidStrCanonically(itemIdPath.getName(0).toString.filterNot(_ == '-'))
        assertUuidValid(uuidStr)
        val bagId = BagId(UUID.fromString(uuidStr))
        if (itemIdPath.getNameCount > 1)
          Try(FileId(bagId, itemIdPath.subpath(1, itemIdPath.getNameCount)))
        else if (uri.toString.endsWith("/"))
          Try(FileId(bagId, Paths.get("")))
        else
          Try(bagId)
      }
    } else Failure(NoItemUriException(uri, baseUri))
  }

  /**
   * Returns the location of the Bag's container directory.
   *
   * @param id
   * @return
   */
  def toContainer(id: ItemId): Try[Path] = Try {
    def uuidToPath(uuid: UUID): Path = {
      val (result, _) = uuidPathComponentSizes.foldLeft((Seq[String](), uuid.toString.filterNot(_ == '-'))) {
        case ((acc, rest), size) =>
          val (next, newRest) = rest.splitAt(size)
          (acc :+ next, newRest)
      }
      Paths.get(result.head, result.tail: _*)
    }

    baseDir.resolve(uuidToPath(id.getUuid))
  }

  /**
   * Returns the location in the file system for a given item. Since the bag's base directory name is not part of
   * the bag-id, only one directory is allowed under the UUID-part of the path which must be the bag directory. If there is more
   * than one directory, the function fails indicating a corrupt BagStore.
   *
   * When the item is a file it may not actually be located there, but only virtually, meaning that it is projected
   * at this location via a `fetch.txt` file. Use BagStoreContext#getFetchPath to get the actual location.
   *
   * @param id the item-id
   * @return the item-location
   */
  protected def toLocation(id: ItemId): Try[Path] = {
    toContainer(id).flatMap {
      container => Try {
        val containedFiles = Files.list(container).iterator().asScala.toList
        assert(containedFiles.size == 1, s"Corrupt BagStore, container with less or more than one file: $container")
        val bagDir = container.resolve(containedFiles.head)

        id match {
          case b: BagId => bagDir
          case f: FileId => bagDir.resolve(f.path)
        }
      }
    }
  }

  /**
   * Returns the path at which the File with the specified file-id is actually stored in the BagStore.
   *
   * @param fileId
   */
  protected def toRealLocation(fileId: FileId): Try[Path] = {
    toLocation(fileId)
        .flatMap {
          path =>
            if (Files.exists(path))
              Try(path)
            else
              getFetchUri(fileId).flatMap(fromUri).flatMap {
                case fileId: FileId => toRealLocation(fileId)
              }
        }
  }

  private def getFetchUri(fileId: FileId): Try[URI] = {
    toLocation(fileId.bagId)
      .flatMap {
        bagDir =>
          bagFacade.getFetchItems(bagDir)
            .flatMap {
              items =>
                Try {
                  items.find(_.path == fileId.path)
                    .map(item => item.uri).get
                }
            }
      }
  }

  /**
   * Returns the item-uri for a given item-id.
   *
   * @param id the item-id
   * @return the item-uri
   */
  protected def toUri(id: ItemId): URI = baseUri.resolve("/" + id.toString)

  /**
   * Utility function that copies a directory to a staging area. This is used to stage bag directories for
   * ingest or dissemination.
   *
   * @param dir the directory to stage
   * @return the location of the staged directory
   */
  protected def stageBagDir(dir: Path): Try[Path] = Try {
    trace(dir)
    val staged = Files.createTempFile(stagingBaseDir, "staged-bag-", "")
    Files.deleteIfExists(staged)
    FileUtils.copyDirectory(dir.toFile, staged.toFile)
    debug(s"Staged directory $dir in $staged")
    staged
  }

  protected def stageBagZip(is: InputStream): Try[Path] = Try {
    trace(is)
    val extractDir = Files.createTempFile(stagingBaseDir, "staged-zip-", "")
    Files.deleteIfExists(extractDir)
    Files.createDirectory(extractDir)
    val zip = extractDir.resolve("bag.zip")
    FileUtils.copyInputStreamToFile(is, zip.toFile)
    new ZipFile(zip.toFile).extractAll(extractDir.toAbsolutePath.toString)
    Files.delete(zip)
    extractDir
  }.flatMap(findBagDir)

  private def findBagDir(extractDir: Path): Try[Path] = Try {
    val files = Files.list(extractDir).iterator().asScala.toList
    if (files.size != 1) throw IncorrectNumberOfFilesInBagZipRootException(files.size)
    else if(!Files.isDirectory(files.head)) throw BagBaseNotFoundException()
    else files.head
  }

  protected def mapProjectedToRealLocation(bagDir: Path): Try[Seq[(Path, Path)]] = {
    import nl.knaw.dans.lib.error._

    for {
      items <- bagFacade.getFetchItems(bagDir)
      xx <- items.map(item => {
        for {
          id <- fromUri(item.uri)
          fileId <- ItemId.toFileId(id)
          location <- toRealLocation(fileId)
        } yield (bagDir.toAbsolutePath.resolve(item.path), location)
      }).collectResults
    } yield xx
  }

  protected def isVirtuallyValid(bagDir: Path): Try[Boolean] =  {


    def getExtraDirectories(links: Seq[Path]): Try[Seq[Path]] = Try {
      val dirs = for {
        link <- links
        comps = link.iterator().asScala.toList
        path <- (bagDir.getNameCount until comps.size)
          .map(comps.take)
          .map(comps => Paths.get("/" + comps.mkString("/")))
          .filterNot(Files.exists(_))
      } yield path
      debug(s"extra directories to create: $dirs")
      dirs
    }


    def moveFetchTxtAndTagmanifestsToTempdir(): Try[Path] = Try {
      val tempDir = Files.createTempDirectory("fetch-temp-")
      debug(s"created temporary directory: $tempDir")
      debug(s"moving ${bagFacade.FETCH_TXT_FILENAME}")
      Files.move(bagDir.resolve(bagFacade.FETCH_TXT_FILENAME), tempDir.resolve(bagFacade.FETCH_TXT_FILENAME))
      // Attention the `toString` after `getFileName` is necessary because Path.startsWith only matches complete path components!
      val tagmanifests = Files.list(bagDir).iterator().asScala.withFilter(_.getFileName.toString.startsWith("tagmanifest-")).toList
      debug(s"tagmanifests: $tagmanifests")
      tagmanifests.foreach(t => Files.move(t, tempDir.resolve(t.getFileName)))
      tempDir
    }

    def moveFetchTxtAndTagmanifestsBack(path: Path): Try[Unit] = Try {
      Files.list(path).iterator().asScala.foreach(f => {
        debug(s"Moving $f -> ${bagDir.resolve(f.getFileName)}")
        Files.move(f, bagDir.resolve(f.getFileName))
      })
      Files.delete(path)
    }

    def createLinks(mappings: Seq[(Path, Path)]): Try[Unit] = Try {
      mappings.foreach {
        case (link, to) =>
          if (!Files.exists(link.getParent))
            Files.createDirectories(link.getParent)
          Files.createLink(link, to)
      }
    }

    def removeLinks(mappings: Seq[(Path, Path)]): Try[Unit] = Try {
      debug(s"mappings (link -> real location): $mappings")
      mappings.foreach {
        case (link, _) =>
          debug(s"Deleting: $link")
          Files.deleteIfExists(link)
      }
    }

    def removeDirectories(dirs: Seq[Path]): Try[Unit] = Try {
      debug(s"directories: $dirs")
      dirs.foreach(Files.delete)
    }

    val fetchTxt = bagDir.resolve(bagFacade.FETCH_TXT_FILENAME)
    if (Files.exists(fetchTxt)) {
        for {
          mappings <- mapProjectedToRealLocation(bagDir)
          extraDirs <- getExtraDirectories(mappings.map { case (link, to) => link })
          validTagManifests <- bagFacade.hasValidTagManifests(bagDir)
          _ = debug(s"valid tagmanifests: $validTagManifests")
          tempLocFetch <- moveFetchTxtAndTagmanifestsToTempdir()
          _ <- createLinks(mappings)
          valid <- bagFacade.isValid(bagDir)
          _ = debug(s"valid bag: $valid")
          _ <- removeLinks(mappings)
          _ <- removeDirectories(extraDirs)
          _ <- moveFetchTxtAndTagmanifestsBack(tempLocFetch)
        } yield validTagManifests && valid
    }
    else
      bagFacade.isValid(bagDir)
  }

  private def assertUuidValid(uuid: String) = {
    assert(Try(UUID.fromString(uuid)).map(_ => true).getOrElse(false), s"UUID ($uuid) is not valid")
  }

  private def assertUuidPartitionedCorrectly(uuidPath: Path) = {
    assert(uuidPath.iterator().asScala
      .toList
      .map(_.toString.length) ==
      uuidPathComponentSizes, "UUID-part slashed incorrectly")
  }

  protected def formatUuidStrCanonically(s: String): String = {
    List(s.slice(0, 8), s.slice(8, 12), s.slice(12, 16), s.slice(16, 20), s.slice(20, 32)).mkString("-")
  }

  protected def checkBagExists(bagId: BagId): Try[Unit] = {
    toContainer(bagId).map {
      f =>
        /*
         * If the container exists, the Bag must exist. This function does not check for corruption of the BagStore.
         */
        if(Files.exists(f) && Files.isDirectory(f)) ()
        else throw NoSuchBagException(bagId)
    }
  }

  protected def checkBagDoesNotExist(bagId: BagId): Try[Unit] = {
    toContainer(bagId).map {
      f =>
        if (Files.exists(f) && Files.isDirectory(f)) throw BagIdAlreadyAssignedException(bagId)
        else ()
    }
  }

  protected def isHidden(bagId: BagId): Try[Boolean] = {
    toLocation(bagId).map(Files.isHidden)
  }

  protected def setPermissions(permissions: String)(bagDir: Path): Try[Unit] = Try {
    Files.walk(bagDir).iterator().asScala.toList.foreach {
      f => Files.setPosixFilePermissions(f, PosixFilePermissions.fromString(permissions))
    }
  }

}
