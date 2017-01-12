/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.bagstore

import java.io.File
import java.net.URI
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths}

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.configuration.PropertiesConfiguration

import scala.util.Try

trait BagStoreApp extends BagStoreAddComponent
  with BagStoreEnumComponent
  with BagStoreGetComponent
  with BagStoreCompleteComponent
  with BagStoreDeleteComponent
  with BagStorePruneComponent
  with Bagit4FacadeComponent
  with BagStoreContextComponent
  with BagStoreOutputContextComponent
  with DebugEnhancedLogging {

  val properties = new PropertiesConfiguration(new File(new File(System.getProperty("app.home")), "cfg/application.properties"))

  override val bagFacade = new Bagit4Facade()
  override val add = new BagStoreAdd {}
  override val complete = new BagStoreComplete {}
  override protected def context0 = new BagStoreContext {
    override val baseDir: Path = Paths.get(properties.getString("bag-store.base-dir")).toAbsolutePath
    override val baseUri = new URI(properties.getString("bag-store.base-uri"))
    override val stagingBaseDir: Path = Paths.get(properties.getString("staging.base-dir"))
    override val uuidPathComponentSizes: Seq[Int] = properties.getStringArray("bag-store.uuid-component-sizes").map(_.toInt).toSeq
    override val bagPermissions: String = properties.getString("bag-store.bag-file-permissions")
  }
  override val delete = new BagStoreDelete {}
  override val enum = new BagStoreEnum {}
  override val get = new BagStoreGet {}
  override val outputContext = new BagStoreOutputContext {
    override val outputBagPermissions: String = properties.getString("output.bag-file-permissions")
  }
  override val prune = new BagStorePrune {}

  def validateSettings(): Unit = {
    assert(Files.isWritable(context.baseDir), s"Non-existent or non-writable base-dir: ${ context.baseDir }")
    assert(Files.isWritable(context.stagingBaseDir), s"Non-existent or non-writable staging base-dir: ${ context.stagingBaseDir }")
    assert(context.uuidPathComponentSizes.sum == 32, s"UUID-path component sizes must add up to length of UUID in hexadecimal, sum found: ${ context.uuidPathComponentSizes.sum }")
    assert(Try(PosixFilePermissions.fromString(context.bagPermissions)).isSuccess, s"Bag file permissions are invalid: '${ context.bagPermissions }'")
    assert(Try(PosixFilePermissions.fromString(outputContext.outputBagPermissions)).isSuccess, s"Bag export file permissions are invalid: '${ outputContext.outputBagPermissions }'")
  }
}
