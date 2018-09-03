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

import java.net.URI
import java.nio.file.attribute.{ PosixFilePermission, PosixFilePermissions }

import nl.knaw.dans.easy.bagstore._

class BagProcessingFixture extends TestSupportFixture
  with BagStoreFixture
  with BagitFixture
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  override val fileSystem: FileSystem = new FileSystem {
    override val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
    override val bagFilePermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
    override val bagDirPermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
    override val localBaseUri: URI = new URI("http://localhost")
  }

  override val bagProcessing: BagProcessing = new BagProcessing {
    override val stagingBaseDir: BagPath = testDir
    override val outputBagFilePermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
    override val outputBagDirPermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
  }
}
