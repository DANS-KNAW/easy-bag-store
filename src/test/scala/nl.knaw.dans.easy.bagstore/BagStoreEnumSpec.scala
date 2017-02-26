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

import java.nio.file.Paths

import org.apache.commons.io.FileUtils

import scala.util.Success

class BagStoreEnumSpec extends BagStoreFixture with BagStoreEnum with BagStoreAdd with BagStorePrune with BagStoreDeactivate {
  FileUtils.copyDirectory(Paths.get("src/test/resources/bags/basic-sequence-unpruned").toFile, testDir.toFile)
  FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bags/valid-bag-complementary-manifests").toFile, testDir.toFile)
  private val TEST_BAG_A = testDir.resolve("a")
  private val TEST_BAG_B = testDir.resolve("b")
  private val TEST_BAG_C = testDir.resolve("c")
  private val TEST_BAG_COMPLEMENTARY = testDir.resolve("valid-bag-complementary-manifests")

  "enumBags" should "return all BagIds" in {
    val ais = add(TEST_BAG_A, baseDir).get
    val bis = add(TEST_BAG_B, baseDir).get
    val cis = add(TEST_BAG_C, baseDir).get

    inside(enumBags().map(_.toList)) {
      case Success(bagIds) => bagIds should (have size 3 and contain only (ais, bis, cis))
    }
  }

  it should "return empty stream if BagStore is empty" in {
    inside(enumBags().map(_.toList)) {
      case Success(bagIds) => bagIds shouldBe empty
    }
  }

  it should "skip hidden Bags by default" in {
    val ais = add(TEST_BAG_A, baseDir).get
    val bis = add(TEST_BAG_B, baseDir).get
    val cis = add(TEST_BAG_C, baseDir).get

    deactivate(bis) shouldBe a[Success[_]]

    inside(enumBags().map(_.toList)) {
      case Success(bagIds) => bagIds should (have size 2 and contain only (ais, cis))
    }
  }

  it should "include hidden Bags if requested" in {
    val ais = add(TEST_BAG_A, baseDir).get
    val bis = add(TEST_BAG_B, baseDir).get
    val cis = add(TEST_BAG_C, baseDir).get

    deactivate(bis) shouldBe a[Success[_]]

    inside(enumBags(includeInactive = true).map(_.toList)) {
      case Success(bagIds) => bagIds should (have size 3 and contain only (ais, bis, cis))
    }
  }

  it should "skip visible Bags if requested" in {
    add(TEST_BAG_A, baseDir).get
    val bis = add(TEST_BAG_B, baseDir).get
    add(TEST_BAG_C, baseDir).get

    deactivate(bis) shouldBe a[Success[_]]

    inside(enumBags(includeActive = false, includeInactive = true).map(_.toList)) {
      case Success(bagIds) => bagIds should (have size 1 and contain only bis)
    }
  }

  it should "skip all Bags if requested" in {
    add(TEST_BAG_A, baseDir).get
    val bis = add(TEST_BAG_B, baseDir).get
    add(TEST_BAG_C, baseDir).get

    deactivate(bis) shouldBe a[Success[_]]

    inside(enumBags(includeActive = false).map(_.toList)) {
      case Success(bagIds) => bagIds shouldBe empty
    }
  }

  "enumFiles" should "return all FileIds in a valid Bag" in {
    val ais = add(TEST_BAG_A, baseDir).get

    inside(enumFiles(ais).map(_.toList)) {
      case Success(fileIds) => fileIds.map(_.path.getFileName.toString) should (have size 10 and
        contain only ("u", "v", "w", "x", "y", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "tagmanifest-md5.txt"))
    }
  }

  it should "return all FileIds in a virtually-valid Bag" in {
    val ais = add(TEST_BAG_A, baseDir).get
    prune(TEST_BAG_B, ais) shouldBe a[Success[_]]
    val bis = add(TEST_BAG_B, baseDir).get
    prune(TEST_BAG_C, ais, bis) shouldBe a[Success[_]]
    val cis = add(TEST_BAG_C, baseDir).get

    inside(enumFiles(cis).map(_.toList)) {
      case Success(fileIds) => fileIds.map(_.path.getFileName.toString) should (have size 13 and
        contain only ("q", "w", "u", "p", "x", "y", "y-old", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "tagmanifest-md5.txt", "fetch.txt"))
    }
  }

  /*
   * If there are multiple payload manifests the BagIt specs do not require that they all contain ALL the payload files. Therefore, it is possible that
   * there are two payload manifests, each of contains a part of the payload file paths. The enum operation should still handle this correctly. The
   * example used also has one overlapping file, to make sure that it does not appear twice in the enumeration.
   *
   * See: <https://tools.ietf.org/html/draft-kunze-bagit#section-3> point 4.
   */
  it should "return all FileIds even if they are distributed over several payload manifests" in {
    val complementary = add(TEST_BAG_COMPLEMENTARY, baseDir).get
    inside(enumFiles(complementary).map(_.toList)) {
      case Success(fileIds) => fileIds.map(_.path.getFileName.toString) should (have size 11 and
        contain only ("u", "v", "w", "x", "y", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "manifest-sha1.txt", "tagmanifest-md5.txt"))
    }
  }
}
