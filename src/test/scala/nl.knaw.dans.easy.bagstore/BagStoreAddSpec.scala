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
import org.scalatest.Inside._

import scala.util._

class BagStoreAddSpec extends BagStoreFixture with BagStoreAdd {
  private val TEST_BAGS_DIR = Paths.get("src/test/resources/bags")
  private val TEST_BAG_MINIMAL = TEST_BAGS_DIR.resolve("minimal-bag")
  private val TEST_BAG_INCOMPLETE = TEST_BAGS_DIR.resolve("incomplete-bag")
  private val TEST_BAGS_PRUNED = TEST_BAGS_DIR.resolve("basic-sequence-pruned")
  private val TEST_BAG_PRUNED_A = TEST_BAGS_PRUNED.resolve("a")
  private val TEST_BAG_PRUNED_B = TEST_BAGS_PRUNED.resolve("b")
  private val TEST_BAG_PRUNED_C = TEST_BAGS_PRUNED.resolve("c")


  "add" should "result in exact copy (except for bag-info.txt) of bag in archive when bag is valid" in {
    val tryBagId = add(TEST_BAG_MINIMAL)
    tryBagId shouldBe a[Success[_]]
    val bagDirInStore = tryBagId.flatMap(toLocation).get
    pathsEqual(TEST_BAG_MINIMAL, bagDirInStore, excludeFiles = "bag-info.txt") shouldBe true
  }

  it should "result in a Failure if bag is incomplete" in {
    add(TEST_BAG_INCOMPLETE) shouldBe a[Failure[_]]
  }

  it should "accept virtually-valid bags" in {
    add(TEST_BAG_PRUNED_A, Some(UUID.fromString("00000000-0000-0000-0000-000000000001")))

    /*
     * B and C are virtually-valid.
     */
    add(TEST_BAG_PRUNED_B, Some(UUID.fromString("00000000-0000-0000-0000-000000000002")))
    add(TEST_BAG_PRUNED_C, Some(UUID.fromString("00000000-0000-0000-0000-000000000003")))
  }

  it should "refuse to ingest hidden bag directories" in {
    val HIDDEN_BAGDIR = testDir.resolve(".some-hidden-bag")
    FileUtils.copyDirectory(TEST_BAG_MINIMAL.toFile, HIDDEN_BAGDIR.toFile)
    val result = add(HIDDEN_BAGDIR)
    result shouldBe a[Failure[_]]
    inside(result) {
      case Failure(e) => e shouldBe a[CannotIngestHiddenBagDirectory]
    }
  }
}
