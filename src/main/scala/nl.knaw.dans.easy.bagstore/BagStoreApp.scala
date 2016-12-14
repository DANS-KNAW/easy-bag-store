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
import java.nio.file.{Path, Paths}

import org.apache.commons.configuration.PropertiesConfiguration

trait BagStoreApp extends BagStoreContext
  with BagStoreAdd
  with BagStoreEnum
  with BagStoreGet
  with BagStoreComplete
  with BagStoreHide {
  val properties = new PropertiesConfiguration(new File(new File(System.getProperty("app.home")), "cfg/application.properties"))
  implicit val baseDir: Path = Paths.get(properties.getString("bag-store.base-dir"))
  implicit val baseUri = new URI(properties.getString("bag-store.base-uri"))
  implicit val uuidPathComponentSizes: Seq[Int] = properties.getStringArray("bag-store.uuid-component-sizes").map(_.toInt).toSeq
}

