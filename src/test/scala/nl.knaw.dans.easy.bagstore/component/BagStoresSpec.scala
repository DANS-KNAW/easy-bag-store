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
package nl.knaw.dans.easy.bagstore.component

import java.io.ByteArrayOutputStream
import java.nio.file.{ Files, Paths }

import nl.knaw.dans.easy.bagstore._
import org.apache.commons.io.FileUtils

import scala.util.{ Failure, Success }

class BagStoresSpec extends TestSupportFixture
  with BagStoresFixture
  with BagitFixture
  with BagStoresComponent
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/minimal-bag").toFile,
    testDir.resolve("minimal-bag").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-unpruned").toFile,
    testDir.resolve("basic-sequence-unpruned").toFile)
  FileUtils.copyDirectoryToDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-pruned/a").toFile,
    testDir.resolve("basic-sequence-pruned").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/valid-bag-complementary-manifests").toFile,
    testDir.resolve("valid-bag-complementary-manifests").toFile)

  private val testBagMinimal = testDir.resolve("minimal-bag")
  private val testBagUnprunedA = testDir.resolve("basic-sequence-unpruned/a")
  private val testBagUnprunedB = testDir.resolve("basic-sequence-unpruned/b")
  private val testBagUnprunedC = testDir.resolve("basic-sequence-unpruned/c")
  private val testBagComplementary = testDir.resolve("valid-bag-complementary-manifests")
  private val testBagPrunedA = testDir.resolve("basic-sequence-pruned/a")

  "get" should "return exactly the same Bag as was added" in {
    val output = testDir.resolve("pruned-output")
    inside(bagStore1.add(testBagPrunedA)) { case Success(result) =>
      bagStores.copyToDirectory(result, output, skipCompletion = true) shouldBe a[Success[_]]
      pathsEqual(testBagPrunedA, output.resolve("a")) shouldBe true
    }
  }

  it should "create Bag base directory with the name of parameter 'output' if 'output' does not point to existing directory" in {
    val output = testDir.resolve("non-existent-directory-that-will-become-base-dir-of-exported-Bag")
    inside(bagStore1.add(testBagPrunedA)) { case Success(result) =>
      bagStores.copyToDirectory(result, output, skipCompletion = true) shouldBe a[Success[_]]
      pathsEqual(testBagPrunedA, output.resolve("a")) shouldBe true
    }
  }

  it should "return a File in the Bag that was added" in {
    inside(bagStore1.add(testBagPrunedA)) { case Success(bagId) =>
      val fileId = FileId(bagId, Paths.get("data/x"))
      val output = Files.createDirectory(testDir.resolve("single-file-x"))

      bagStores.copyToDirectory(fileId, output) shouldBe a[Success[_]]
      pathsEqual(testBagPrunedA.resolve("data/x"), output.resolve("x")) shouldBe true
    }
  }

  it should "find a Bag in any BagStore if no specific BagStore is specified" in {
    inside(bagStore1.add(testBagPrunedA)) { case Success(bagId1) =>
      inside(bagStore2.add(testBagPrunedA)) { case Success(bagId2) =>
        bagStores.copyToDirectory(bagId1, testDir.resolve("bag-from-store1")) should matchPattern { case Success((_, `store1`)) => }
        bagStores.copyToDirectory(bagId2, testDir.resolve("bag-from-store2")) should matchPattern { case Success((_, `store2`)) => }
      }
    }
  }

  it should "result in failure if Bag is specifically looked for in the wrong BagStore" in {
    inside(bagStore1.add(testBagPrunedA)) { case Success(bagId1) =>
      inside(bagStore2.add(testBagPrunedA)) { case Success(bagId2) =>
        bagStores.copyToDirectory(bagId2, testDir.resolve("bag-from-store1-wrong"), skipCompletion = false, Some(store1)) should matchPattern {
          case Failure(NoSuchBagException(_)) =>
        }

        bagStores.copyToDirectory(bagId1, testDir.resolve("bag-from-store2-wrong"), skipCompletion = false, Some(store2)) should matchPattern {
          case Failure(NoSuchBagException(_)) =>
        }
      }
    }
  }

  it should "result in a failure when a get is done on a hidden bag with forceInactive=false" in {
    val (bagId: BagId, output: BagPath) = setupTestDeactivateBag
    // without force option results in failure
    bagStore1.copyToDirectory(bagId, output.resolve("a1"), false, false) should matchPattern {
      case Failure(_: InactiveException) =>
    }
    // with force option results bagId a success
    bagStore1.copyToDirectory(bagId, output.resolve("a1"), false, true) shouldBe a[Success[_]]
    // make deposit active again
    bagStore1.reactivate(bagId) shouldBe a[Success[_]]
    // now it works again without the force option
    bagStore1.copyToDirectory(bagId, output.resolve("a2"), false, false) shouldBe a[Success[_]]
    // it also works  with the force option
    bagStore1.copyToDirectory(bagId, output.resolve("a3"), false, false) shouldBe a[Success[_]]
  }

  // TODO: add tests for failures
  // TODO: add tests for file permissions

  private def setupTestDeactivateBag = {
    val bagId = bagStore1.add(testBagPrunedA).getOrElse(fail)
    val output = testDir.resolve("pruned-output/hidden/test")
    bagStores.copyToDirectory(bagId, output, skipCompletion = true).getOrElse(fail)
    bagStore1.deactivate(bagId).getOrElse(fail)
    (bagId, output)
  }

  "enumBags" should "return all BagIds" in {
    inside(bagStore1.add(testBagUnprunedA)) { case Success(ais) =>
      inside(bagStore1.add(testBagUnprunedB)) { case Success(bis) =>
        inside(bagStore1.add(testBagUnprunedC)) { case Success(cis) =>
          inside(bagStores.enumBags().map(_.toList)) {
            case Success(bagIds) => bagIds should (have size 3 and contain only(ais, bis, cis))
          }
        }
      }
    }
  }

  it should "return empty stream if BagStore is empty" in {
    bagStores.enumBags() should matchPattern {
      case Success(Seq()) =>
    }
  }

  it should "skip hidden Bags by default" in {
    inside(bagStore1.add(testBagUnprunedA)) { case Success(ais) =>
      inside(bagStore1.add(testBagUnprunedB)) { case Success(bis) =>
        inside(bagStore1.add(testBagUnprunedC)) { case Success(cis) =>
          bagStores.deactivate(bis) shouldBe a[Success[_]]

          inside(bagStores.enumBags().map(_.toList)) {
            case Success(bagIds) => bagIds should (have size 2 and contain only(ais, cis))
          }
        }
      }
    }
  }

  it should "include hidden Bags if requested" in {
    inside(bagStore1.add(testBagUnprunedA)) { case Success(ais) =>
      inside(bagStore1.add(testBagUnprunedB)) { case Success(bis) =>
        inside(bagStore1.add(testBagUnprunedC)) { case Success(cis) =>
          bagStores.deactivate(bis) shouldBe a[Success[_]]

          inside(bagStores.enumBags(includeInactive = true).map(_.toList)) {
            case Success(bagIds) => bagIds should (have size 3 and contain only(ais, bis, cis))
          }
        }
      }
    }
  }

  it should "skip visible Bags if requested" in {
    inside(bagStore1.add(testBagUnprunedA)) { case Success(_) =>
      inside(bagStore1.add(testBagUnprunedB)) { case Success(bis) =>
        inside(bagStore1.add(testBagUnprunedC)) { case Success(_) =>
          bagStores.deactivate(bis) shouldBe a[Success[_]]

          inside(bagStores.enumBags(includeActive = false, includeInactive = true).map(_.toList)) {
            case Success(bagIds) => bagIds should (have size 1 and contain only bis)
          }
        }
      }
    }
  }

  it should "skip all Bags if requested" in {
    inside(bagStore1.add(testBagUnprunedA)) { case Success(_) =>
      inside(bagStore1.add(testBagUnprunedB)) { case Success(bis) =>
        inside(bagStore1.add(testBagUnprunedC)) { case Success(_) =>
          bagStores.deactivate(bis) shouldBe a[Success[_]]

          inside(bagStores.enumBags(includeActive = false).map(_.toList)) {
            case Success(bagIds) => bagIds shouldBe empty
          }
        }
      }
    }
  }

  "enumFiles" should "return all FileIds in a valid Bag" in {
    inside(bagStore1.add(testBagUnprunedA)) { case Success(ais) =>
      inside(bagStores.enumFiles(ais, includeDirectories = false, None).map(_.toList)) { case Success(fileIds) =>
        fileIds.map(_.path.getFileName.toString) should {
          have size 10 and
            contain only("u", "v", "w", "x", "y", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "tagmanifest-md5.txt")
        }
      }
    }
  }

  it should "return all FileIds in a virtually-valid Bag" in {
    implicit val baseDir: BaseDir = bagStore1.baseDir
    inside(bagStore1.add(testBagUnprunedA)) { case Success(ais) =>
      bagProcessing.prune(testBagUnprunedB, ais :: Nil) shouldBe a[Success[_]]
      inside(bagStore1.add(testBagUnprunedB)) { case Success(bis) =>
        bagProcessing.prune(testBagUnprunedC, bis :: Nil) shouldBe a[Success[_]]
        inside(bagStore1.add(testBagUnprunedC)) { case Success(cis) =>
          inside(bagStores.enumFiles(cis, includeDirectories = false, None).map(_.toList)) {
            case Success(fileIds) => fileIds.map(_.path.getFileName.toString) should (have size 13 and
              contain only("q", "w", "u", "p", "x", "y", "y-old", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "tagmanifest-md5.txt", "fetch.txt"))
          }
        }
      }
    }
  }

  it should "fail if the bag was marked as inactive" in {
    implicit val baseDir: BaseDir = bagStore1.baseDir
    inside(bagStore1.add(testBagMinimal)) {
      case Success(bagId: BagId) =>
        bagStore1.deactivate(bagId) shouldBe a[Success[_]]
        bagStore1.enumFiles(bagId) should matchPattern {
          case Failure(InactiveException(`bagId`, false)) =>
        }
    }
  }

  it should "not if the bag was marked as inactive, when forceActive = true" in {
    implicit val baseDir: BaseDir = bagStore1.baseDir
    inside(bagStore1.add(testBagMinimal)) {
      case Success(bagId: BagId) =>
        bagStore1.deactivate(bagId) shouldBe a[Success[_]]
        bagStore1.enumFiles(bagId, forceInactive = true) shouldBe a[Success[_]]
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
    inside(bagStore1.add(testBagComplementary)) { case Success(complementary) =>
      inside(bagStores.enumFiles(complementary, includeDirectories = false, None).map(_.toList)) {
        case Success(fileIds) => fileIds.map(_.path.getFileName.toString) should (have size 11 and
          contain only("u", "v", "w", "x", "y", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "manifest-sha1.txt", "tagmanifest-md5.txt"))
      }
    }
  }

  "deactivate" should "be able to inactivate a Bag that is not yet inactive" in {
    implicit val baseDir: BaseDir = bagStore1.baseDir
    inside(bagStore1.add(testBagMinimal)) { case Success(bagId) =>
      val tryInactiveBagId = bagStores.deactivate(bagId)
      tryInactiveBagId shouldBe a[Success[_]]
      inside(fileSystem.toLocation(bagId)) { case Success(location) =>
        Files.isHidden(location) shouldBe true
      }
    }
  }

  it should "result in a Failure if Bag is already inactive" in {
    inside(bagStore1.add(testBagMinimal)) { case Success(bagId) =>
      bagStores.deactivate(bagId) shouldBe a[Success[_]]

      bagStores.deactivate(bagId) should matchPattern {
        case Failure(AlreadyInactiveException(_)) =>
      }
    }
  }

  "reactivate" should "be able to reactivate an inactive Bag" in {
    implicit val baseDir: BaseDir = store1
    inside(bagStore1.add(testBagMinimal)) { case Success(bagId) =>
      bagStores.deactivate(bagId) shouldBe a[Success[_]]
      inside(fileSystem.toLocation(bagId)) { case Success(location) =>
        Files.isHidden(location) shouldBe true
      }

      bagStores.reactivate(bagId) shouldBe a[Success[_]]
      inside(fileSystem.toLocation(bagId)) { case Success(location) =>
        Files.isHidden(location) shouldBe false
      }
    }
  }

  it should "result in a Failure if Bag is not marked as inactive" in {
    inside(bagStore1.add(testBagMinimal)) { case Success(bagId) =>
      bagStores.reactivate(bagId) should matchPattern {
        case Failure(NotInactiveException(_)) =>
      }
    }
  }

  "copyToStream" should "return a stream containing the content of the files" in {
    implicit val baseDir: BaseDir = store1
    val bagID = bagStore1.add(testBagMinimal).getOrElse(fail())
    val baos = new ByteArrayOutputStream()
    bagStores.copyToStream(bagID, Some(ArchiveStreamType.TAR), baos, Some(baseDir)) shouldBe a[Success[_]]
    val content = baos.toString
    content should include("9e5ad981e0d29adc278f6a294b8c2aca  bagit.txt")
    content should include("ae4573c51c28ac09546cd7fc55422ae4  manifest-md5.txt")
  }

  it should "fail if the bag is made inactive beforehand" in {
    implicit val baseDir: BaseDir = store1
    val bagID = bagStore1.add(testBagMinimal).getOrElse(fail())
    bagStores.deactivate(bagID) shouldBe a[Success[_]]
    val baos = new ByteArrayOutputStream()
    bagStores.copyToStream(bagID, Some(ArchiveStreamType.TAR), baos, Some(baseDir)) shouldBe Failure(InactiveException(bagID))
  }

  it should "return a stream containing the content of all the files if the bag is made inactive beforehand, but the forceInactive param is given" in {
    implicit val baseDir: BaseDir = store1
    val bagID = bagStore1.add(testBagMinimal).getOrElse(fail())
    bagStores.deactivate(bagID) shouldBe a[Success[_]]
    val baos = new ByteArrayOutputStream()
    bagStores.copyToStream(bagID, Some(ArchiveStreamType.TAR), baos, Some(baseDir), forceInactive = true) shouldBe a[Success[_]]
    val content = baos.toString
    content should include("9e5ad981e0d29adc278f6a294b8c2aca  bagit.txt")
    content should include("ae4573c51c28ac09546cd7fc55422ae4  manifest-md5.txt")
  }
}
