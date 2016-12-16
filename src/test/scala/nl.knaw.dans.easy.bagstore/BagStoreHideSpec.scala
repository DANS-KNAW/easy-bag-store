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

import java.nio.file.{Files, Paths}

import org.scalatest.Inside.inside

import scala.util.{Failure, Success}

class BagStoreHideSpec extends BagStoreFixture with BagStoreHide with BagStoreAdd {
  private val TEST_BAGS_DIR = Paths.get("src/test/resources/bags")
  private val TEST_BAG_MINIMAL = TEST_BAGS_DIR.resolve("minimal-bag")


  "hide" should "be able to hide an unhidden Bag" in {
    val tryBagId = add(TEST_BAG_MINIMAL)
    tryBagId shouldBe a[Success[_]]

    val tryHiddenBagId = hide(tryBagId.get)
    tryHiddenBagId shouldBe a[Success[_]]
    Files.isHidden(toLocation(tryBagId.get).get) shouldBe true
  }

  it should "result in a Failure if Bag is already hidden" in {
    val tryBagId = add(TEST_BAG_MINIMAL)
    hide(tryBagId.get)
    val tryHiddenBagId = hide(tryBagId.get)
    tryHiddenBagId shouldBe a[Failure[_]]
    inside(tryHiddenBagId) {
      case Failure(e) => e shouldBe a[AlreadyHiddenException]
    }
  }

  "reveal" should "be able to unhide a hidden Bag" in {
    val tryBagId = add(TEST_BAG_MINIMAL)
    hide(tryBagId.get)
    isHidden(tryBagId.get).get shouldBe true
    val result = reveal(tryBagId.get)
    result shouldBe a[Success[_]]
    Files.isHidden(toLocation(tryBagId.get).get) shouldBe false
  }

  it should "result in a Failure if Bag is already visible" in {
    val tryBagId = add(TEST_BAG_MINIMAL)
    val result = reveal(tryBagId.get)
    result shouldBe a[Failure[_]]
    inside(result) {
      case Failure(e) => e shouldBe a[AlreadyVisibleException]
    }
  }


}
