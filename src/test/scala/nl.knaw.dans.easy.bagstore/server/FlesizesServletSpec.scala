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

import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }
import nl.knaw.dans.lib.encode.PathEncoding
import org.apache.commons.io.FileUtils
import org.scalatra.test.EmbeddedJettyContainer
import org.scalatra.test.scalatest.ScalatraSuite

import scala.util.Success

class FlesizesServletSpec extends TestSupportFixture
  with BagitFixture
  with BagStoresFixture
  with EmbeddedJettyContainer
  with ScalatraSuite
  with FilesizesServletComponent
  with BagStoresComponent
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  override val filesizesServlet: FilesizesServlet = new FilesizesServlet {}

  override def beforeAll(): Unit = {
    super.beforeAll()
    addServlet(filesizesServlet, "/*")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store1").toFile, store1.toFile)
  }

  it should "return filesize when file is found and is a regular file" in {
    val itemId = "01000000-0000-0000-0000-000000000001/data/sub/u"
    get(s"/$itemId", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 200
      body shouldBe "12"
    }
  }

  it should "fail when the bag is not found" in {
    get(s"/${ UUID.randomUUID() }") {
      status shouldBe 404
    }
  }

  it should "fail when only the bag id is given" in {
    val itemId = "01000000-0000-0000-0000-000000000001"
    get(s"/$itemId", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 404
    }
  }

  it should "fail when the item is not a regular file" in {
    val itemId = "01000000-0000-0000-0000-000000000001/data/sub"
    get(s"/$itemId", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 404
      body shouldBe s"Item $itemId is not a regular file."
    }
  }

  it should "fail when the file within a bag cannot be found" in {
    val itemId = "01000000-0000-0000-0000-000000000001/data/nonexistent"
    get(s"/$itemId", headers = Map("Accept" -> "text/plain")) {
      status shouldBe 404
      body shouldBe s"Item $itemId not found"
    }
  }
}
