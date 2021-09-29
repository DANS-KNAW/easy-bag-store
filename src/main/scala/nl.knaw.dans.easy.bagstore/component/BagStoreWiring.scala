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
package nl.knaw.dans.easy.bagstore.component

import java.net.URI
import java.util.{Set => JSet}
import java.nio.file.attribute.{ PosixFilePermission, PosixFilePermissions }
import java.nio.file.{ Files, Paths }

import nl.knaw.dans.easy.bagstore.{ BagFacadeComponent, BaseDir, ConfigurationComponent }

import scala.collection.JavaConverters._

trait BagStoreWiring extends BagStoresComponent with BagStoreComponent with BagProcessingComponent with FileSystemComponent {
  this: ConfigurationComponent with BagFacadeComponent =>

  private val properties = configuration.properties

  override lazy val fileSystem: FileSystem = new FileSystem {
    override val uuidPathComponentSizes: Seq[Int] = properties.getStringArray("bag-store.uuid-slash-pattern").map(_.toInt).toSeq
    override val bagFilePermissions: JSet[PosixFilePermission] = PosixFilePermissions.fromString(properties.getString("bag-store.bag-file-permissions"))
    override val bagDirPermissions: JSet[PosixFilePermission] = PosixFilePermissions.fromString(properties.getString("bag-store.bag-dir-permissions"))
    override val localBaseUri: URI = new URI(properties.getString("bag-store.local-file-uri-base"))

    require(uuidPathComponentSizes.sum == 32, s"UUID-path component sizes must add up to length of UUID in hexadecimal, sum found: ${ uuidPathComponentSizes.sum }")
  }
  override lazy val bagProcessing: BagProcessing = new BagProcessing {
    override val outputBagFilePermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString(properties.getString("cli.output.bag-file-permissions"))
    override val outputBagDirPermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString(properties.getString("cli.output.bag-dir-permissions"))
    override val stagingBaseDir: BagPath = Paths.get(properties.getString("bag-store.staging.base-dir"))

    require(Files.isWritable(stagingBaseDir), s"Non-existent or non-writable staging base-dir: $stagingBaseDir")
  }

  override lazy val bagStores: BagStores = new BagStores {
    bagStores =>
    override val storeShortnames: Map[String, BaseDir] = {
      val stores = configuration.stores
      stores.getKeys.asScala
        .map(name => name -> Paths.get(stores.getString(name)).toAbsolutePath)
        .toMap
    }
  }
}
