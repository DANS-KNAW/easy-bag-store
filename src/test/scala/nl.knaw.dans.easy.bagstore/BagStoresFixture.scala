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

import java.net.URI

import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }

trait BagStoresFixture extends BagStoreFixture {
  test: TestSupportFixture
    with BagFacadeComponent
    with BagStoresComponent
    with BagStoreComponent
    with BagProcessingComponent
    with FileSystemComponent =>

  override val fileSystem = new FileSystem {
    override val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
    override val bagPermissions: String = "rwxr-xr-x"
    override val localBaseUri: URI = new URI("http://localhost")
  }

  override val bagProcessing = new BagProcessing {
    override val stagingBaseDir: BagPath = testDir
    override val outputBagPermissions: String = "rwxr-xr-x"
  }

  val bagStore1 = new BagStore {
    implicit val baseDir: BaseDir = store1
  }

  val bagStore2 = new BagStore {
    implicit val baseDir: BaseDir = store2
  }

  override val bagStores = new BagStores {
    override val storeShortnames: Map[String, BaseDir] = Map(
      "store1" -> store1,
      "store2" -> store2
    )
  }
}
