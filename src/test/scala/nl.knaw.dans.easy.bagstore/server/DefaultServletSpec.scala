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

import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }
import nl.knaw.dans.easy.bagstore.{ Bagit4Fixture, ServletFixture, TestSupportFixture }
import org.scalamock.scalatest.MockFactory
import org.scalatra.test.scalatest.ScalatraSuite

class DefaultServletSpec extends TestSupportFixture
  with Bagit4Fixture
  with ServletFixture
  with ScalatraSuite
  with MockFactory
  with DefaultServletComponent
  with BagStoresComponent
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  override val fileSystem: FileSystem = mock[FileSystem]
  override val processor: BagProcessing = mock[BagProcessing]
  override val bagStores: BagStores = mock[BagStores]
  override val defaultServlet: DefaultServlet = new DefaultServlet {
    override val externalBaseUri: URI = new URI("http://example-archive.org/")
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    addServlet(defaultServlet, "/*")
  }

  "get" should "signal that the service is running" in {
    get("/") {
      status shouldBe 200
      body should (include ("EASY Bag Store is running") and include ("Available stores at <http://example-archive.org/stores>"))
    }
  }

  "get stores" should "list all stores as urls to be called" in {
    val mockStore1 = mock[BagStore]
    val mockStore2 = mock[BagStore]

    val storeMap: Map[String, BagStore] = Map(
      "store1" -> mockStore1,
      "store2" -> mockStore2
    )

    bagStores.stores _ expects() once() returning storeMap

    get("/stores") {
      status shouldBe 200
      body shouldBe "<http://example-archive.org/stores/store1>\n<http://example-archive.org/stores/store2>"
    }
  }

  it should "return an empty message when there are no bagstores" in {
    bagStores.stores _ expects() once() returning Map.empty

    get("/stores") {
      status shouldBe 200
      body shouldBe empty
    }
  }
}
