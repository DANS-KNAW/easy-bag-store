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
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{ Files, Paths }

import nl.knaw.dans.easy.bagstore.{ BagFacadeComponent, BaseDir, ConfigurationComponent }

import scala.collection.JavaConverters._
import scala.util.Try

trait BagStoreWiring extends BagStoresComponent with BagStoreComponent with BagProcessingComponent with FileSystemComponent {
  this: ConfigurationComponent with BagFacadeComponent =>

  private val properties = configuration.properties

  override lazy val fileSystem: FileSystem = new FileSystem {
    override val uuidPathComponentSizes: Seq[Int] = properties.getStringArray("bag-store.uuid-component-sizes").map(_.toInt).toSeq
    override val bagPermissions: String = properties.getString("bag-store.bag-file-permissions")
    override val localBaseUri: URI = new URI(properties.getString("bag-store.base-uri"))

    require(uuidPathComponentSizes.sum == 32, s"UUID-path component sizes must add up to length of UUID in hexadecimal, sum found: ${ uuidPathComponentSizes.sum }")
    require(Try(PosixFilePermissions.fromString(bagPermissions)).isSuccess, s"Bag file permissions are invalid: '$bagPermissions'")
  }
  override lazy val processor: BagProcessing = new BagProcessing {
    override val outputBagPermissions: String = properties.getString("output.bag-file-permissions")
    override val stagingBaseDir: BagPath = Paths.get(properties.getString("staging.base-dir"))

    require(Try(PosixFilePermissions.fromString(outputBagPermissions)).isSuccess, s"Bag export file permissions are invalid: '$outputBagPermissions'")
    require(Files.isWritable(stagingBaseDir), s"Non-existent or non-writable staging base-dir: $stagingBaseDir")
  }

  override lazy val bagStores: BagStores = new BagStores {
    bagStores =>
    override val stores: Map[String, BagStore] = {
      val stores = configuration.stores
      stores.getKeys.asScala
        .map(name => name -> new BagStore {
          implicit val baseDir: BaseDir = Paths.get(stores.getString(name)).toAbsolutePath
        })
        .toMap
    }
  }
}
