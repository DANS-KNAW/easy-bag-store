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
import nl.knaw.dans.easy.bagstore.{ BagitFixture, ServletFixture, TestSupportFixture }
import org.scalamock.scalatest.MockFactory
import org.scalatra.test.scalatest.ScalatraSuite

class DefaultServletSpec extends TestSupportFixture
  with BagitFixture
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
      body should (include("EASY Bag Store is running") and include("Available stores at <http://example-archive.org/stores>"))
    }
  }
}
