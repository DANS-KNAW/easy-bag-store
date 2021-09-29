/*
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
import java.nio.file.Paths
import java.nio.file.attribute.{ PosixFilePermission, PosixFilePermissions }
import java.util.UUID

import nl.knaw.dans.easy.bagstore._
import org.apache.commons.io.FileUtils

import scala.util.{ Failure, Success }

class FileSystemSpec extends TestSupportFixture
  with BagStoreFixture
  with BagitFixture
  with FileSystemComponent {

  override def beforeEach(): Unit = {
    super.beforeEach()
    FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, store1.toFile)
  }

  override val fileSystem: FileSystem = new FileSystem {
    override val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
    override val bagFilePermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
    override val bagDirPermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
    override val localBaseUri: URI = new URI("http://localhost")
  }

  implicit val baseDir: BaseDir = store1

  "fromLocation" should "return a Failure for an empty path" in {
    fileSystem.fromLocation(Paths.get("")) shouldBe a[Failure[_]]
  }

  it should "return a bag-id for valid UUID-path with correct slash positions under base-dir" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(2)
    val uuidPath = Paths.get(parentDir, childDir)

    fileSystem.fromLocation(store1.toAbsolutePath.resolve(uuidPath)) should matchPattern {
      case Success(BagId(`uuid`)) =>
    }
  }

  it should "return a Failure if slashes are in incorrect positions" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(3)
    val uuidPath = Paths.get(parentDir, childDir)

    fileSystem.fromLocation(store1.toAbsolutePath.resolve(uuidPath)) shouldBe a[Failure[_]]
  }

  it should "return a bag-id even if there are many slashes" in {
    val otherFS = new FileSystem {
      override val uuidPathComponentSizes: Seq[Int] = Seq.fill(32)(1)
      override val bagFilePermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
      override val bagDirPermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
      override val localBaseUri: URI = new URI("http://localhost")
    }

    val uuid = UUID.randomUUID()
    val dirs = uuid.toString.filterNot(_ == '-').grouped(1).toList
    val uuidPath = Paths.get(dirs.head, dirs.tail: _*)

    otherFS.fromLocation(store1.toAbsolutePath.resolve(uuidPath)) should matchPattern {
      case Success(BagId(`uuid`)) =>
    }
  }

  it should "return a bag-id for uuid-path/bag-name even if bag-name does not exists" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(2)
    val bagPath = Paths.get(parentDir, childDir, 1.toString, "")

    fileSystem.fromLocation(store1.toAbsolutePath.resolve(bagPath)) should matchPattern {
      case Success(BagId(`uuid`)) =>
    }
  }

  it should "return a file-id for uuid-path/bag-name/filename" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(2)
    val bagPath = Paths.get(parentDir, childDir, "bag-name", "filename")

    inside(fileSystem.fromLocation(store1.toAbsolutePath.resolve(bagPath))) {
      case Success(FileId(BagId(foundUuid), filepath, _)) =>
        foundUuid shouldBe uuid
        filepath shouldBe Paths.get("filename")
    }
  }

  it should "return a file-id for uuid-path/bag-name/a/longer/path" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(2)
    val bagPath = Paths.get(parentDir, childDir, "bag-name", "a", "longer", "path")

    inside(fileSystem.fromLocation(store1.toAbsolutePath.resolve(bagPath))) {
      case Success(FileId(BagId(foundUuid), filepath, _)) =>
        foundUuid shouldBe uuid
        filepath shouldBe Paths.get("a", "longer", "path")
    }
  }

  private val localBaseUri = fileSystem.localBaseUri

  "fromUri" should "return a Failure for a URI that is not under the base-uri" in {
    val uuid = UUID.randomUUID()

    val uri = new URI(s"http://some-other-base/$uuid")
    fileSystem.fromUri(uri) should matchPattern { case Failure(NoItemUriException(`uri`, `localBaseUri`)) => }
  }

  it should "return a failure with invalid UUID" in {
    val uri = new URI(s"$localBaseUri/abc")
    fileSystem.fromUri(uri) should matchPattern { case Failure(e: IllegalArgumentException) if e.getMessage == "String 'abc----' is not a UUID" => }
  }

  it should "return a bag-id for valid UUID-path after the base-uri" in {
    val uuid = UUID.randomUUID()

    fileSystem.fromUri(new URI(s"$localBaseUri/$uuid")) should matchPattern {
      case Success(BagId(`uuid`)) =>
    }
  }

  it should "return a bag-id for valid UUID-path after the base-uri even if base-uri contains part of path" in {
    val otherFS = new FileSystem {
      override val uuidPathComponentSizes: Seq[Int] = Seq.fill(32)(1)
      override val bagFilePermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
      override val bagDirPermissions: java.util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxr-xr-x")
      override val localBaseUri: URI = new URI("http://localhost")
    }

    val uuid = UUID.randomUUID()

    otherFS.fromUri(new URI(s"$localBaseUri/$uuid")) should matchPattern {
      case Success(BagId(`uuid`)) =>
    }
  }

  it should "return a file-id for base-uri/uuid/ even though it can never resolve to a file" in {
    val uuid = UUID.randomUUID()

    inside(fileSystem.fromUri(new URI(s"$localBaseUri/$uuid/"))) {
      case Success(FileId(BagId(foundUuid), path, _)) =>
        foundUuid shouldBe uuid
        path shouldBe Paths.get("")
    }
  }

  it should "return a file-id for base-uri/uuid/filename" in {
    val uuid = UUID.randomUUID()

    inside(fileSystem.fromUri(new URI(s"$localBaseUri/$uuid/filename"))) {
      case Success(FileId(BagId(foundUuid), filepath, _)) =>
        foundUuid shouldBe uuid
        filepath shouldBe Paths.get("filename")
    }
  }

  it should "return a file-id for base-uri/uuid/a/longer/path" in {
    val uuid = UUID.randomUUID()

    inside(fileSystem.fromUri(new URI(s"$localBaseUri/$uuid/a/longer/path"))) {
      case Success(FileId(BagId(foundUuid), filepath, _)) =>
        foundUuid shouldBe uuid
        filepath shouldBe Paths.get("a", "longer", "path")
    }
  }

  it should "return a Failure if only the base-uri is passed" in {
    fileSystem.fromUri(localBaseUri) should matchPattern { case Failure(IncompleteItemUriException(_)) => }
  }

  "toLocation" should "return location of bag when given a bag-id" in {
    val bagId = BagId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    inside(fileSystem.toLocation(bagId)) {
      case Success(p) => p shouldBe store1.resolve("00/000000000000000000000000000001/bag-revision-1")
    }
  }

  it should "return a failure if two bags found in container" in {
    val bagId = BagId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))

    inside(fileSystem.toLocation(bagId)) {
      case Failure(e) => e.getMessage should include("Corrupt BagStore")
    }
  }

  it should "return location of a file when given a file-id" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000001"), Paths.get("data/x"))

    inside(fileSystem.toLocation(fileId)) {
      case Success(p) => p shouldBe store1.resolve("00/000000000000000000000000000001/bag-revision-1/data/x")
    }
  }

  it should "return location of a file when given a file-id, even if it does not exist, as long as bag base does exist" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000001"), Paths.get("non-existent/file"))

    inside(fileSystem.toLocation(fileId)) {
      case Success(p) => p shouldBe store1.resolve("00/000000000000000000000000000001/bag-revision-1/non-existent/file")
    }
  }

  "toRealLocation" should "resolve to the location pointed to in the fetch.txt" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000003"), Paths.get("data/sub-copy/u"))

    inside(fileSystem.toRealLocation(fileId)) {
      case Success(path) => path shouldBe store1.resolve("00/000000000000000000000000000001/bag-revision-1/data/sub/u")
    }
  }

  it should "resolve to real location if fetch references other fetch reference" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000003"), Paths.get("data/x"))

    inside(fileSystem.toRealLocation(fileId)) {
      case Success(path) => path shouldBe store1.resolve("00/000000000000000000000000000001/bag-revision-1/data/x")
    }
  }

  it should "fail if the file does not exist" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000003"), Paths.get("data/not-existing-file"))

    fileSystem.toRealLocation(fileId) should matchPattern { case Failure(NoSuchFileItemException(`fileId`)) => }
  }

  "isVirtuallyValid" should "return true for a valid bag" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bags/valid-bag").toFile, testDir.resolve("valid-bag").toFile)

    fileSystem.isVirtuallyValid(testDir.resolve("valid-bag")) should matchPattern { case Success(Right(())) => }
  }

  it should "return true for a virtually-valid bag" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bags/virtually-valid-bag").toFile, testDir.resolve("virtually-valid-bag").toFile)

    fileSystem.isVirtuallyValid(testDir.resolve("virtually-valid-bag")) should matchPattern { case Success(Right(())) => }
  }
}
