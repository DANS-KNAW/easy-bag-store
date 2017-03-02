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

import java.nio.file.{Files, Paths}

import scala.util.{Failure, Success}

class BagStoreDeactivateSpec extends BagStoreFixture with BagStoreDeactivate with BagStoreAdd with BagStorePrune {
  private val TEST_BAGS_DIR = Paths.get("src/test/resources/bags")
  private val TEST_BAG_MINIMAL = TEST_BAGS_DIR.resolve("minimal-bag")
  implicit val baseDir = store1

  "deactivate" should "be able to inactivate a Bag that is not yet inactive" in {
    val tryBagId = add(TEST_BAG_MINIMAL, store1)
    tryBagId shouldBe a[Success[_]]

    val tryInactiveBagId = deactivate(tryBagId.get)
    tryInactiveBagId shouldBe a[Success[_]]
    Files.isHidden(toLocation(tryBagId.get).get) shouldBe true
  }

  it should "result in a Failure if Bag is already inactive" in {
    val tryBagId = add(TEST_BAG_MINIMAL, store1)
    deactivate(tryBagId.get) shouldBe a[Success[_]]

    inside(deactivate(tryBagId.get)) {
      case Failure(e) => e shouldBe a[AlreadyInactiveException]
    }
  }

  "reactivate" should "be able to reactivate an inactive Bag" in {
    val tryBagId = add(TEST_BAG_MINIMAL, store1)
    deactivate(tryBagId.get) shouldBe a[Success[_]]
    inside(isHidden(tryBagId.get)) {
      case Success(hidden) => hidden shouldBe true
    }

    reactivate(tryBagId.get) shouldBe a[Success[_]]
    Files.isHidden(toLocation(tryBagId.get).get) shouldBe false
  }

  it should "result in a Failure if Bag is not marked as inactive" in {
    val tryBagId = add(TEST_BAG_MINIMAL, store1)

    inside(reactivate(tryBagId.get)) {
      case Failure(e) => e shouldBe a[NotInactiveException]
    }
  }
}
