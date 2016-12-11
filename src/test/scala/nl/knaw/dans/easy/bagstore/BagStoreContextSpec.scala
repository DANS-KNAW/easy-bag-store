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

import java.net.URI
import java.nio.file.{Path, Paths}
import java.util.UUID

import org.apache.commons.io.FileUtils
import org.scalatest.Inside.inside

import scala.util.{Failure, Success}

class BagStoreContextSpec extends BagStoreFixture with BagStoreContext {
  FileUtils.copyDirectory(Paths.get("src/test/resources/bag-store").toFile, baseDir.toFile)

  "fromLocation" should "return a Failure for an empty path" in {
    fromLocation(Paths.get("")) shouldBe a[Failure[_]]
  }

  it should "return a bag-id for valid UUID-path with correct slash positions under base-dir" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(2)
    val uuidPath = Paths.get(parentDir, childDir)
    val id = fromLocation(baseDir.toAbsolutePath.resolve(uuidPath))
    id shouldBe a[Success[_]]
    inside(id) {
      case Success(BagId(foundUuid)) => foundUuid shouldBe uuid
    }
  }

  it should "return a Failure if slashes are in incorrect positions" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(3)
    val uuidPath = Paths.get(parentDir, childDir)
    fromLocation(baseDir.toAbsolutePath.resolve(uuidPath)) shouldBe a[Failure[_]]
  }

  it should "return a bag-id even if there are many slashes" in {
    new BagStoreContext {
      override implicit val baseDir: Path = BagStoreContextSpec.this.baseDir
      override implicit val baseUri: URI = BagStoreContextSpec.this.baseUri
      override implicit val uuidPathComponentSizes: Seq[Int] = Seq.fill(32)(1)

      val uuid = UUID.randomUUID()
      val dirs = uuid.toString.filterNot(_ == '-').grouped(1).toList
      val uuidPath = Paths.get(dirs.head, dirs.tail: _*)
      val id = fromLocation(baseDir.toAbsolutePath.resolve(uuidPath))
      id shouldBe a[Success[_]]
      inside(id) {
        case Success(BagId(foundUuid)) => foundUuid shouldBe uuid
      }
    }
  }

  it should "return a bag-id for uuid-path/bag-name even if bag-name does not exists" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(2)
    val bagPath = Paths.get(parentDir, childDir, 1.toString, "")
    val id = fromLocation(baseDir.toAbsolutePath.resolve(bagPath))
    id shouldBe a[Success[_]]
    inside(id) {
      case Success(BagId(foundUuid)) =>
        foundUuid shouldBe uuid
    }
  }

  it should "return a file-id for uuid-path/bag-name/filename" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(2)
    val bagPath = Paths.get(parentDir, childDir, "bag-name", "filename")
    val id = fromLocation(baseDir.toAbsolutePath.resolve(bagPath))
    id shouldBe a[Success[_]]
    inside(id) {
      case Success(FileId(BagId(foundUuid), filepath)) =>
        foundUuid shouldBe uuid
        filepath shouldBe Paths.get("filename")
    }

  }

  it should "return a file-id for uuid-path/bag-name/a/longer/path" in {
    val uuid = UUID.randomUUID()
    val (parentDir, childDir) = uuid.toString.filterNot(_ == '-').splitAt(2)
    val bagPath = Paths.get(parentDir, childDir, "bag-name", "a", "longer", "path")
    val id = fromLocation(baseDir.toAbsolutePath.resolve(bagPath))
    id shouldBe a[Success[_]]
    inside(id) {
      case Success(FileId(BagId(foundUuid), filepath)) =>
        foundUuid shouldBe uuid
        filepath shouldBe Paths.get("a", "longer", "path")
    }
  }

  "toLocation" should "return location of bag when given a bag-id"  in {
    val bagId = BagId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    val result = toLocation(bagId)
    result shouldBe a[Success[_]]
    inside(result) {
      case Success(p) => p shouldBe baseDir.resolve("00/000000000000000000000000000001/bag-revision-1")
    }
  }

  it should "return a failure if two bags found in container" in {
    val bagId = BagId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
    val result = toLocation(bagId)
    result shouldBe a[Failure[_]]
    inside(result) {
      case Failure(e) => e.getMessage should include("Corrupt BagStore")
    }
  }

  it should "return location of a file when given a file-id" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000001"), Paths.get("data/x"))
    val result = toLocation(fileId)
    result shouldBe a[Success[_]]
    inside(result) {
      case Success(p) => p shouldBe baseDir.resolve("00/000000000000000000000000000001/bag-revision-1/data/x")
    }
  }

  it should "return location of a file when given a file-id, even if it does not exist, as long as bag base does exist" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000001"), Paths.get("non-existent/file"))
    val result = toLocation(fileId)
    result shouldBe a[Success[_]]
    inside(result) {
      case Success(p) => p shouldBe baseDir.resolve("00/000000000000000000000000000001/bag-revision-1/non-existent/file")
    }
  }

  "fromUri" should "return a Failure for a URI that is not under the base-uri" in {
    val uuid = UUID.randomUUID()
    val fail = fromUri(new URI(s"http://some-other-base/$uuid"))
    inside(fail) {
      case Failure(e) =>
        e shouldBe a[NoItemUriException]
    }
  }

  it should "return a bag-id for valid UUID-path after the base-uri" in {
    val uuid = UUID.randomUUID()
    val id = fromUri(new URI(s"$baseUri/$uuid"))
    id shouldBe a[Success[_]]
    inside(id) {
      case Success(BagId(foundUuid)) => foundUuid shouldBe uuid
    }
  }

  it should "return a bag-id for valid UUID-path after the base-uri even if base-uri contains part of path" in {
    new BagStoreContext {
      override implicit val baseDir: Path = BagStoreContextSpec.this.baseDir
      override implicit val baseUri: URI = new URI("http://example-archive.org/base-path/")
      override implicit val uuidPathComponentSizes: Seq[Int] = Seq.fill(32)(1)

      val uuid = UUID.randomUUID()
      val id = fromUri(new URI(s"$baseUri/$uuid"))
      id shouldBe a[Success[_]]
      inside(id) {
        case Success(BagId(foundUuid)) => foundUuid shouldBe uuid
      }
    }
  }

  it should "return a file-id for base-uri/uuid/ even though it can never resolve to a file" in {
    val uuid = UUID.randomUUID()
    val id = fromUri(new URI(s"$baseUri/$uuid/"))
    id shouldBe a[Success[_]]
    inside(id) {
      case Success(FileId(BagId(foundUuid), path)) =>
        foundUuid shouldBe uuid
        path shouldBe Paths.get("")
    }
  }

  it should "return a file-id for base-uri/uuid/filename" in {
    val uuid = UUID.randomUUID()
    val id = fromUri(new URI(s"$baseUri/$uuid/filename"))
    id shouldBe a[Success[_]]
    inside(id) {
      case Success(FileId(BagId(foundUuid), filepath)) =>
        foundUuid shouldBe uuid
        filepath shouldBe Paths.get("filename")
    }
  }

  it should "return a file-id for base-uri/uuid/a/longer/path" in {
    val uuid = UUID.randomUUID()
    val id = fromUri(new URI(s"$baseUri/$uuid/a/longer/path"))
    id shouldBe a[Success[_]]

    inside(id) {
      case Success(FileId(BagId(foundUuid), filepath)) =>
        foundUuid shouldBe uuid
        filepath shouldBe Paths.get("a", "longer", "path")
    }
  }

  it should "return a Failure if only the base-uri is passed" in {
    val fail = fromUri(baseUri)
    fail shouldBe a[Failure[_]]
    inside(fail) {
      case Failure(e) => e shouldBe a[IncompleteItemUriException]
    }
  }
