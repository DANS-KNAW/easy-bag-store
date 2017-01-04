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

import java.nio.file.{Path, Paths}

import org.apache.commons.io.FileUtils

import scala.util.{Failure, Success}

trait BagFacadeComponentSpec extends TestSupportFixture with BagFacadeComponent {

  val testResourcesDir: Path = Paths.get("src/test/resources/")

  "isValid" should "return true when passed a valid bag-dir" in {
    FileUtils.copyDirectory(testResourcesDir.resolve("bags/valid-bag").toFile, testDir.resolve("valid-bag").toFile)
    bagFacade.isValid(testDir.resolve("valid-bag")) shouldBe Success(true)
  }

  it should "return false when passed a bag-dir that is not valid" in {
    FileUtils.copyDirectory(testResourcesDir.resolve("bags/incomplete-bag").toFile, testDir.resolve("incomplete-bag").toFile)
    bagFacade.isValid(testDir.resolve("incomplete-bag")) shouldBe Success(false)
  }

  it should "return a failure when passed a non-existent directory" in {
    bagFacade.isValid(testDir.resolve("non-existent-dir")) shouldBe a[Failure[_]]
  }
}

class Bagit4FacadeComponentSpec extends BagFacadeComponentSpec with Bagit4FacadeComponent {
  val bagFacade: BagFacade = new Bagit4Facade()
}
