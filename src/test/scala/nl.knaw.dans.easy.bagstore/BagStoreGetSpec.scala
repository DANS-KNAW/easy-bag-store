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

import java.nio.file.Paths

import org.apache.commons.io.FileUtils

import scala.util.Success

class BagStoreGetSpec extends BagStoreFixture with BagStoreGet with BagStoreAdd with BagStorePrune with BagStoreOutputContext {
  override val outputBagPermissions: String = "rwxrwxrwx"
  private val TEST_BAGS_DIR = Paths.get("src/test/resources/bags")
  private val TEST_BAGS_PRUNED = TEST_BAGS_DIR.resolve("basic-sequence-pruned")
  private val TEST_BAG_PRUNED_A = TEST_BAGS_PRUNED.resolve("a")

  "get" should "return exactly the same Bag as was added" in {
    get(add(TEST_BAG_PRUNED_A).get, testDir) shouldBe a[Success[_]]
    pathsEqual(TEST_BAG_PRUNED_A, testDir.resolve("a")) shouldBe true
  }

  it should "create Bag base directory with the name of parameter 'output' if 'output' does not point to existing directory" in {
    val output = testDir.resolve("non-existent-directory-that-will-become-base-dir-of-exported-Bag")

    get(add(TEST_BAG_PRUNED_A).get, output) shouldBe a[Success[_]]
    pathsEqual(TEST_BAG_PRUNED_A, output) shouldBe true
  }

  it should "return a File in the Bag that was added" in {
    val bagId = add(TEST_BAG_PRUNED_A).get
    val fileId = FileId(bagId, Paths.get("data/x"))

    get(fileId, testDir) shouldBe a[Success[_]]
    pathsEqual(TEST_BAG_PRUNED_A.resolve("data/x"), testDir.resolve("x")) shouldBe true
  }

  it should "rename a File to name specified in 'output' if 'output' does not point to an existing directory" in {
    val bagId = add(TEST_BAG_PRUNED_A).get
    val fileId = FileId(bagId, Paths.get("data/x"))

    get(fileId, testDir.resolve("x-renamed")) shouldBe a[Success[_]]
    // Attention: pathsEqual cannot be used, as it also compares file names
    FileUtils.contentEquals(TEST_BAG_PRUNED_A.resolve("data/x").toFile, testDir.resolve("x-renamed").toFile) shouldBe true
  }

  // TODO: add tests for failures
  // TODO: add tests for file permissions

}
