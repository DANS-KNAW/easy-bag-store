package nl.knaw.dans.easy.bagstore.component

import java.io.FileInputStream
import java.net.URI
import java.nio.file.Paths
import java.util.UUID

import nl.knaw.dans.easy.bagstore._
import org.apache.commons.io.FileUtils

import scala.io.Source
import scala.util.{ Failure, Success }

class BagProcessingSpec extends TestSupportFixture
  with BagStoreFixture
  with Bagit4Fixture
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent { test =>

  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-pruned").toFile,
    testDir.resolve("basic-sequence-pruned").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-unpruned").toFile,
    testDir.resolve("basic-sequence-unpruned").toFile)

  private val TEST_BAG_PRUNED_A = testDir.resolve("basic-sequence-pruned/a")
  private val TEST_BAG_PRUNED_B = testDir.resolve("basic-sequence-pruned/b")
  private val TEST_BAG_PRUNED_C = testDir.resolve("basic-sequence-pruned/c")
  private val TEST_BAG_UNPRUNED_A = testDir.resolve("basic-sequence-unpruned/a")
  private val TEST_BAG_UNPRUNED_B = testDir.resolve("basic-sequence-unpruned/b")
  private val TEST_BAG_UNPRUNED_C = testDir.resolve("basic-sequence-unpruned/c")

  private val fs = new FileSystem {
    override val baseDir: BagPath = store1
    override val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
    override val bagPermissions: String = "rwxr-xr-x"
    override val localBaseUri: URI = new URI("http://example-archive.org")
  }

  private val processor = new BagProcessing {
    override val fileSystem: FileSystem = fs
    override val stagingBaseDir: BagPath = testDir
    override val outputBagPermissions: String = "rwxr-xr-x"
  }

  private val bagStore = new BagStore {
    override val fileSystem: FileSystem = fs
    override val processor: BagProcessing = test.processor
  }

  private val stagingBaseDir = processor.stagingBaseDir
  private val localBaseUri: URI = fs.localBaseUri

  "complete" should "make pruned Bag whole again" in {
    bagStore.add(TEST_BAG_PRUNED_A, Some(UUID.fromString("00000000-0000-0000-0000-000000000001"))) shouldBe a[Success[_]]
    bagStore.add(TEST_BAG_PRUNED_B, Some(UUID.fromString("00000000-0000-0000-0000-000000000002"))).recoverWith { case e => e.printStackTrace(); Failure(e) } shouldBe a[Success[_]]

    val testDirBagC = testDir.resolve("c")
    FileUtils.copyDirectory(TEST_BAG_PRUNED_C.toFile, testDirBagC.toFile)

    processor.complete(testDirBagC) shouldBe a[Success[_]]

    pathsEqual(TEST_BAG_UNPRUNED_C, testDirBagC) shouldBe true
  }

  "unzipBag" should "unzip zipped file and return the staging directory containing as a child the bag base directory" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)

    inside(processor.unzipBag(new FileInputStream("src/test/resources/zips/one-basedir.zip"))) {
      case Success(staging) => staging.getParent shouldBe stagingBaseDir
    }
  }

  "findBagDir" should "result in a failure if there are two base directories in the zip file" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)

    val staging = processor.unzipBag(new FileInputStream("src/test/resources/zips/two-basedirs.zip")).get
    processor.findBagDir(staging) should matchPattern { case Failure(IncorrectNumberOfFilesInBagZipRootException(2)) => }
  }

  it should "result in a failure if there are no files in the zip file" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)

    processor.unzipBag(new FileInputStream("src/test/resources/zips/empty.zip")) shouldBe a[Failure[_]]
    // Actually, stageBagZip should not end in Failure, but it does because lingala chokes on the empty zip
    // This is the next best thing.
  }

  it should "result in a failure if there is no base directory in the zip file" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)

    val staging = processor.unzipBag(new FileInputStream("src/test/resources/zips/one-file.zip")).get
    processor.findBagDir(staging) should matchPattern { case Failure(BagBaseNotFoundException()) => }
  }

  "prune" should "change files present in ref-bags to fetch.txt entries" in {
    val tryA = bagStore.add(TEST_BAG_UNPRUNED_A)
    tryA shouldBe a[Success[_]]
    processor.prune(TEST_BAG_UNPRUNED_B, tryA.get :: Nil) shouldBe a[Success[_]]

    /*
     * Now follow checks on the content of of the ingested bags. Each file should be EITHER actually present in the
     * data-directory OR a reference the fetch.txt (never both). The comments are taken from
     * src/test/resources/bags/basic-sequence-unpruned/README.txt
     */

    // Note about the regular expressions: the beginning and end of line symbols don't seem to work, so using work-around with \n? here.

    /*
     * Check bag B
     */
    val bFetchTxt = Source.fromFile(TEST_BAG_UNPRUNED_B.resolve("fetch.txt").toFile).mkString
    val uuidForA = tryA.get.uuid

    // data/sub/u      unchanged               => reference in fetch.txt
    bFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/sub/u\\s+\\d+\\s+data/sub/u\n"""
    TEST_BAG_UNPRUNED_B.resolve("data/sub/u").toFile shouldNot exist

    // [data/sub/v]    moved                   => not present here, no reference in fetch.txt
    bFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/sub/v\n""")
    TEST_BAG_UNPRUNED_B.resolve("data/sub/v").toFile shouldNot exist

    // [data/sub/w]    deleted                 => not present here, no reference in fetch.txt
    bFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/sub/w\n""")
    TEST_BAG_UNPRUNED_B.resolve("data/sub/w").toFile shouldNot exist

    // data/v          moved                   => reference in fetch.txt
    bFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/sub/v\\s+\\d+\\s+data/v\n"""
    TEST_BAG_UNPRUNED_B.resolve("data/v").toFile shouldNot exist
    TEST_BAG_UNPRUNED_B.resolve("data/sub/v").toFile shouldNot exist

    // data/x          unchanged               => reference in fetch.txt
    bFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/x\\s+\\d+\\s+data/x\n"""
    TEST_BAG_UNPRUNED_B.resolve("data/x").toFile shouldNot exist

    // data/y          changed                 => actual file
    bFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/y\n""")
    TEST_BAG_UNPRUNED_B.resolve("data/y").toFile should exist
    io.Source.fromFile(TEST_BAG_UNPRUNED_B.resolve("data/y").toFile).mkString should include("content of y edited in b")

    // data/y-old      copy of y               => reference in fetch.txt
    bFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/y\\s+\\d+\\s+data/y-old\n"""
    TEST_BAG_UNPRUNED_B.resolve("data/y-old").toFile shouldNot exist

    // [data/z]        deleted                 => not present, no reference in fetch.txt
    bFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/z\n""")
    TEST_BAG_UNPRUNED_B.resolve("data/z").toFile shouldNot exist

    /*
     * Adding the now pruned Bag B so that C may reference it
     */
    val tryB = bagStore.add(TEST_BAG_UNPRUNED_B)
    tryB shouldBe a[Success[_]]

    processor.prune(TEST_BAG_UNPRUNED_C, tryA.get :: tryB.get :: Nil)

    /*
     * Check bag C
     */
    val cFetchTxt = io.Source.fromFile(TEST_BAG_UNPRUNED_C.resolve("fetch.txt").toFile).mkString
    val uuidForB = tryB.get.uuid

    // data/sub/q      new file                => actual file
    cFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/sub/q\n""")
    TEST_BAG_UNPRUNED_C.resolve("data/sub/q").toFile should exist

    // data/sub/w      restored file from a
    cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/sub/w\\s+\\d+\\s+data/sub/w\n"""
    TEST_BAG_UNPRUNED_C.resolve("data/sub/w").toFile shouldNot exist

    // data/sub-copy/u the same as in b        => reference in fetch.txt to a (copied from b's fetch.txt)
    cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/sub/u\\s+\\d+\\s+data/sub-copy/u\n"""
    TEST_BAG_UNPRUNED_C.resolve("data/sub-copy/u").toFile shouldNot exist

    // data/p          new file                => actual file
    cFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/p\n""")
    TEST_BAG_UNPRUNED_C.resolve("data/p").toFile should exist

    // data/x          unchanged               => reference in fetch.txt to a
    cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/x\\s+\\d+\\s+data/x\n"""
    TEST_BAG_UNPRUNED_C.resolve("data/x").toFile shouldNot exist

    // data/y          unchanged               => reference in fetch.txt to b
    cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForB/data/y\\s+\\d+\\s+data/y\n"""
    TEST_BAG_UNPRUNED_C.resolve("data/y").toFile shouldNot exist

    // data/y-old      unchanged               => reference in fetch.txt to a
    cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/y\\s+\\d+\\s+data/y-old\n"""
    TEST_BAG_UNPRUNED_C.resolve("data/y-old").toFile shouldNot exist

    // [data/v]        deleted                 => not present here, no reference in fetch.txt
    cFetchTxt should not include regex(s"""\n?$localBaseUri.*\\s+\\d+\\s+data/v\n""")
    TEST_BAG_UNPRUNED_C.resolve("data/v").toFile shouldNot exist

    // data/z          unchanged               => reference in fetch.txt to a
    cFetchTxt should include regex s"""\n?$localBaseUri/$uuidForA/data/z\\s+\\d+\\s+data/z\n"""
    TEST_BAG_UNPRUNED_C.resolve("data/z").toFile shouldNot exist
  }
}
