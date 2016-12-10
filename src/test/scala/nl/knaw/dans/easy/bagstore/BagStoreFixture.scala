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

import java.net.URI
import java.nio.file.Paths

import org.apache.commons.io.FileUtils
import org.scalatest.{FlatSpec, Matchers, OneInstancePerTest}

/**
 * Common base class for tests that need to set up a test bag store. This class should only do the set-up that is
 * common to all these tests, nothing more!
 */
abstract class BagStoreFixture extends TestSupportFixture with BagStoreContext {
  implicit val baseDir = testDir.resolve("bag-store")
  implicit val baseUri = new URI("http://example-archive")
  implicit val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
  baseDir.toFile.mkdirs()
}
