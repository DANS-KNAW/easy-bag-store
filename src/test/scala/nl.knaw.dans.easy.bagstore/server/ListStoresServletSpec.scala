/*
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
import java.nio.file.Paths

import nl.knaw.dans.easy.bagstore.{ BagitFixture, BaseDir, TestSupportFixture }
import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }
import org.scalamock.scalatest.MockFactory
import org.scalatra.test.EmbeddedJettyContainer
import org.scalatra.test.scalatest.ScalatraSuite

// NOTE: this functionality is part of the StoresServlet, but because we need to have control
// over the stores using mocking, we have to test this in a separate class
class ListStoresServletSpec extends TestSupportFixture
  with BagitFixture
  with EmbeddedJettyContainer
  with ScalatraSuite
  with MockFactory
  with StoresServletComponent
  with BagStoresComponent
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  override val fileSystem: FileSystem = mock[FileSystem]
  override val bagProcessing: BagProcessing = mock[BagProcessing]
  override val bagStores: BagStores = mock[BagStores]
  override val storesServlet: StoresServlet = new StoresServlet {
    override val externalBaseUri: URI = new URI("http://example-archive.org/")
    override val bagstoreUsername: String = "unused"
    override val bagstorePassword: String = "unused"
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    addServlet(storesServlet, "/*")
  }

  "get /" should "list all stores as urls to be called" in {
    val storeMap: Map[String, BaseDir] = Map(
      "store1" -> Paths.get("/base/store1"),
      "store2" -> Paths.get("/base/store2")
    )

    bagStores.storeShortnames _ expects() once() returning storeMap

    get("/") {
      status shouldBe 200
      body shouldBe "<http://example-archive.org/stores/store1>\n<http://example-archive.org/stores/store2>"
    }
  }

  it should "return an empty message when there are no bagstores" in {
    bagStores.storeShortnames _ expects() once() returning Map.empty

    get("/") {
      status shouldBe 200
      body shouldBe empty
    }
  }
}
