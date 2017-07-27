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

import java.nio.file.{ Files, Path }

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterEach

trait BagStoreFixture extends BeforeAndAfterEach {
  this: TestSupportFixture with BagFacadeComponent =>

  val store1: Path = testDir.resolve("bag-store-1")
  val store2: Path = testDir.resolve("bag-store-2")

  override def beforeEach(): Unit = {
    super.beforeEach()

    FileUtils.deleteDirectory(store1.toFile)
    FileUtils.deleteDirectory(store2.toFile)

    Files.createDirectories(store1)
    Files.createDirectories(store2)
  }
}
