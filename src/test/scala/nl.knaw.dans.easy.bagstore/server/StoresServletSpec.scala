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
package nl.knaw.dans.easy.bagstore.server

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }
import java.util.{ Base64, UUID }

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }
import org.apache.commons.io.FileUtils
import org.scalatra.test.scalatest.ScalatraSuite

import scala.io.Source
import scala.util.{ Success, Try }

class StoresServletSpec extends TestSupportFixture
  with BagitFixture
  with BagStoresFixture
  with ServletFixture
  with ScalatraSuite
  with StoresServletComponent
  with BagStoresComponent
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  val username = "easy-bag-store"
  val password = "easy-bag-store"

  override val storesServlet: StoresServlet = new StoresServlet {
    override val externalBaseUri: URI = new URI("http://example-archive.org/")
    override val bagstoreUsername: String = username
    override val bagstorePassword: String = password
  }

  private val testBagsUnpruned = testDir.resolve("basic-sequence-unpruned-with-refbags")
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-unpruned-with-refbags").toFile,
    testBagsUnpruned.toFile)
  private val bagInput = Files.createDirectory(testDir.resolve("bag-input"))
  private val testBagUnprunedA = bagInput.resolve("unpruned-with-refbags-a.zip")
  private val testBagUnprunedB = bagInput.resolve("unpruned-with-refbags-b.zip")
  private val testBagUnprunedC = bagInput.resolve("unpruned-with-refbags-c.zip")
  private val testBagUnprunedInvalid = bagInput.resolve("unpruned-with-refbags-invalid.zip")

  new ZipFile(testBagUnprunedA.toFile) {
    addFolder(testBagsUnpruned.resolve("a").toFile, new ZipParameters)
  }
  new ZipFile(testBagUnprunedB.toFile) {
    addFolder(testBagsUnpruned.resolve("b").toFile, new ZipParameters)
  }
  new ZipFile(testBagUnprunedC.toFile) {
    addFolder(testBagsUnpruned.resolve("c").toFile, new ZipParameters)
  }
  new ZipFile(testBagUnprunedInvalid.toFile) {
    addFolder(testBagsUnpruned.resolve("invalid").toFile, new ZipParameters)
    addFile(testBagsUnpruned.resolve("invalid/deposit.properties").toFile, new ZipParameters)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    addServlet(storesServlet, "/*")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store1").toFile, store1.toFile)
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store2").toFile, store2.toFile)
    Files.move(store2.resolve("02/000000000000000000000000000001"), store2.resolve("02/000000000000000000000000000004"))
    Files.move(store2.resolve("02/000000000000000000000000000002"), store2.resolve("02/000000000000000000000000000005"))
    Files.move(store2.resolve("02/000000000000000000000000000003"), store2.resolve("02/000000000000000000000000000006"))
  }

  private def setBag1Hidden(): Unit = {
    Files.move(store1.resolve("01/000000000000000000000000000001/bag-revision-1"), store1.resolve("01/000000000000000000000000000001/.bag-revision-1"))
  }

  private def makeBagstore1Invalid(): Unit = {
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/ff").toFile, store1.toFile)
  }

  private def makeBagstore2Invalid(): Unit = {
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/ff").toFile, store2.toFile)
  }

  "get /:bagstore/bags" should "enumerate all bags in a specific store" in {
    get("/store1/bags") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "01000000-0000-0000-0000-000000000001",
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "enumerate inactive bags only when this is set" in {
    setBag1Hidden()
    get("/store1/bags", "state" -> "inactive") {
      status shouldBe 200
      body.lines.toList should contain only "01000000-0000-0000-0000-000000000001"
    }
  }

  it should "enumerate active bags only by default" in {
    setBag1Hidden()
    get("/store1/bags") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "enumerate all bags when this is set" in {
    setBag1Hidden()
    get("/store1/bags", "state" -> "all") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "01000000-0000-0000-0000-000000000001",
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "enumerate the bags in all bag-stores even if an unknown state is given" in {
    get("/store1/bags", "state" -> "invalid value") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "01000000-0000-0000-0000-000000000001",
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "return an empty string when all bag-stores are empty" in {
    FileUtils.deleteDirectory(store1.resolve("01").toFile)
    get("/store1/bags") {
      status shouldBe 200
      body shouldBe empty
    }
  }

  it should "return a 500 error when the store is corrupt" in {
    makeBagstore1Invalid()

    get("/store1/bags") {
      status shouldBe 500
      body should include("Unexpected type of failure. Please consult the logs")
    }
  }

  it should "function normally when another store is corrupt" in {
    makeBagstore2Invalid()

    get("/store1/bags") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "01000000-0000-0000-0000-000000000001",
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "fail when an unknown store is given" in {
    get("/unknown-store/bags") {
      status shouldBe 404
      body shouldBe "No such bag-store: unknown-store"
    }
  }

  "get /:bagstore/bags/:uuid" should "return an overview of the files in a given bag in a certain store" in {
    get("/store1/bags/01000000-0000-0000-0000-000000000001", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 200
      body.lines.toList should contain only(
        "01000000-0000-0000-0000-000000000001/data/x",
        "01000000-0000-0000-0000-000000000001/data/y",
        "01000000-0000-0000-0000-000000000001/data/z",
        "01000000-0000-0000-0000-000000000001/data/sub/u",
        "01000000-0000-0000-0000-000000000001/data/sub/v",
        "01000000-0000-0000-0000-000000000001/data/sub/w",
        "01000000-0000-0000-0000-000000000001/metadata/dataset.xml",
        "01000000-0000-0000-0000-000000000001/metadata/files.xml",
        "01000000-0000-0000-0000-000000000001/bagit.txt",
        "01000000-0000-0000-0000-000000000001/bag-info.txt",
        "01000000-0000-0000-0000-000000000001/manifest-sha1.txt",
        "01000000-0000-0000-0000-000000000001/tagmanifest-sha1.txt"
      )
    }
  }

  it should "fail when the store is unknown" in {
    get("/unknown-store/bags/01000000-0000-0000-0000-000000000001") {
      status shouldBe 404
      body shouldBe "No such bag-store: unknown-store"
    }
  }

  it should "fail when the given uuid is not a uuid" in {
    get("/store1/bags/00000000000000000000000000000001") {
      status shouldBe 400
      body shouldBe "Invalid UUID string: 00000000000000000000000000000001"
    }
  }

  it should "fail when the given uuid is not a well-formatted uuid" in {
    get("/store1/bags/abc-def-ghi-jkl-mno") {
      status shouldBe 400
      body shouldBe "Invalid UUID string: abc-def-ghi-jkl-mno"
    }
  }

  it should "fail when the bag is not found" in {
    val uuid = UUID.randomUUID()
    get(s"/store1/bags/$uuid") {
      status shouldBe 404
      body shouldBe s"Bag $uuid does not exist in BagStore"
    }
  }

  it should "return an overview when text/plain is specified in content negotiation" in {
    get("/store1/bags/01000000-0000-0000-0000-000000000001", params = Map.empty, headers = Map("Accept" -> "text/plain")) {
      status shouldBe 200
      body.lines.toList should contain only(
        "01000000-0000-0000-0000-000000000001/data/x",
        "01000000-0000-0000-0000-000000000001/data/y",
        "01000000-0000-0000-0000-000000000001/data/z",
        "01000000-0000-0000-0000-000000000001/data/sub/u",
        "01000000-0000-0000-0000-000000000001/data/sub/v",
        "01000000-0000-0000-0000-000000000001/data/sub/w",
        "01000000-0000-0000-0000-000000000001/metadata/dataset.xml",
        "01000000-0000-0000-0000-000000000001/metadata/files.xml",
        "01000000-0000-0000-0000-000000000001/bagit.txt",
        "01000000-0000-0000-0000-000000000001/bag-info.txt",
        "01000000-0000-0000-0000-000000000001/manifest-sha1.txt",
        "01000000-0000-0000-0000-000000000001/tagmanifest-sha1.txt"
      )
    }
  }

  it should "return the bag itself when application/zip is specified for content negotiation" in {
    get("/store1/bags/01000000-0000-0000-0000-000000000001", params = Map.empty, headers = Map("Accept" -> "application/zip")) {
      status shouldBe 200

      val zip = testDir.resolve("bag-output/01000000-0000-0000-0000-000000000001.zip")
      val unzipped = testDir.resolve("bag-output/01000000-0000-0000-0000-000000000001")
      Files.createDirectories(zip.getParent)
      Files.copy(response.inputStream, zip)
      zip.toFile should exist

      val unzip = Try {
        new ZipFile(zip.toFile) {
          setFileNameCharset(StandardCharsets.UTF_8.name)
        }.extractAll(unzipped.toAbsolutePath.toString)
      }
      unzipped.toFile should exist
      unzip shouldBe a[Success[_]] // It is actually a zip-file

      pathsEqual(unzipped, store1.resolve("01/000000000000000000000000000001"), "tagmanifest-sha1.txt") shouldBe true
      // BagProcessing.complete causes the order in tagmanifest-sha1.txt to change...
      Source.fromFile(unzipped.resolve("bag-revision-1/tagmanifest-sha1.txt").toFile).getLines().toList should
        contain theSameElementsAs Source.fromFile(store1.resolve("01/000000000000000000000000000001/bag-revision-1/tagmanifest-sha1.txt").toFile).getLines().toList
    }
  }

  "get /:bagstore/bags/:uuid/*" should "return a specific file in the bag indicated by the path" in {
    get("/store1/bags/01000000-0000-0000-0000-000000000001/data/y") {
      status shouldBe 200

      Source.fromInputStream(response.inputStream).mkString shouldBe
        Source.fromFile(store1.resolve("01/000000000000000000000000000001/bag-revision-1/data/y").toFile).mkString
    }
  }

  it should "return a metadata file in the bag indicated by the path" in {
    get("/store1/bags/01000000-0000-0000-0000-000000000001/metadata/files.xml") {
      status shouldBe 200

      Source.fromInputStream(response.inputStream).mkString shouldBe
        Source.fromFile(store1.resolve("01/000000000000000000000000000001/bag-revision-1/metadata/files.xml").toFile).mkString
    }
  }

  it should "fail when the store is unknown" in {
    get("/unknown-store/bags/00000000-0000-0000-0000-000000000001/data/y") {
      status shouldBe 404
      body shouldBe "No such bag-store: unknown-store"
    }
  }

  it should "fail when the given uuid is not a uuid" in {
    get("/store1/bags/00000000000000000000000000000001/data/y") {
      status shouldBe 400
      body shouldBe "Invalid UUID string: 00000000000000000000000000000001"
    }
  }

  it should "fail when the given uuid is not a well-formatted uuid" in {
    get("/store1/bags/abc-def-ghi-jkl-mno/data/y") {
      status shouldBe 400
      body shouldBe "Invalid UUID string: abc-def-ghi-jkl-mno"
    }
  }

  it should "fail when the bag is not found" in {
    val uuid = UUID.randomUUID()
    get(s"/store1/bags/$uuid/data/y") {
      status shouldBe 404
      body shouldBe s"Bag $uuid does not exist in BagStore"
    }
  }

  it should "fail when the file is not found" in {
    get("/store1/bags/01000000-0000-0000-0000-000000000001/unknown-folder/unknown-file") {
      status shouldBe 404
      body shouldBe s"Item 01000000-0000-0000-0000-000000000001/unknown-folder/unknown-file not found"
    }
  }

  def authenticationHeader(username: String, password: String, authType: String = "Basic"): List[(String, String)] = {
    val encoded = Base64.getEncoder.encodeToString(s"$username:$password")
    List("Authorization" -> s"$authType $encoded")
  }

  private val basicAuthentication = authenticationHeader(username, password)

  def putBag(uuid: String, bagZip: Path): Unit = {
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(bagZip), basicAuthentication) {
      status shouldBe 201
      header should contain("Location" -> s"http://example-archive.org/stores/store1/bags/$uuid")
    }
  }

  "put /:bagstore/bags/:uuid" should "store a bag in the given bag-store" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    putBag(uuid, testBagUnprunedA)
  }

  it should "fail, returning a bad request when a second put is done on the same uuid" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    putBag(uuid, testBagUnprunedA)
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(testBagUnprunedA), basicAuthentication) {
      status shouldBe 400
      body should include(s"$uuid already exists in BagStore store1 (bag-ids must be globally unique)")
    }
  } 
  
  it should "should fail and return a badrequest if there are multiple files in the root directory of the zipped bag" in {
    val uuid = "11111111-1111-1111-1111-111111111114"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(testBagUnprunedInvalid), basicAuthentication)  {
        status shouldBe 400
    }
  }

  it should "store and prune multiple revisions of a bagsequence" in {
    val uuid1 = "11111111-1111-1111-1111-111111111111"
    val uuid2 = "11111111-1111-1111-1111-111111111112"
    val uuid3 = "11111111-1111-1111-1111-111111111113"

    putBag(uuid1, testBagUnprunedA)
    putBag(uuid2, testBagUnprunedB)
    putBag(uuid3, testBagUnprunedC)

    val pruned = Paths.get("src/test/resources/bags/basic-sequence-pruned")

    pathsEqual(pruned.resolve("a"), store1.resolve("11/111111111111111111111111111111/a")) shouldBe true

    pathsEqual(pruned.resolve("b"), store1.resolve("11/111111111111111111111111111112/b"), "fetch.txt", "tagmanifest-md5.txt") shouldBe true
    // BagProcessing.complete causes the order in b/tagmanifest-md5.txt to change...
    Source.fromFile(pruned.resolve("b/tagmanifest-md5.txt").toFile).getLines().toList should
      contain theSameElementsAs Source.fromFile(store1.resolve("11/111111111111111111111111111112/b/tagmanifest-md5.txt").toFile).getLines().toList

    pathsEqual(pruned.resolve("c"), store1.resolve("11/111111111111111111111111111113/c"), "fetch.txt", "tagmanifest-md5.txt") shouldBe true
    // BagProcessing.complete causes the order in c/tagmanifest-md5.txt to change...
    Source.fromFile(pruned.resolve("c/tagmanifest-md5.txt").toFile).getLines().toList should
      contain theSameElementsAs Source.fromFile(store1.resolve("11/111111111111111111111111111113/c/tagmanifest-md5.txt").toFile).getLines().toList
  }

  it should "make an identity with get/:bagstore/bags/:uuid" in {
    def retrieveBag(uuid: String, bagName: String): Unit = {
      get(s"/store1/bags/$uuid", params = Map.empty, headers = Map("Accept" -> "application/zip")) {
        status shouldBe 200

        val zip = testDir.resolve(s"bag-output/$uuid.zip")
        val unzip = testDir.resolve(s"bag-output/$uuid")
        Files.createDirectories(zip.getParent)
        Files.copy(response.inputStream, zip)
        zip.toFile should exist

        new ZipFile(zip.toFile) {
          setFileNameCharset(StandardCharsets.UTF_8.name)
        }.extractAll(unzip.toAbsolutePath.toString)
        unzip.toFile should exist

        pathsEqual(unzip.resolve(bagName), testBagsUnpruned.resolve(bagName), "refbags.txt", "tagmanifest-md5.txt", "fetch.txt") shouldBe true
        // BagProcessing.complete causes the order in b/tagmanifest-md5.txt to change...
        Source.fromFile(unzip.resolve(s"$bagName/tagmanifest-md5.txt").toFile).getLines().toList should
          contain theSameElementsAs Source.fromFile(testBagsUnpruned.resolve(s"$bagName/tagmanifest-md5.txt").toFile).getLines().toList
      }
    }

    val uuid1 = "11111111-1111-1111-1111-111111111111"
    val uuid2 = "11111111-1111-1111-1111-111111111112"
    val uuid3 = "11111111-1111-1111-1111-111111111113"

    putBag(uuid1, testBagUnprunedA)
    putBag(uuid2, testBagUnprunedB)
    putBag(uuid3, testBagUnprunedC)

    retrieveBag(uuid1, "a")
    retrieveBag(uuid2, "b")
    retrieveBag(uuid3, "c")
  }

  it should "fail when no BasicAuth credentials are provided" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(testBagUnprunedA)) {
      status shouldBe 401
      body shouldBe "Unauthenticated"
      header("WWW-Authenticate") shouldBe """Basic realm="easy-bag-store""""
    }
  }

  it should "fail when another type of credentials is provided" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(testBagUnprunedA), authenticationHeader("foo", "bar", "Bearer")) {
      status shouldBe 400
      body shouldBe "Bad Request"
    }
  }

  it should "fail when invalid BasicAuth credentials are provided" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(testBagUnprunedA), authenticationHeader("wrong-username", "wrong-password")) {
      status shouldBe 401
      body shouldBe "Unauthenticated"
      header("WWW-Authenticate") shouldBe """Basic realm="easy-bag-store""""
    }
  }

  it should "fail when a second call does not provide credentials" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    putBag(uuid, testBagUnprunedA)
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(testBagUnprunedA)) {
      status shouldBe 401
      body shouldBe "Unauthenticated"
      header("WWW-Authenticate") shouldBe """Basic realm="easy-bag-store""""
    }
  }

  it should "fail when the store is unknown" in {
    put("/unknown-store/bags/11111111-1111-1111-1111-111111111111", body = Files.readAllBytes(testBagUnprunedA), basicAuthentication) {
      status shouldBe 404
      body shouldBe "No such bag-store: unknown-store"
    }
  }

  it should "fail when the given uuid is not a uuid" in {
    val uuid = "11111111111111111111111111111111"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(testBagUnprunedA), basicAuthentication) {
      status shouldBe 400
      body shouldBe s"invalid UUID string: $uuid"
    }
  }

  it should "fail when the given uuid is not a well-formatted uuid" in {
    val uuid = "abc-def-ghi-jkl-mno"
    put(s"/store1/bags/$uuid", headers = basicAuthentication) {
      status shouldBe 400
      body shouldBe s"invalid UUID string: $uuid"
    }
  }

  it should "fail when the input stream is empty" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    put(s"/store1/bags/$uuid", headers = basicAuthentication) {
      status shouldBe 400
      body shouldBe "The provided input did not contain a bag"
    }
  }

  it should "fail when the input stream contains anything else than a zip-file" in {
    val uuid = "66666666-6666-6666-6666-666666666666"
    put(s"/store1/bags/$uuid", body = "hello world".getBytes, basicAuthentication) {
      status shouldBe 400
      body shouldBe "The provided input did not contain a bag"
    }
  }

  it should "fail when the input stream contains a zip-file that doesn't represent a bag" in {
    val zip = Files.createDirectory(testDir.resolve("failing-input")).resolve("failing.zip")
    new ZipFile(zip.toFile) {
      addFolder(testBagsUnpruned.resolve("a/data").toFile, new ZipParameters)
    }

    val uuid = "66666666-6666-6666-6666-666666666666"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(zip), basicAuthentication) {
      status shouldBe 400
      body should include(s"Bag $uuid is not a valid bag")
    }
  }
}
