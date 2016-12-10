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

import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
 * Provides the bag-store context, which consists of:
 *
 * - a known base directory (base-dir)
 * - a known base URI (base-uri)
 * - a few simple rules for mapping IDs and URIs to locations and v.v.
 *
 * See project's README for details.
 */
trait BagStoreContext extends DebugEnhancedLogging with BagIt {
  implicit val baseDir: Path
  // Must be absolute.
  implicit val baseUri: URI
  implicit val uuidPathComponentSizes: Seq[Int]

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
  protected def toLocation(id: ItemId): Try[Path] = Try {
    def uuidToPath(uuid: UUID): Path = {
      val (result, _) = uuidPathComponentSizes.foldLeft((Seq[String](), uuid.toString.filterNot(_ == '-'))) {
        case ((acc, rest), size) =>
          val (next, newRest) = rest.splitAt(size)
          (acc :+ next, newRest)
      }
      Paths.get(result.head, result.tail: _*)
    }

    val container = baseDir.resolve(uuidToPath(id.getUuid))
    val containedFiles = Files.list(container).iterator().asScala.toList
    assert(containedFiles.size == 1, s"Corrupt BagStore, container with less or more than one file: $container")
    val bagDir = container.resolve(containedFiles.head)

    id match {
      case b: BagId => bagDir
      case f: FileId => bagDir.resolve(f.path)
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



//
//
//  /**
//   * Returns the actual path of the item pointed to by path. For bag-sequences and bags this is always the same
//   * as `path`. For file-items this may be a different location, pointed to in the bag's `fetch.txt` file.
//   *
//   * @param path the item-location
//   * @return the actual location
//   */
//  protected def getFetchPath(path: Path): Try[Path] = {
//    if (Files.exists(path)) fromLocation(path).map(toLocation) // A bit wasteful, but we'll catch out-of-store paths this way.
//    else
//      fromLocation(path)
//        .flatMap(ItemId.toFileId)
//        .map(_.bagId)
//        .map(toLocation)
//        .map {
//          case bagPath =>
//            val filePathInBag = bagPath.relativize(path)
//            bagFacade.getFetchItems(bagPath).flatMap {
//              case fetchItems => fetchItems.get(filePathInBag)
//                .map {
//                  case fi => fromUri(fi.uri).map(toLocation)
//                }.get // TODO: refactor away get
//            }
//        }.get // TODO: refactor away get
//  }
//
//
//  /**
//   * Returns the item-uri for a given item-id.
//   *
//   * @param id the item-id
//   * @return the item-uri
//   */
//  protected def toUri(id: ItemId): URI = baseUri.resolve(id.toString)
//
//  /**
//   * Utility function that copies a directory to a staging area. This is used to stage bag directories for
//   * ingest or dissemination.
//   *
//   * @param dir the directory to stage
//   * @return the location of the staged directory
//   */
//  protected def stageDirectory(dir: Path): Try[Path] = Try {
//    trace(dir)
//    val staged = Files.createTempFile("staged-bag-", "")
//    Files.deleteIfExists(staged)
//    FileUtils.copyDirectory(dir.toFile, staged.toFile)
//    debug(s"Staged directory $dir in $staged")
//    staged
//  }
//
//  protected def resolvePossiblyFileItemUri(uri: URI): URI = {
//    trace(uri)
//    (fromUri(uri) map {
//      case fileId: FileId => toLocation(fileId).toUri
//      case _ => sys.error(s"Non-file-uri item-uri: $uri.")
//    }).get  // TODO: refactor calling function to handle Tries
//  }

  protected def isVirtuallyValid(bagDir: Path): Try[Boolean] =  {
//    import nl.knaw.dans.lib.error._
//
//    def getMappings(fetchItems: Seq[FetchItem]): Try[Seq[(Path, Path)]] = {
//      fetchItems.map {
//        case item =>
//          val archivedCopy = fromUri(item.uri).flatMap(toLocation)
//
//
//      }
//
//      ???
//    }
//
//
//    val fetchTxt = bagDir.resolve(bagFacade.FETCH_TXT_FILENAME)
//    if (Files.exists(fetchTxt)) {
//      // TODO: convert to constant
//
//      for {
//        items <- bagFacade.getFetchItems(bagDir)
//        mappings <- getMappings(items)
//      } yield mappings
//
//
////      // Resolve fetch.txt to hard links
////      (bagFacade.getFetchItems(bagDir) flatMap {
////        items =>
////          items.map(item => fromUri(item.uri)
////            .flatMap(toLocation)
////            .map((_, item.path))).collectResults
////      } map (links => links.foreach { case (existing, pathInBag) => Files.createLink(bagDir.resolve(pathInBag), existing) }))
////        .flatMap { _ =>
////          val tempFetchTxt = Files.createTempFile("fetchtxt-backup", ".txt")
////          Files.delete(tempFetchTxt)
////          Files.move(fetchTxt, tempFetchTxt)
////          val result = bagFacade.isValid(bagDir)
////          Files.move(tempFetchTxt, fetchTxt)
////        }
//    } else
//      bagFacade.isValid(bagDir)

    ???
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

  private def formatUuidStrCanonically(s: String): String = {
    List(s.slice(0, 8), s.slice(8, 12), s.slice(12, 16), s.slice(16, 20), s.slice(20, 32)).mkString("-")
  }
}
