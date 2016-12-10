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

import gov.loc.repository.bagit.{Bag, BagFactory, FetchTxt}
import resource.Using

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

sealed abstract class Algorithm(val strength: Int)
case object MD5 extends Algorithm(1)
case object SHA1 extends Algorithm(2)
case object SHA256 extends Algorithm(3)
case object SHA512 extends Algorithm(4)

case class FetchItem(uri: URI, size: Long, path: Path)

case class InvalidAfterFetchException(msg: String) extends Exception(msg)
case class BagNotFoundException(bagDir: Path, cause: Throwable) extends Exception(s"A bag could not be loaded at $bagDir", cause)

trait BagFacade {
  def isValid(bagDir: Path): Try[Boolean]

  def getPayloadManifest(bagDir: Path, algorithm: Algorithm): Try[Map[Path, String]]

  def getFetchItems(bagDir: Path): Try[Map[Path, FetchItem]]

  def getSupportedManifestAlgorithms(bagDir: Path): Try[Set[Algorithm]]

  def getWeakestCommonAlgorithm(algorithmSets: Set[Set[Algorithm]]): Option[Algorithm]

  def writeFetchFile(bagDir: Path, fetchList: List[FetchItem]): Try[Unit] =
    Using.fileWriter()(bagDir.resolve("fetch.txt").toFile)
      .map(writer => fetchList.foreach {
        case FetchItem(uri, size, path) => writer.write(s"${uri.toASCIIString}  $size  $path\n")
      })
      .tried

  def resolveFetchItems(bagDir: Path, resolveUri: URI => URI): Try[Unit]
}

class Bagit4Facade(bagFactory: BagFactory = new BagFactory) extends BagFacade {

  val algorithmMap = Map[Algorithm, gov.loc.repository.bagit.Manifest.Algorithm](
    MD5 -> gov.loc.repository.bagit.Manifest.Algorithm.MD5,
    SHA1 -> gov.loc.repository.bagit.Manifest.Algorithm.SHA1,
    SHA256 -> gov.loc.repository.bagit.Manifest.Algorithm.SHA256,
    SHA512 -> gov.loc.repository.bagit.Manifest.Algorithm.SHA512)
  val algorithmReverseMap = algorithmMap.map(_.swap)

  def isValid(bagDir: Path): Try[Boolean] =
    for {
      bag <- getBagFromDir(bagDir)
      result <- Try { bag.verifyValid() }
      valid <- Try { result.isSuccess }
    } yield valid

  def getPayloadManifest(bagDir: Path, algorithm: Algorithm): Try[Map[Path, String]] =
    getBagFromDir(bagDir)
      .map(_.getPayloadManifest(algorithmMap(algorithm))
        .asScala
        .map { case (path, c) => (Paths.get(path), c) }
        .toMap)

  def getFetchItems(bagDir: Path): Try[Map[Path, FetchItem]] =
    getFetchTxt(bagDir).map(
      _.map(
        _.asScala
          .map(fi => FetchItem(new URI(fi.getUrl), fi.getSize, Paths.get(fi.getFilename)))
          .map(fi => fi.path -> fi)
          .toMap)
        .getOrElse(Map.empty))

  def getSupportedManifestAlgorithms(bagDir: Path): Try[Set[Algorithm]] =
    getBagFromDir(bagDir)
      .map(_.getPayloadManifests
        .asScala
        .map(manifest => algorithmReverseMap(manifest.getAlgorithm))
        .toSet)

  def getWeakestCommonAlgorithm(algorithmSets: Set[Set[Algorithm]]): Option[Algorithm] = {
    val commonAlgorithms = algorithmSets.reduce((n, a) => a.intersect(n))
    val weakToStrong = algorithmMap.keys.toSeq.sortBy(_.strength)
    weakToStrong.find(commonAlgorithms.contains)
  }

  def getBagFromDir(dir: Path): Try[Bag] = Try {
    bagFactory.createBag(dir.toFile, BagFactory.Version.V0_97, BagFactory.LoadOption.BY_MANIFESTS)
  }.recoverWith { case cause => Failure(BagNotFoundException(dir, cause)) }

  def resolveFetchItems(bagDir: Path, resolveUri: URI => URI): Try[Unit] =
    getFetchTxt(bagDir)
      .map(_.foreach(_.asScala.foreach(item =>
        Using.urlInputStream(resolveUri(new URI(item.getUrl)).toURL)
          .foreach(src => Files.copy(src, bagDir.toAbsolutePath.resolve(item.getFilename))))))

  def deleteFetchItems(bagDir: Path): Try[Unit] =
    getFetchTxt(bagDir)
      .map(_.foreach(_.asScala.foreach(item => Files.delete(bagDir.toAbsolutePath.resolve(item.getFilename)))))

  def getFetchTxt(bagDir: Path): Try[Option[FetchTxt]] = getBagFromDir(bagDir).map(bag => Option(bag.getFetchTxt))
}
