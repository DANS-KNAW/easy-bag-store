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

import java.io.FileInputStream
import java.net.URI
import java.nio.file.Paths
import java.util.UUID

import better.files.File
import nl.knaw.dans.easy.bagstore._
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfter

import scala.io.Source
import scala.util.{ Failure, Success }

class BagProcessingSpec extends BagProcessingFixture
with BeforeAndAfter {

  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-pruned").toFile,
    testDir.resolve("basic-sequence-pruned").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-unpruned").toFile,
    testDir.resolve("basic-sequence-unpruned").toFile)

  private val testBagPrunedA = testDir.resolve("basic-sequence-pruned/a")
  private val testBagPrunedB = testDir.resolve("basic-sequence-pruned/b")
  private val testBagPrunedC = testDir.resolve("basic-sequence-pruned/c")
  private val testBagUnprunedA = testDir.resolve("basic-sequence-unpruned/a")
  private val testBagUnprunedB = testDir.resolve("basic-sequence-unpruned/b")
  private val testBagUnprunedC = testDir.resolve("basic-sequence-unpruned/c")

  val bagStore: BagStore = new BagStore {
    implicit val baseDir: BaseDir = store1
  }

  private val stagingBaseDir = bagProcessing.stagingBaseDir
  private val localBaseUri: URI = fileSystem.localBaseUri
  implicit val baseDir: BaseDir = bagStore.baseDir

  override def beforeEach(): Unit =  {
    val refBagDir = File(testDir.toString) / "refbag"
    refBagDir.createDirectories()
    refBagDir.clear()
  }

  "complete" should "make pruned Bag whole again" in {
    bagStore.add(testBagPrunedA, Some(UUID.fromString("00000000-0000-0000-0000-000000000001"))) shouldBe a[Success[_]]
    bagStore.add(testBagPrunedB, Some(UUID.fromString("00000000-0000-0000-0000-000000000002"))) shouldBe a[Success[_]]

    val testDirBagC = testDir.resolve("c")
    FileUtils.copyDirectory(testBagPrunedC.toFile, testDirBagC.toFile)

    bagProcessing.complete(testDirBagC) shouldBe a[Success[_]]

    pathsEqual(testBagUnprunedC, testDirBagC, "tagmanifest-md5.txt") shouldBe true
    // BagProcessing.complete causes the order in c/tagmanifest-md5.txt to change...
    Source.fromFile(testBagUnprunedC.resolve("tagmanifest-md5.txt").toFile).getLines().toList should
      contain theSameElementsAs Source.fromFile(testDirBagC.resolve("tagmanifest-md5.txt").toFile).getLines().toList
  }

  "unzipBag" should "unzip zipped file and return the staging directory containing as a child the bag base directory" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)

    inside(bagProcessing.unzipBag(new FileInputStream("src/test/resources/zips/one-basedir.zip"))) {
      case Success(staging) => staging.getParent shouldBe stagingBaseDir
    }
  }

  "findBagDir" should "result in a failure if there are two base directories in the zip file" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)

    inside(bagProcessing.unzipBag(new FileInputStream("src/test/resources/zips/two-basedirs.zip"))) { case Success(staging) =>
      bagProcessing.findBagDir(staging) should matchPattern { case Failure(IncorrectNumberOfFilesInBagZipRootException(2)) => }
    }
  }

  it should "result in a failure if there are no files in the zip file" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)

    bagProcessing.unzipBag(new FileInputStream("src/test/resources/zips/empty.zip")) shouldBe a[Failure[_]]
    // Actually, stageBagZip should not end in Failure, but it does because lingala chokes on the empty zip
    // This is the next best thing.
  }

  it should "result in a failure if there is no base directory in the zip file" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)

    inside(bagProcessing.unzipBag(new FileInputStream("src/test/resources/zips/one-file.zip"))) { case Success(staging) =>
      bagProcessing.findBagDir(staging) should matchPattern { case Failure(BagBaseNotFoundException()) => }
    }
  }

  "prune" should "change files present in ref-bags to fetch.txt entries" in {
    inside(bagStore.add(testBagUnprunedA)) { case Success(resA) =>
      bagProcessing.prune(testBagUnprunedB, resA :: Nil) shouldBe a[Success[_]]


      /*
       * Now follow checks on the content of of the ingested bags. Each file should be EITHER actually present in the
       * data-directory OR a reference the fetch.txt (never both). The comments are taken from
       * src/test/resources/bags/basic-sequence-unpruned/README.txt
       */

      // Note about the regular expressions: the beginning and end of line symbols don't seem to work, so using work-around with \n? here.

      /*
       * Check bag B
       */
      val bFetchTxt = Source.fromFile(testBagUnprunedB.resolve("fetch.txt").toFile).mkString
      val uuidForA = resA.uuid

      // data/sub/u      unchanged               => reference in fetch.txt
      bFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/sub/u\\s+\\d+\\s+data/sub/u\n"""
      testBagUnprunedB.resolve("data/sub/u").toFile shouldNot exist

      // [data/sub/v]    moved                   => not present here, no reference in fetch.txt
      bFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/sub/v\n""")
      testBagUnprunedB.resolve("data/sub/v").toFile shouldNot exist

      // [data/sub/w]    deleted                 => not present here, no reference in fetch.txt
      bFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/sub/w\n""")
      testBagUnprunedB.resolve("data/sub/w").toFile shouldNot exist

      // data/v          moved                   => reference in fetch.txt
      bFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/sub/v\\s+\\d+\\s+data/v\n"""
      testBagUnprunedB.resolve("data/v").toFile shouldNot exist
      testBagUnprunedB.resolve("data/sub/v").toFile shouldNot exist

      // data/x          unchanged               => reference in fetch.txt
      bFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/x\\s+\\d+\\s+data/x\n"""
      testBagUnprunedB.resolve("data/x").toFile shouldNot exist

      // data/y          changed                 => actual file
      bFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/y\n""")
      testBagUnprunedB.resolve("data/y").toFile should exist
      io.Source.fromFile(testBagUnprunedB.resolve("data/y").toFile).mkString should include("content of y edited in b")

      // data/y-old      copy of y               => reference in fetch.txt
      bFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/y\\s+\\d+\\s+data/y-old\n"""
      testBagUnprunedB.resolve("data/y-old").toFile shouldNot exist

      // [data/z]        deleted                 => not present, no reference in fetch.txt
      bFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/z\n""")
      testBagUnprunedB.resolve("data/z").toFile shouldNot exist

      /*
       * Adding the now pruned Bag B so that C may reference it
       */
      inside(bagStore.add(testBagUnprunedB)) { case Success(resB) =>
        bagProcessing.prune(testBagUnprunedC, resA :: resB :: Nil)

        /*
         * Check bag C
         */
        val cFetchTxt = io.Source.fromFile(testBagUnprunedC.resolve("fetch.txt").toFile).mkString
        val uuidForB = resB.uuid

        // data/sub/q      new file                => actual file
        cFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/sub/q\n""")
        testBagUnprunedC.resolve("data/sub/q").toFile should exist

        // data/sub/w      restored file from a
        cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/sub/w\\s+\\d+\\s+data/sub/w\n"""
        testBagUnprunedC.resolve("data/sub/w").toFile shouldNot exist

        // data/sub-copy/u the same as in b        => reference in fetch.txt to a (copied from b's fetch.txt)
        cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/sub/u\\s+\\d+\\s+data/sub-copy/u\n"""
        testBagUnprunedC.resolve("data/sub-copy/u").toFile shouldNot exist

        // data/p          new file                => actual file
        cFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/p\n""")
        testBagUnprunedC.resolve("data/p").toFile should exist

        // data/x          unchanged               => reference in fetch.txt to a
        cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/x\\s+\\d+\\s+data/x\n"""
        testBagUnprunedC.resolve("data/x").toFile shouldNot exist

        // data/y          unchanged               => reference in fetch.txt to b
        cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForB/data/y\\s+\\d+\\s+data/y\n"""
        testBagUnprunedC.resolve("data/y").toFile shouldNot exist

        // data/y-old      unchanged               => reference in fetch.txt to a
        cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/y\\s+\\d+\\s+data/y-old\n"""
        testBagUnprunedC.resolve("data/y-old").toFile shouldNot exist

        // [data/v]        deleted                 => not present here, no reference in fetch.txt
        cFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/v\n""")
        testBagUnprunedC.resolve("data/v").toFile shouldNot exist

        // data/z          unchanged               => reference in fetch.txt to a
        cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/z\\s+\\d+\\s+data/z\n"""
        testBagUnprunedC.resolve("data/z").toFile shouldNot exist
      }
    }
  }

  "getReferenceBags" should "fail when an empty refbag.txt is found in the bag" in {
    // first create dir with empty refbag.txt
    val bagDir = createFilesForRefbagTests("")

    val bagId = BagId(UUID.randomUUID())
    bagProcessing.getReferenceBags(bagDir.path, bagId) should matchPattern {
      case Failure(InvalidBagException(`bagId`, "the bag contains an empty refbags.txt")) =>
    }
  }

  it should "succeed if no refbag.txt is found in the bag" in {
    val bagDir = File(testDir.toString) / "refbag"
    val bagId = BagId(UUID.randomUUID())
    bagProcessing.getReferenceBags(bagDir.path, bagId) shouldBe a[Success[_]]
  }

  it should "fail when an refbag.txt with only whitespaces is found in the bag" in {
    val bagDir = createFilesForRefbagTests("                           ")
    val bagId = BagId(UUID.randomUUID())
    bagProcessing.getReferenceBags(bagDir.path, bagId) should matchPattern {
      case Failure(InvalidBagException(`bagId`, "the bag contains an empty refbags.txt")) =>
    }
  }

  it should "succeed a non-empty refbag.txt is found in the bag" in {
    val bagDir = createFilesForRefbagTests("refbag content")
    val bagId = BagId(UUID.randomUUID())
    bagProcessing.getReferenceBags(bagDir.path, bagId) shouldBe a[Success[_]]
  }

  private def createFilesForRefbagTests(content: String): File = {
    val bagDir = File(testDir.toString) / "refbag"
    (bagDir / "refbags.txt")
      .write(content)

    (bagDir / "bagit.txt")
      .write(
        """BagIt-Version: 0.97
          |Tag-File-Character-Encoding: UTF-8""".stripMargin)
    bagDir
  }
}
