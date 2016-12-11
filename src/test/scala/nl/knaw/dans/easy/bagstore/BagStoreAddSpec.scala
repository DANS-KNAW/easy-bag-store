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

import scala.util._

class BagStoreAddSpec extends BagStoreFixture with BagStoreAdd {
  val TEST_BAGS_DIR = Paths.get("src/test/resources/bags")
  val TEST_BAG_MINIMAL = TEST_BAGS_DIR.resolve("minimal-bag")
  val TEST_BAG_INVALID = TEST_BAGS_DIR.resolve("incomplete-bag")
  val TEST_BAG_SSEQ_A = TEST_BAGS_DIR.resolve("basic-sequence/a")
  val TEST_BAG_SSEQ_B = TEST_BAGS_DIR.resolve("basic-sequence/b")
  val TEST_BAG_SSEQ_C = TEST_BAGS_DIR.resolve("basic-sequence/c")

  "add" should "result in exact copy (except for bag-info.txt) of bag in archive when bag is valid" in {
    val tryBagId = add(TEST_BAG_MINIMAL)
    tryBagId shouldBe a[Success[_]]
    val bagDirInStore = tryBagId.flatMap(toLocation).get
    pathsEqual(TEST_BAG_MINIMAL, bagDirInStore, excludeFiles = "bag-info.txt") shouldBe true
  }

  it should "result in a Failure if bag is invalid" in {
    add(TEST_BAG_INVALID) shouldBe a[Failure[_]]
  }

//  it should "change files present in ref-bags to fetch.txt entries" in {
//    val tryA = add(TEST_BAG_SSEQ_A)
//    tryA shouldBe a[Success[_]]
//    val tryB = add(TEST_BAG_SSEQ_B, Some(tryA.get.bagSequenceId))
//    tryB shouldBe a[Success[_]]
//    val tryC = add(TEST_BAG_SSEQ_C, Some(tryA.get.bagSequenceId))
//    tryC shouldBe a[Success[_]]
//
//    // Bags in store
//    val ais = tryA.map(toLocation).get
//    val bis = tryB.map(toLocation).get
//    val cis = tryC.map(toLocation).get
//
//    /*
//     * Now follow checks on the content of of the ingested bags. Each file should be EITHER actually present in the
//     * data-directory OR a reference the fetch.txt (never both). The comments are taken from
//     * src/test/resources/test-bags/simple-sequence/README.txt
//     */
//
//    /*
//     * Check bag A
//     */
//    pathsEqual(TEST_BAG_SSEQ_A, ais) shouldBe true
//
//    // Note about the regular expressions: the beginning and end of line symbols don't seem to work, so using work-around with \n? here.
//
//    /*
//     * Check bag B
//     */
//    val bFetchTxt = io.Source.fromFile(bis.resolve("fetch.txt").toFile).mkString
//
//    // data/sub/u      unchanged               => reference in fetch.txt
//    bFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/sub/u\\s+\\d+\\s+data/sub/u\n"""
//    bis.resolve("data/sub/u").toFile shouldNot exist
//
//    // [data/sub/v]    moved                   => not present here, no reference in fetch.txt
//    bFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/sub/v\n""")
//    bis.resolve("data/sub/v").toFile shouldNot exist
//
//    // [data/sub/w]    deleted                 => not present here, no reference in fetch.txt
//    bFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/sub/w\n""")
//    bis.resolve("data/sub/w").toFile shouldNot exist
//
//    // data/v          moved                   => reference in fetch.txt
//    bFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/sub/v\\s+\\d+\\s+data/v\n"""
//    bis.resolve("data/v").toFile shouldNot exist
//    bis.resolve("data/sub/v").toFile shouldNot exist
//
//    // data/x          unchanged               => reference in fetch.txt
//    bFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/x\\s+\\d+\\s+data/x\n"""
//    bis.resolve("data/x").toFile shouldNot exist
//
//    // data/y          changed                 => actual file
//    bFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/y\n""")
//    bis.resolve("data/y").toFile should exist
//    io.Source.fromFile(bis.resolve("data/y").toFile).mkString should include("content of y edited in b")
//
//    // data/y-old      copy of y               => reference in fetch.txt
//    bFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/y\\s+\\d+\\s+data/y-old\n"""
//    bis.resolve("data/y-old").toFile shouldNot exist
//
//    // [data/z]        deleted                 => not present, no reference in fetch.txt
//    bFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/z\n""")
//    bis.resolve("data/z").toFile shouldNot exist
//
//
//    /*
//     * Check bag C
//     */
//    val cFetchTxt = io.Source.fromFile(cis.resolve("fetch.txt").toFile).mkString
//
//    // data/sub/q      new file                => actual file
//    cFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/sub/q\n""")
//    cis.resolve("data/sub/q").toFile should exist
//
//    // data/sub/w      restored file from a
//    cFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/sub/w\\s+\\d+\\s+data/sub/w\n"""
//    cis.resolve("data/sub/w").toFile shouldNot exist
//
//    // data/sub-copy/u the same as in b        => reference in fetch.txt to a (copied from b's fetch.txt)
//    cFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/sub/u\\s+\\d+\\s+data/sub-copy/u\n"""
//    cis.resolve("data/sub-copy/u").toFile shouldNot exist
//
//    // data/p          new file                => actual file
//    cFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/p\n""")
//    cis.resolve("data/p").toFile should exist
//
//    // data/x          unchanged               => reference in fetch.txt to a
//    cFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/x\\s+\\d+\\s+data/x\n"""
//    cis.resolve("data/x").toFile shouldNot exist
//
//    // data/y          unchanged               => reference in fetch.txt to b
//    cFetchTxt should include regex s"""\n?$baseUri.*\\.2/data/y\\s+\\d+\\s+data/y\n"""
//    cis.resolve("data/y").toFile shouldNot exist
//
//    // data/y-old      unchanged               => reference in fetch.txt to a
//    cFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/y\\s+\\d+\\s+data/y-old\n"""
//    cis.resolve("data/y-old").toFile shouldNot exist
//
//    // [data/v]        deleted                 => not present here, no reference in fetch.txt
//    cFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/v\n""")
//    cis.resolve("data/v").toFile shouldNot exist
//
//    // data/z          unchanged               => reference in fetch.txt to a
//    cFetchTxt should include regex s"""\n?$baseUri.*\\.1/data/z\\s+\\d+\\s+data/z\n"""
//    cis.resolve("data/z").toFile shouldNot exist
//  }
}