//
//  "fromUri followed by toUri" should "be no-op" in {
//    val uuid = UUID.randomUUID()
//    val rev = 42
//    val uri = baseUri.resolve(s"$uuid.$rev/some/path")
//    val id = fromUri(uri)
//    id shouldBe a[Success[_]]
//    inside(id) {
//      case Success(id) => toUri(id) shouldBe uri
//    }
//  }
//
//  "fromLocation followed by toLocation" should "be no-op" in {
//    val (parentDir, childDir) = UUID.randomUUID().toString.filterNot(_ == '-').splitAt(2)
//    val rev = 42
//    val path = baseDir.resolve(s"$parentDir/$childDir/$rev/some/path")
//    val id = fromLocation(path)
//    id shouldBe a[Success[_]]
//    inside(id) {
//      case Success(id) => toLocation(id) shouldBe path
//    }
//
//  }
//
//  "getFetchPath" should "return Failure for path not in bag-store" in {
//    getFetchPath(baseDir.getParent) shouldBe a[Failure[_]]
//  }
//
//  it should "return a bag-sequence-path unchanged" in {
//    val orgPath = baseDir.resolve("10/53546d80e14c27bccfdd839c8d16d9")
//    val tryPath = getFetchPath(orgPath)
//    tryPath shouldBe a[Success[_]]
//    inside(tryPath) {
//      case Success(path) => path shouldBe orgPath
//    }
//  }
//
//  it should "return a bag-path unchanged" in {
//    val orgPath = baseDir.resolve("10/53546d80e14c27bccfdd839c8d16d9/1")
//    val tryPath = getFetchPath(orgPath)
//    tryPath shouldBe a[Success[_]]
//    inside(tryPath) {
//      case Success(path) => path shouldBe orgPath
//    }
//  }
//
//  it should "return a file-path that exists unchanged" in {
//    val orgPath = baseDir.resolve("10/53546d80e14c27bccfdd839c8d16d9/1/data/x")
//    val tryPath = getFetchPath(orgPath)
//    tryPath shouldBe a[Success[_]]
//    inside(tryPath) {
//      case Success(path) => path shouldBe orgPath
//    }
//  }
//
//  it should "return the actual path for a file-path that would be created during fetching" in {
//    val orgPath = baseDir.resolve("10/53546d80e14c27bccfdd839c8d16d9/2/data/v")
//    val tryPath = getFetchPath(orgPath)
//    tryPath shouldBe a[Success[_]]
//    inside(tryPath) {
//      case Success(path) =>
//        path shouldNot be(orgPath)
//        path shouldBe baseDir.resolve(Paths.get("10/53546d80e14c27bccfdd839c8d16d9/1/data/sub/v"))
//    }
//  }
//

  "toRealLocation" should "resolve to the location pointed to in the fetch.txt" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000003"), Paths.get("data/sub-copy/u"))
    val result = toRealLocation(fileId)
    result shouldBe a[Success[_]]
    inside(result) {
      case Success(path) => path shouldBe baseDir.resolve("00/000000000000000000000000000001/bag-revision-1/data/sub/u")
    }
  }

  it should "resolve to real location if fetch references other fetch reference" in {
    val fileId = FileId(UUID.fromString("00000000-0000-0000-0000-000000000003"), Paths.get("data/x"))
    val result = toRealLocation(fileId)
    result shouldBe a[Success[_]]
    inside(result) {
      case Success(path) => path shouldBe baseDir.resolve("00/000000000000000000000000000001/bag-revision-1/data/x")
    }
  }

  "isVirtuallyValid" should "return true for a valid bag" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bags/valid-bag").toFile, testDir.resolve("valid-bag").toFile)
    val result = isVirtuallyValid(testDir.resolve("valid-bag"))
    result shouldBe a[Success[_]]
    inside(result) {
      case Success(valid) => valid shouldBe true
    }
  }

  it should "return true for a virtually-valid bag" in {
    FileUtils.copyDirectory(Paths.get("src/test/resources/bags/virtually-valid-bag").toFile, testDir.resolve("virtually-valid-bag").toFile)
    val result = isVirtuallyValid(testDir.resolve("virtually-valid-bag"))
    result shouldBe a[Success[_]]
    inside(result) {
      case Success(valid) => valid shouldBe true
    }

  }

}
