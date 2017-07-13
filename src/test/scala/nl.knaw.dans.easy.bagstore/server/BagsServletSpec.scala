package nl.knaw.dans.easy.bagstore.server

import java.nio.file.{ Files, Paths }
import java.util.UUID

import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }
import nl.knaw.dans.easy.bagstore.{ BagStoresFixture, Bagit4Fixture, TestSupportFixture }
import org.apache.commons.io.FileUtils
import org.scalatra.test.scalatest.ScalatraSuite

class BagsServletSpec extends TestSupportFixture
  with Bagit4Fixture
  with BagStoresFixture
  with ScalatraSuite
  with BagsServletComponent
  with BagStoresComponent
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  override val bagsServlet: BagsServlet = new BagsServlet {}

  override def beforeAll(): Unit = {
    super.beforeAll()
    addServlet(bagsServlet, "/*")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/00").toFile, store1.toFile)
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/00").toFile, store2.toFile)
    Files.move(store2.resolve("00/000000000000000000000000000001"), store2.resolve("00/000000000000000000000000000004"))
    Files.move(store2.resolve("00/000000000000000000000000000002"), store2.resolve("00/000000000000000000000000000005"))
    Files.move(store2.resolve("00/000000000000000000000000000003"), store2.resolve("00/000000000000000000000000000006"))
  }

  private def setBag1Hidden(): Unit = {
    Files.move(store1.resolve("00/000000000000000000000000000001/bag-revision-1"), store1.resolve("00/000000000000000000000000000001/.bag-revision-1"))
  }

  private def makeBagstore1Invalid(): Unit = {
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/ff").toFile, store1.toFile)
  }

  "get" should "enumerate the bags in all bag-stores" in {
    get("/") {
      status shouldBe 200
      body.lines.toList should contain only(
        "00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003",
        "00000000-0000-0000-0000-000000000004",
        "00000000-0000-0000-0000-000000000005",
        "00000000-0000-0000-0000-000000000006"
      )
    }
  }

  it should "enumerate inactive bags only when this is set" in {
    setBag1Hidden()

    get("/", "state" -> "inactive") {
      status shouldBe 200
      body.lines.toList should contain only "00000000-0000-0000-0000-000000000001"
    }
  }

  it should "enumerate active bags only by default" in {
    setBag1Hidden()

    get("/") {
      status shouldBe 200
      body.lines.toList should contain only(
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003",
        "00000000-0000-0000-0000-000000000004",
        "00000000-0000-0000-0000-000000000005",
        "00000000-0000-0000-0000-000000000006"
      )
    }
  }

  it should "enumerate all bags when this is set" in {
    setBag1Hidden()

    get("/", "state" -> "all") {
      status shouldBe 200
      body.lines.toList should contain only(
        "00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003",
        "00000000-0000-0000-0000-000000000004",
        "00000000-0000-0000-0000-000000000005",
        "00000000-0000-0000-0000-000000000006"
      )
    }
  }

  it should "enumerate the bags in all bag-stores even if an unknown state is given" in {
    get("/", "state" -> "invalid value") {
      status shouldBe 200
      body.lines.toList should contain only(
        "00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003",
        "00000000-0000-0000-0000-000000000004",
        "00000000-0000-0000-0000-000000000005",
        "00000000-0000-0000-0000-000000000006"
      )
    }
  }

  it should "return an empty string when all bag-stores are empty" in {
    FileUtils.deleteDirectory(store1.resolve("00").toFile)
    FileUtils.deleteDirectory(store2.resolve("00").toFile)

    get("/") {
      status shouldBe 200
      body shouldBe empty
    }
  }

  it should "return a 500 error when an error occurs" in {
    makeBagstore1Invalid()

    get("/") {
      status shouldBe 500
      body should include("Unexpected type of failure. Please consult the logs")
    }
  }

  "get uuid" should "enumerate the files of a given bag" in {
    get("/00000000-0000-0000-0000-000000000001") {
      status shouldBe 200
      // TODO why aren't the metadata files included in here?
      body.lines.toList should contain only (
        "00000000-0000-0000-0000-000000000001/bagit.txt",
        "00000000-0000-0000-0000-000000000001/data/y",
        "00000000-0000-0000-0000-000000000001/manifest-sha1.txt",
        "00000000-0000-0000-0000-000000000001/data/x",
        "00000000-0000-0000-0000-000000000001/data/sub/w",
        "00000000-0000-0000-0000-000000000001/data/z",
        "00000000-0000-0000-0000-000000000001/data/sub/u",
        "00000000-0000-0000-0000-000000000001/tagmanifest-sha1.txt",
        "00000000-0000-0000-0000-000000000001/data/sub/v",
        "00000000-0000-0000-0000-000000000001/bag-info.txt"
      )
    }
  }

  it should "fail when the given uuid is not a uuid" in {
    get("/00000000000000000000000000000001") {
      status shouldBe 400
      body shouldBe "invalid UUID string: 00000000000000000000000000000001"
    }
  }

  it should "fail when the given uuid is not a well-formatted uuid" in {
    get("/abc-def-ghi-jkl-mno") {
      status shouldBe 400
      body shouldBe "invalid UUID string: abc-def-ghi-jkl-mno"
    }
  }

  it should "fail when the bag is not found" in {
    get(s"/${ UUID.randomUUID() }") {
      status shouldBe 404
    }
  }
}
