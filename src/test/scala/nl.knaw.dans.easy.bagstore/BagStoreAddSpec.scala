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

import java.nio.file.{ Path, Paths }
import java.util.UUID

import org.apache.commons.io.FileUtils

import scala.util._

class BagStoreAddSpec extends BagStoreFixture with BagStoreAdd with BagStorePrune {
  private val TEST_BAGS_DIR = Paths.get("src/test/resources/bags")
  private val TEST_BAG_MINIMAL = TEST_BAGS_DIR.resolve("minimal-bag")
  private val TEST_BAG_INCOMPLETE = TEST_BAGS_DIR.resolve("incomplete-bag")
  private val TEST_BAGS_PRUNED = TEST_BAGS_DIR.resolve("basic-sequence-pruned")
  private val TEST_BAG_PRUNED_A = TEST_BAGS_PRUNED.resolve("a")
  private val TEST_BAG_PRUNED_B = TEST_BAGS_PRUNED.resolve("b")
  private val TEST_BAG_PRUNED_C = TEST_BAGS_PRUNED.resolve("c")
  private val TEST_BAGS_WITH_REFS = TEST_BAGS_DIR.resolve("basic-sequence-unpruned-with-refbags")
  private val TEST_BAG_WITH_REFS_A = TEST_BAGS_WITH_REFS.resolve("a")
  private val TEST_BAG_WITH_REFS_B = TEST_BAGS_WITH_REFS.resolve("b")
  private val TEST_BAG_WITH_REFS_C = TEST_BAGS_WITH_REFS.resolve("c")

  def testSuccessfulAdd(path: Path, uuid: UUID): Unit = {
    inside(add(path, Some(uuid))) {
      case Success(bagId) => bagId.uuid shouldBe uuid
    }
  }

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
    val uuid1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val uuid3 = UUID.fromString("00000000-0000-0000-0000-000000000003")

    testSuccessfulAdd(TEST_BAG_PRUNED_A, uuid1)

    /*
     * B and C are virtually-valid.
     */
    testSuccessfulAdd(TEST_BAG_PRUNED_B, uuid2)
    testSuccessfulAdd(TEST_BAG_PRUNED_C, uuid3)
  }

  it should "refuse to ingest hidden bag directories" in {
    val HIDDEN_BAGDIR = testDir.resolve(".some-hidden-bag")
    FileUtils.copyDirectory(TEST_BAG_MINIMAL.toFile, HIDDEN_BAGDIR.toFile)
    val result = add(HIDDEN_BAGDIR)
    inside(result) {
      case Failure(e) => e shouldBe a[CannotIngestHiddenBagDirectory]
    }
  }

  it should "first prune a bag if a refbags file is present" in {
    val uuid1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val uuid2 = UUID.fromString("11111111-1111-1111-1111-111111111112")
    val uuid3 = UUID.fromString("11111111-1111-1111-1111-111111111113")

    testSuccessfulAdd(TEST_BAG_WITH_REFS_A, uuid1)

    /*
     * B and C are pruned before they are added.
     */
    testSuccessfulAdd(TEST_BAG_WITH_REFS_B, uuid2)
    testSuccessfulAdd(TEST_BAG_WITH_REFS_C, uuid3)
  }
}
