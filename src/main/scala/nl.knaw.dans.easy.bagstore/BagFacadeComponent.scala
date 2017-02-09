/**
 * Copyright (C) 2016-17 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
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
import java.nio.file.{Path, Paths}

import gov.loc.repository.bagit.writer.impl.FileSystemHelper
import gov.loc.repository.bagit.{Bag, BagFactory, FetchTxt}
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import resource.Using

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

sealed abstract class Algorithm(val strength: Int)
case object MD5 extends Algorithm(1)
case object SHA1 extends Algorithm(2)
case object SHA256 extends Algorithm(3)
case object SHA512 extends Algorithm(4)

case class FetchItem(uri: URI, size: Long, path: Path)

case class BagNotFoundException(bagDir: Path, cause: Throwable) extends Exception(s"A bag could not be loaded at $bagDir", cause)

trait BagFacadeComponent {

  val bagFacade: BagFacade

  trait BagFacade { this: DebugEnhancedLogging =>
    val FETCH_TXT_FILENAME = "fetch.txt"

    def isValid(bagDir: Path): Try[Boolean]

    def hasValidTagManifests(bagDir: Path): Try[Boolean]

    def getPayloadManifest(bagDir: Path, algorithm: Algorithm): Try[Map[Path, String]]

    def getFetchItems(bagDir: Path): Try[Seq[FetchItem]]

    def getSupportedManifestAlgorithms(bagDir: Path): Try[Set[Algorithm]]

    def getWeakestCommonAlgorithm(algorithmSets: Set[Set[Algorithm]]): Option[Algorithm]

    def writeFetchFile(bagDir: Path, fetchList: List[FetchItem]): Try[Unit] =
      Using.fileWriter()(bagDir.resolve("fetch.txt").toFile)
        .map(writer => fetchList.foreach {
          case FetchItem(uri, size, path) => writer.write(s"${uri.toASCIIString}  $size  $path\n")
        })
        .tried

    def removeFromTagManifests(bagDir: Path, filename: String): Try[Unit]

    def removeFetchTxtFromTagManifests(bagDir: Path): Try[Unit]

    def getPayloadFilePaths(bagDir: Path): Try[Set[Path]]

  }
}

trait Bagit4FacadeComponent extends BagFacadeComponent {
  class Bagit4Facade(bagFactory: BagFactory = new BagFactory) extends BagFacade with DebugEnhancedLogging {

    private val algorithmMap = Map[Algorithm, gov.loc.repository.bagit.Manifest.Algorithm](
      MD5 -> gov.loc.repository.bagit.Manifest.Algorithm.MD5,
      SHA1 -> gov.loc.repository.bagit.Manifest.Algorithm.SHA1,
      SHA256 -> gov.loc.repository.bagit.Manifest.Algorithm.SHA256,
      SHA512 -> gov.loc.repository.bagit.Manifest.Algorithm.SHA512)
    private val algorithmReverseMap = algorithmMap.map(_.swap)

    override def isValid(bagDir: Path): Try[Boolean] = {
      for {
        bag <- getBag(bagDir)
        result <- Try { bag.verifyValid() }
        valid <- Try { result.isSuccess }
      } yield valid
    }

    override def hasValidTagManifests(bagDir: Path): Try[Boolean] = {
      for {
        bag <- getBag(bagDir)
        result <- Try { bag.verifyTagManifests() }
        valid <- Try { result.isSuccess }
      } yield valid
    }


    override def getPayloadManifest(bagDir: Path, algorithm: Algorithm): Try[Map[Path, String]] =
      getBag(bagDir)
        .map(_.getPayloadManifest(algorithmMap(algorithm))
          .asScala
          .map { case (path, c) => (Paths.get(path), c) }
          .toMap)

    override def getFetchItems(bagDir: Path): Try[Seq[FetchItem]] =
      getFetchTxt(bagDir).map(
        _.map(
          _.asScala
            .map(fi => FetchItem(new URI(fi.getUrl), fi.getSize, Paths.get(fi.getFilename)))
            .toSeq)
          .getOrElse(Seq.empty))

    private def getFetchTxt(bagDir: Path): Try[Option[FetchTxt]] = getBag(bagDir).map(bag => Option(bag.getFetchTxt))

    override def getSupportedManifestAlgorithms(bagDir: Path): Try[Set[Algorithm]] =
      getBag(bagDir)
        .map(_.getPayloadManifests
          .asScala
          .map(manifest => algorithmReverseMap(manifest.getAlgorithm))
          .toSet)

    override def getWeakestCommonAlgorithm(algorithmSets: Set[Set[Algorithm]]): Option[Algorithm] = {
      val commonAlgorithms = algorithmSets.reduce((n, a) => a.intersect(n))
      val weakToStrong = algorithmMap.keys.toSeq.sortBy(_.strength)
      weakToStrong.find(commonAlgorithms.contains)
    }

    private def getBag(dir: Path): Try[Bag] = Try {
      bagFactory.createBag(dir.toFile, BagFactory.Version.V0_97, BagFactory.LoadOption.BY_MANIFESTS)
    }.recoverWith { case cause => Failure(BagNotFoundException(dir, cause)) }

    override def removeFetchTxtFromTagManifests(bagDir: Path): Try[Unit] = {
      removeFromTagManifests(bagDir, FETCH_TXT_FILENAME)
    }

    override def removeFromTagManifests(bagDir: Path, filename: String): Try[Unit] = {
      getBag(bagDir)
        .map(_.getTagManifests.asScala.foreach(manifest => {
          manifest.remove(filename)
          FileSystemHelper.write(manifest, bagDir.resolve(manifest.getFilepath).toFile)
        }))
    }

    /**
     * Returns the set of payload file paths for a virtually-valid bag. If the bag is not
     * virtually-valid the results will be unreliable.
     *
     * @param bagDir the virtually bag
     * @return the set of payload file paths
     */
    override def getPayloadFilePaths(bagDir: Path): Try[Set[Path]] =  {
      getBag(bagDir)
        .map(_.getPayloadManifests
          .asScala
          .map(_.keySet() // a Manifest extends java.util.Map<String, String>
            .asScala // converts to mutable set
            .map(Paths.get(_))
            .toSet) // make set immutable
          .reduce(_ ++ _))
    }
  }
}
