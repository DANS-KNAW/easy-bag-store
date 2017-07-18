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
package nl.knaw.dans.easy.bagstore

import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.{ CountDownLatch, ExecutorService }
import java.util.{ ArrayList => JArrayList, Set => JSet }

import gov.loc.repository.bagit.domain.{ Bag, Manifest => BagitManifest }
import gov.loc.repository.bagit.exceptions._
import gov.loc.repository.bagit.hash.{ StandardSupportedAlgorithms, SupportedAlgorithm }
import gov.loc.repository.bagit.reader.BagReader
import gov.loc.repository.bagit.verify.{ BagVerifier, CheckManifestHashesTask }
import gov.loc.repository.bagit.writer.ManifestWriter
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import resource.Using

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

sealed abstract class Algorithm(val strength: Int)
case object MD5 extends Algorithm(1)
case object SHA1 extends Algorithm(2)
case object SHA256 extends Algorithm(3)
case object SHA512 extends Algorithm(4)

case class FetchItem(uri: URI, size: Long, path: Path)

case class BagNotFoundException(bagDir: Path, cause: Throwable) extends Exception(s"A bag could not be loaded at $bagDir", cause)

trait BagFacadeComponent {

  val bagFacade: BagFacade

  trait BagFacade {
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
          case FetchItem(uri, size, path) => writer.write(s"${ uri.toASCIIString }  $size  $path\n")
        })
        .tried

    def removeFromTagManifests(bagDir: Path, filename: String): Try[Unit]

    def removeFetchTxtFromTagManifests(bagDir: Path): Try[Unit]

    def getPayloadFilePaths(bagDir: Path): Try[Set[Path]]
  }
}

trait Bagit5FacadeComponent extends BagFacadeComponent with DebugEnhancedLogging {
  class Bagit5Facade(bagReader: BagReader = new BagReader) extends BagFacade {

    private val algorithmMap = Map[Algorithm, SupportedAlgorithm](
      MD5 -> StandardSupportedAlgorithms.MD5,
      SHA1 -> StandardSupportedAlgorithms.SHA1,
      SHA256 -> StandardSupportedAlgorithms.SHA256,
      SHA512 -> StandardSupportedAlgorithms.SHA512)
    private val algorithmReverseMap = algorithmMap.map(_.swap)

    override def isValid(bagDir: Path): Try[Boolean] = {
      for {
        bag <- getBag(bagDir)
        verifier = new BagVerifier
        valid = Try { verifier.isValid(bag, false) }.map(_ => true).getOrElse(false)
      } yield valid
    }

    override def hasValidTagManifests(bagDir: Path): Try[Boolean] = {
      val verifier = new BagVerifier

      def runTasks(tagManifest: BagitManifest)(executor: ExecutorService): Try[Boolean] = {
        val values = tagManifest.getFileToChecksumMap
        val exc = new JArrayList[Exception]()
        val latch = new CountDownLatch(values.size())

        for (entry <- values.entrySet().asScala) {
          executor.execute(new CheckManifestHashesTask(entry, tagManifest.getAlgorithm.getMessageDigestName, latch, exc))
        }

        latch.await()

        exc.asScala.toList match {
          case Nil => Success(true)
          case (_: CorruptChecksumException) :: _ => Success(false)
          case e :: _ => Failure(new VerificationException(e))
        }
      }

      getBag(bagDir)
        .map(_.getTagManifests.asScala.toStream
          .map(runTasks(_)(verifier.getExecutor).unsafeGetOrThrow)
          .forall(true ==))
    }

    override def getPayloadManifest(bagDir: Path, algorithm: Algorithm): Try[Map[Path, String]] = {
      getBag(bagDir)
        .flatMap(_.getPayLoadManifests
          .asScala
          .find(_.getAlgorithm == algorithmMap(algorithm))
          .map(manifest => Success(manifest.getFileToChecksumMap.asScala.toMap))
          .getOrElse(Failure(NoSuchPayloadManifestException(bagDir, algorithm)))
        )
    }

    override def getFetchItems(bagDir: Path): Try[Seq[FetchItem]] = {
      getBag(bagDir)
        .map(_.getItemsToFetch
          .asScala
          .map(item => FetchItem(item.url.toURI, item.length, item.path)))
    }

    override def getSupportedManifestAlgorithms(bagDir: Path): Try[Set[Algorithm]] = {
      getBag(bagDir)
        .map(_.getPayLoadManifests
          .asScala
          .map(manifest => algorithmReverseMap(manifest.getAlgorithm))
          .toSet)
    }

    override def getWeakestCommonAlgorithm(algorithmSets: Set[Set[Algorithm]]): Option[Algorithm] = {
      val commonAlgorithms = algorithmSets.reduce((n, a) => a.intersect(n))
      val weakToStrong = algorithmMap.keys.toSeq.sortBy(_.strength)
      weakToStrong.find(commonAlgorithms.contains)
    }

    private def getBag(bagDir: Path): Try[Bag] = Try {
      bagReader.read(bagDir)
    }.recoverWith {
      case cause: IOException => Failure(BagReaderException(bagDir, cause))
      case cause: UnparsableVersionException => Failure(BagReaderException(bagDir, cause))
      case cause: MaliciousPathException => Failure(BagReaderException(bagDir, cause))
      case cause: InvalidBagMetadataException => Failure(BagReaderException(bagDir, cause))
      case cause: UnsupportedAlgorithmException => Failure(BagReaderException(bagDir, cause))
      case cause: InvalidBagitFileFormatException => Failure(BagReaderException(bagDir, cause))
      case NonFatal(cause) => Failure(BagReaderException(bagDir, cause))
    }

    override def removeFetchTxtFromTagManifests(bagDir: Path): Try[Unit] = {
      removeFromTagManifests(bagDir, FETCH_TXT_FILENAME)
    }

    override def removeFromTagManifests(bagDir: Path, filename: String): Try[Unit] = {
      getBag(bagDir)
        .map(bag => {
          val tagManifests: JSet[BagitManifest] = bag.getTagManifests
          tagManifests.asScala.foreach(_.getFileToChecksumMap.remove(bagDir.resolve(filename)))
          ManifestWriter.writeTagManifests(tagManifests, bagDir, bagDir, bag.getFileEncoding)
        })
    }

    /**
     * Returns the set of payload file paths for a virtually-valid bag. If the bag is not
     * virtually-valid the results will be unreliable.
     *
     * @param bagDir the virtually bag
     * @return the set of payload file paths
     */
    override def getPayloadFilePaths(bagDir: Path): Try[Set[Path]] = {
      getBag(bagDir)
        .map(_.getPayLoadManifests
          .asScala
          .map(_.getFileToChecksumMap.keySet().asScala.toSet)
          .reduce(_ ++ _))
    }
  }
}
