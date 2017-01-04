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
import java.nio.file.{Files, Path}

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

trait BagStorePrune { this: BagFacadeComponent with BagStoreContextComponent with DebugEnhancedLogging =>

  /**
   * Takes a virtually-valid Bag and a list of bag-ids of reference Bags. The Bag is searched for files that are already in one
   * of the Reference Bags. These files are removed from the Bag and included from one of the reference Bags through a
   * fetch.txt entry. This way the Bag stays virtually-valid while possibly taking up less storage space.
   *
   * @param bagDir the Bag to prune
   * @param refBag the reference Bags to search
   * @return
   */
  def prune(bagDir: Path, refBag: BagId*): Try[Unit] = {
    replaceRedundantFilesWithFetchReferences(bagDir, refBag.toList)
  }

  private def replaceRedundantFilesWithFetchReferences(bagDir: Path, refBags: List[BagId]): Try[Unit] = {
    trace(bagDir, refBags)
    val result = for {
      refBagLocations <- refBags.map(context.toLocation).collectResults
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
          context.fromUri(uri).flatMap(context.toLocation) match {
            case Success(file) if FileUtils.contentEquals(fileInNewBag.toFile, file.toFile) =>
              Files.delete(fileInNewBag)
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

  private def getChecksumToUriMap(refBags: Seq[BagId], algorithm: Algorithm): Try[Map[String, URI]] = {
    refBags.map(getChecksumToUriMap(_, algorithm)).collectResults.map(_.reduce(_ ++ _))
  }

  private def getChecksumToUriMap(refBag: BagId, algorithm: Algorithm): Try[Map[String, URI]] = {
    trace(refBag, algorithm)
    for {
      refBagDir <- context.toLocation(refBag)
      manifest <- bagFacade.getPayloadManifest(refBagDir, algorithm)
      map <- manifest.map { case (pathInBag, checksum) =>
        context.toRealLocation(FileId(refBag, pathInBag))
          .flatMap(context.fromLocation)
          .map(checksum -> context.toUri(_))
      }.filter(_.isSuccess).collectResults.map(_.toMap)
    } yield map
  }

  private def getWeakestCommonSupportedAlgorithm(bagDirs: Seq[Path]): Try[Option[Algorithm]] =
    getSupportedAlgorithmsPerBag(bagDirs).map(bagFacade.getWeakestCommonAlgorithm)

  private def getSupportedAlgorithmsPerBag(bagDirs: Seq[Path]): Try[Set[Set[Algorithm]]] = {
    trace(bagDirs)
    bagDirs.map(bagFacade.getSupportedManifestAlgorithms).collectResults.map(_.toSet)
  }
}
