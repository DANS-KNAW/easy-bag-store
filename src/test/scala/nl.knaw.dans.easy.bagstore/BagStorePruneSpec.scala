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

import org.apache.commons.io.FileUtils

import scala.util.Success

class BagStorePruneSpec extends BagStoreFixture with BagStorePrune with BagStoreAdd {
  FileUtils.copyDirectory(Paths.get("src/test/resources/bags/basic-sequence-unpruned").toFile, testDir.toFile)
  private val TEST_BAG_A = testDir.resolve("a")
  private val TEST_BAG_B = testDir.resolve("b")
  private val TEST_BAG_C = testDir.resolve("c")

  "prune" should "change files present in ref-bags to fetch.txt entries" in {
    val tryA = add(TEST_BAG_A)
    tryA shouldBe a[Success[_]]
    prune(TEST_BAG_B, tryA.get) shouldBe a[Success[_]]

    /*
     * Now follow checks on the content of of the ingested bags. Each file should be EITHER actually present in the
     * data-directory OR a reference the fetch.txt (never both). The comments are taken from
     * src/test/resources/bags/basic-sequence-unpruned/README.txt
     */

    // Note about the regular expressions: the beginning and end of line symbols don't seem to work, so using work-around with \n? here.

    /*
     * Check bag B
     */
    val bFetchTxt = io.Source.fromFile(TEST_BAG_B.resolve("fetch.txt").toFile).mkString
    val uuidForA = tryA.get.uuid

    // data/sub/u      unchanged               => reference in fetch.txt
    bFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/sub/u\\s+\\d+\\s+data/sub/u\n"""
    TEST_BAG_B.resolve("data/sub/u").toFile shouldNot exist

    // [data/sub/v]    moved                   => not present here, no reference in fetch.txt
    bFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/sub/v\n""")
    TEST_BAG_B.resolve("data/sub/v").toFile shouldNot exist

    // [data/sub/w]    deleted                 => not present here, no reference in fetch.txt
    bFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/sub/w\n""")
    TEST_BAG_B.resolve("data/sub/w").toFile shouldNot exist

    // data/v          moved                   => reference in fetch.txt
    bFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/sub/v\\s+\\d+\\s+data/v\n"""
    TEST_BAG_B.resolve("data/v").toFile shouldNot exist
    TEST_BAG_B.resolve("data/sub/v").toFile shouldNot exist

    // data/x          unchanged               => reference in fetch.txt
    bFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/x\\s+\\d+\\s+data/x\n"""
    TEST_BAG_B.resolve("data/x").toFile shouldNot exist

    // data/y          changed                 => actual file
    bFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/y\n""")
    TEST_BAG_B.resolve("data/y").toFile should exist
    io.Source.fromFile(TEST_BAG_B.resolve("data/y").toFile).mkString should include("content of y edited in b")

    // data/y-old      copy of y               => reference in fetch.txt
    bFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/y\\s+\\d+\\s+data/y-old\n"""
    TEST_BAG_B.resolve("data/y-old").toFile shouldNot exist

    // [data/z]        deleted                 => not present, no reference in fetch.txt
    bFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/z\n""")
    TEST_BAG_B.resolve("data/z").toFile shouldNot exist

    /*
     * Adding the now pruned Bag B so that C may reference it
     */
    val tryB = add(TEST_BAG_B)
    tryB shouldBe a[Success[_]]

    prune(TEST_BAG_C, tryA.get, tryB.get)

    /*
     * Check bag C
     */
    val cFetchTxt = io.Source.fromFile(TEST_BAG_C.resolve("fetch.txt").toFile).mkString
    val uuidForB = tryB.get.uuid

    // data/sub/q      new file                => actual file
    cFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/sub/q\n""")
    TEST_BAG_C.resolve("data/sub/q").toFile should exist

    // data/sub/w      restored file from a
    cFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/sub/w\\s+\\d+\\s+data/sub/w\n"""
    TEST_BAG_C.resolve("data/sub/w").toFile shouldNot exist

    // data/sub-copy/u the same as in b        => reference in fetch.txt to a (copied from b's fetch.txt)
    cFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/sub/u\\s+\\d+\\s+data/sub-copy/u\n"""
    TEST_BAG_C.resolve("data/sub-copy/u").toFile shouldNot exist

    // data/p          new file                => actual file
    cFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/p\n""")
    TEST_BAG_C.resolve("data/p").toFile should exist

    // data/x          unchanged               => reference in fetch.txt to a
    cFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/x\\s+\\d+\\s+data/x\n"""
    TEST_BAG_C.resolve("data/x").toFile shouldNot exist

    // data/y          unchanged               => reference in fetch.txt to b
    cFetchTxt should include regex s"""\n?$baseUri/$uuidForB/data/y\\s+\\d+\\s+data/y\n"""
    TEST_BAG_C.resolve("data/y").toFile shouldNot exist

    // data/y-old      unchanged               => reference in fetch.txt to a
    cFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/y\\s+\\d+\\s+data/y-old\n"""
    TEST_BAG_C.resolve("data/y-old").toFile shouldNot exist

    // [data/v]        deleted                 => not present here, no reference in fetch.txt
    cFetchTxt should not include regex(s"""\n?$baseUri.*\\s+\\d+\\s+data/v\n""")
    TEST_BAG_C.resolve("data/v").toFile shouldNot exist

    // data/z          unchanged               => reference in fetch.txt to a
    cFetchTxt should include regex s"""\n?$baseUri/$uuidForA/data/z\\s+\\d+\\s+data/z\n"""
    TEST_BAG_C.resolve("data/z").toFile shouldNot exist
  }
}
