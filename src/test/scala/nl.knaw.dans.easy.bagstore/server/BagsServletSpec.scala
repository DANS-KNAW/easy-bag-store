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

import java.nio.file.{ Files, Paths }
import java.util.UUID

import nl.knaw.dans.lib.encode.PathEncoding
import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }
import org.apache.commons.io.FileUtils
import org.scalatra.test.EmbeddedJettyContainer
import org.scalatra.test.scalatest.ScalatraSuite

import scala.util.Success

class BagsServletSpec extends TestSupportFixture
  with BagitFixture
  with BagStoresFixture
  with EmbeddedJettyContainer
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
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store1").toFile, store1.toFile)
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store2").toFile, store2.toFile)
  }

  private def escapePath(path: String): String = {
    Paths.get(path).escapePath
  }

  private def setBag1Hidden(): Unit = {
    Files.move(store1.resolve("01/000000000000000000000000000001/bag-revision-1"), store1.resolve("01/000000000000000000000000000001/.bag-revision-1"))
  }

  private def makeBagstore1Invalid(): Unit = {
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/ff").toFile, store1.toFile)
  }

  "get" should "enumerate the bags in all bag-stores" in {
    get("/") {
      status shouldBe 200
      body.lines.toList should contain only(
        "01000000-0000-0000-0000-000000000001",
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003",
        "02000000-0000-0000-0000-000000000001",
        "02000000-0000-0000-0000-000000000002",
        "02000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "enumerate inactive bags only when this is set" in {
    setBag1Hidden()

    get("/", "state" -> "inactive") {
      status shouldBe 200
      body.lines.toList should contain only "01000000-0000-0000-0000-000000000001"
    }
  }

  it should "enumerate active bags only by default" in {
    setBag1Hidden()

    get("/") {
      status shouldBe 200
      body.lines.toList should contain only(
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003",
        "02000000-0000-0000-0000-000000000001",
        "02000000-0000-0000-0000-000000000002",
        "02000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "enumerate all bags when this is set" in {
    setBag1Hidden()

    get("/", "state" -> "all") {
      status shouldBe 200
      body.lines.toList should contain only(
        "01000000-0000-0000-0000-000000000001",
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003",
        "02000000-0000-0000-0000-000000000001",
        "02000000-0000-0000-0000-000000000002",
        "02000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "return an empty string when all bag-stores are empty" in {
    FileUtils.deleteDirectory(store1.resolve("01").toFile)
    FileUtils.deleteDirectory(store2.resolve("02").toFile)

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

  it should "enumerate the bags in all bag-stores even if an unknown state is given" in {
    get("/", params = Map("state" -> "invalid value"), headers = Map("Accept" -> "text/plain")) {
      status shouldBe 200
      body.lines.toList should contain only(
        "01000000-0000-0000-0000-000000000001",
        "01000000-0000-0000-0000-000000000002",
        "01000000-0000-0000-0000-000000000003",
        "02000000-0000-0000-0000-000000000001",
        "02000000-0000-0000-0000-000000000002",
        "02000000-0000-0000-0000-000000000003"
      )
    }
  }

  "get uuid" should "enumerate the files of a given bag" in {
    get("/01000000-0000-0000-0000-000000000001", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 200
      body.lines.toList should contain only(
        "01000000-0000-0000-0000-000000000001/" + escapePath("data/x"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("data/y"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("data/z"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("data/sub/u"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("data/sub/v"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("data/sub/w"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("metadata/dataset.xml"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("metadata/files.xml"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("bagit.txt"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("bag-info.txt"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("manifest-sha1.txt"),
        "01000000-0000-0000-0000-000000000001/" + escapePath("tagmanifest-sha1.txt")
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

  it should "fail when the given uuid is too long" in {
    val tooLongUuid = "01000000-0000-0000-0000-0000000000011234465565"
    get(s"/${ tooLongUuid }") {
      status shouldBe 400
      body shouldBe s"invalid UUID string: $tooLongUuid"
    }
  }

  it should "fail when the given uuid is too short" in {
    val tooShortUuid = "01000000-0000-0000-0000-0000"
    get(s"/${ tooShortUuid }") {
      status shouldBe 400
      body shouldBe s"invalid UUID string: $tooShortUuid"
    }
  }

  it should "fail when the bag is not found" in {
    get(s"/${ UUID.randomUUID() }") {
      status shouldBe 404
    }
  }

  it should "fail if a file within a bag cannot be found" in {
    val itemId = "01000000-0000-0000-0000-000000000001/" + escapePath("bag-info2.txt")
    get(s"/$itemId", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 404
      body shouldBe s"Item $itemId not found"
    }
  }

  it should "fail when done on an inactive/ hidden bag" in {
    val bagID = "01000000-0000-0000-0000-000000000001"
    bagStore1.deactivate(BagId(UUID.fromString(bagID))) shouldBe a[Success[_]]
    get(s"/$bagID") {
      status shouldBe 410
      body shouldBe s"Tried to retrieve an inactive bag: $bagID with toggle forceInactive = false"
    }
  }

  it should "fail, returning a 410, when done on an item within an inactive/ hidden bag" in {
    val bagID = "01000000-0000-0000-0000-000000000001"
    bagStore1.deactivate(BagId(UUID.fromString(bagID))) shouldBe a[Success[_]]
    get(s"/$bagID/bag-info.txt") {
      status shouldBe 410
      body shouldBe s"Tried to retrieve an inactive bag: $bagID with toggle forceInactive = false"
    }
  }

  it should "return a 404 non-existing item within an inactive/ hidden bag is requested" in {
    val bagID = "01000000-0000-0000-0000-000000000001"
    val itemId = bagID + "/" + escapePath("bag-info6.txt")
    bagStore1.deactivate(BagId(UUID.fromString(bagID))) shouldBe a[Success[_]]
    get(s"/$itemId") {
      status shouldBe 404
      body shouldBe s"Item $itemId not found"
    }
  }

  // this calls enumFiles
  it should " fail when done on an inactive/ hidden bag when headers text/plain is provided" in {
    val bagID = "01000000-0000-0000-0000-000000000001"
    bagStore1.deactivate(BagId(UUID.fromString(bagID))) shouldBe a[Success[_]]
    get(s"/$bagID", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 410
      body shouldBe s"Tried to retrieve an inactive bag: $bagID with toggle forceInactive = false"
    }
  }

  // this calls copyOutputStream
  it should "fail, returning a 404, when done on non existing item in an inactive/ hidden bag when headers text/plain is provided" in {
    val bagID = "01000000-0000-0000-0000-000000000001"
    val itemId = bagID + "/" + escapePath("bag-info2.txt")
    bagStore1.deactivate(BagId(UUID.fromString(bagID))) shouldBe a[Success[_]]
    get(s"/$itemId", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 404
      body shouldBe s"Item $itemId not found"
    }
  }

  it should "fail when done on an item in an inactive/ hidden bag when headers text/plain is provided" in {
    val bagID = "01000000-0000-0000-0000-000000000001"
    bagStore1.deactivate(BagId(UUID.fromString(bagID))) shouldBe a[Success[_]]
    get(s"/$bagID/bag-info.txt", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 410
      body shouldBe s"Tried to retrieve an inactive bag: $bagID with toggle forceInactive = false"
    }
  }
}
