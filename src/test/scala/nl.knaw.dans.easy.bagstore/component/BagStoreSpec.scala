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

import java.net.URI
import java.nio.file.{ Path, Paths }
import java.util.UUID

import nl.knaw.dans.easy.bagstore._
import org.apache.commons.io.FileUtils

import scala.util.{ Failure, Success }

class BagStoreSpec extends TestSupportFixture
  with BagStoreFixture
  with BagitFixture
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/minimal-bag").toFile,
    testDir.resolve("minimal-bag").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/incomplete-bag").toFile,
    testDir.resolve("incomplete-bag").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-pruned").toFile,
    testDir.resolve("basic-sequence-pruned").toFile)
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-unpruned-with-refbags").toFile,
    testDir.resolve("basic-sequence-unpruned-with-refbags").toFile)

  private val testBagMinimal = testDir.resolve("minimal-bag")
  private val testBagIncomplete = testDir.resolve("incomplete-bag")
  private val testBagPrunedA = testDir.resolve("basic-sequence-pruned/a")
  private val testBagPrunedB = testDir.resolve("basic-sequence-pruned/b")
  private val testBagPrunedC = testDir.resolve("basic-sequence-pruned/c")
  private val testBagWithRefsA = testDir.resolve("basic-sequence-unpruned-with-refbags/a")
  private val testBagWithRefsB = testDir.resolve("basic-sequence-unpruned-with-refbags/b")
  private val testBagWithRefsC = testDir.resolve("basic-sequence-unpruned-with-refbags/c")

  override val fileSystem = new FileSystem {
    override val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
    override val bagPermissions: String = "rwxr-xr-x"
    override val localBaseUri: URI = new URI("http://example-archive.org")
  }

  override val processor = new BagProcessing {
    override val stagingBaseDir: BagPath = testDir
    override val outputBagPermissions: String = "rwxr-xr-x"
  }

  private val bagStore = new BagStore {
    override implicit val baseDir: BaseDir = store1
  }

  implicit val baseDir: BaseDir = bagStore.baseDir

  def testSuccessfulAdd(path: Path, uuid: UUID): Unit = {
    inside(bagStore.add(path, Some(uuid))) {
      case Success(bagId) => bagId.uuid shouldBe uuid
    }
  }

  "add" should "result in exact copy (except for bag-info.txt) of bag in archive when bag is valid" in {
    inside(bagStore.add(testBagMinimal)) { case Success(bagId) =>
      inside(fileSystem.toLocation(bagId)) { case Success(bagDirInStore) =>
        pathsEqual(testBagMinimal, bagDirInStore, excludeFiles = "bag-info.txt") shouldBe true
      }
    }
  }

  it should "result in a Failure if bag is incomplete" in {
    bagStore.add(testBagIncomplete) shouldBe a[Failure[_]]
  }

  it should "accept virtually-valid bags" in {
    val uuid1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val uuid3 = UUID.fromString("00000000-0000-0000-0000-000000000003")

    testSuccessfulAdd(testBagPrunedA, uuid1)

    /*
     * B and C are virtually-valid.
     */
    testSuccessfulAdd(testBagPrunedB, uuid2)
    testSuccessfulAdd(testBagPrunedC, uuid3)
  }

  it should "refuse to ingest hidden bag directories" in {
    val hiddenBagDir = testDir.resolve(".some-hidden-bag")
    FileUtils.copyDirectory(testBagMinimal.toFile, hiddenBagDir.toFile)
    val result = bagStore.add(hiddenBagDir)
    inside(result) {
      case Failure(e) => e shouldBe a[CannotIngestHiddenBagDirectoryException]
    }
  }

  it should "first prune a bag if a refbags file is present" in {
    val uuid1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val uuid2 = UUID.fromString("11111111-1111-1111-1111-111111111112")
    val uuid3 = UUID.fromString("11111111-1111-1111-1111-111111111113")

    testSuccessfulAdd(testBagWithRefsA, uuid1)

    /*
     * B and C are pruned before they are added.
     */
    testSuccessfulAdd(testBagWithRefsB, uuid2)
    testSuccessfulAdd(testBagWithRefsC, uuid3)
  }
}
