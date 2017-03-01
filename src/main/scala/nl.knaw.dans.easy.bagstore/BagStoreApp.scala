/**
 * Copyright (C) 2016-17 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
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

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

trait BagStoreApp extends BagStoreContext
  with BagStoreAdd
  with BagStoreEnum
  with BagStoreGet
  with BagStoreComplete
  with BagStoreDeactivate
  with BagStorePrune
  with Bagit4FacadeComponent
  with BagStoreOutputContext
  with DebugEnhancedLogging {

  val properties = new PropertiesConfiguration(new File(System.getProperty("app.home"), "cfg/application.properties"))
  val localBaseUri = new URI(properties.getString("bag-store.base-uri"))
  val stagingBaseDir: Path = Paths.get(properties.getString("staging.base-dir"))
  val uuidPathComponentSizes: Seq[Int] = properties.getStringArray("bag-store.uuid-component-sizes").map(_.toInt).toSeq
  val bagPermissions: String = properties.getString("bag-store.bag-file-permissions")
  val outputBagPermissions: String = properties.getString("output.bag-file-permissions")
  val bagFacade = new Bagit4Facade()

  // TODO: make this shorter and validate (check path syntax + writeability).
  val stores: Map[String, Path] = {
    val map = mutable.Map[String, Path]()
    val props = new PropertiesConfiguration(Paths.get(System.getProperty("app.home"), "cfg/stores.properties").toFile)
    props.getKeys.asScala.foreach(k => map(k) = Paths.get(props.getString(k)).toAbsolutePath)
    map.toMap
  }

  protected def validateSettings(): Unit =  {
    assert(Files.isWritable(stagingBaseDir), s"Non-existent or non-writable staging base-dir: $stagingBaseDir")
    assert(uuidPathComponentSizes.sum == 32, s"UUID-path component sizes must add up to length of UUID in hexadecimal, sum found: ${uuidPathComponentSizes.sum}")
    assert(Try(PosixFilePermissions.fromString(bagPermissions)).isSuccess, s"Bag file permissions are invalid: '$bagPermissions'")
    assert(Try(PosixFilePermissions.fromString(outputBagPermissions)).isSuccess, s"Bag export file permissions are invalid: '$outputBagPermissions'")
  }
}

