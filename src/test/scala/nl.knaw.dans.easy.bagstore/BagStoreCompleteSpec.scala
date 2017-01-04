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
import java.util.UUID

import org.apache.commons.io.FileUtils

import scala.util.Success

class BagStoreCompleteSpec extends BagStoreFixture with BagStoreCompleteComponent with BagStoreAddComponent with BagStoreOutputContextComponent {
  private val TEST_BAGS_DIR = Paths.get("src/test/resources/bags")
  private val TEST_BAGS_PRUNED = TEST_BAGS_DIR.resolve("basic-sequence-pruned")
  private val TEST_BAGS_UNPRUNED = TEST_BAGS_DIR.resolve("basic-sequence-unpruned")
  private val TEST_BAG_PRUNED_A = TEST_BAGS_PRUNED.resolve("a")
  private val TEST_BAG_PRUNED_B = TEST_BAGS_PRUNED.resolve("b")
  private val TEST_BAG_PRUNED_C = TEST_BAGS_PRUNED.resolve("c")
  private val TEST_BAG_UNPRUNED_C = TEST_BAGS_UNPRUNED.resolve("c")

  override val add = new BagStoreAdd {}
  override val complete = new BagStoreComplete {}
  override val outputContext = new BagStoreOutputContext {
    override val outputBagPermissions: String = "rwxr-xr-x"
  }

  "complete" should "make pruned Bag whole again" in {
    add.add(TEST_BAG_PRUNED_A, Some(UUID.fromString("00000000-0000-0000-0000-000000000001")))
    add.add(TEST_BAG_PRUNED_B, Some(UUID.fromString("00000000-0000-0000-0000-000000000002")))
    val testDirBagC = testDir.resolve("c")
    FileUtils.copyDirectory(TEST_BAG_PRUNED_C.toFile, testDirBagC.toFile)
    val result = complete.complete(testDirBagC)
    result shouldBe a[Success[_]]
    pathsEqual(TEST_BAG_UNPRUNED_C, testDirBagC) shouldBe true
  }
}
