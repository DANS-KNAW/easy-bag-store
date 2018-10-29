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

import java.io.ByteArrayInputStream
import java.nio.file.Paths
import java.util.UUID

import better.files.File
import nl.knaw.dans.bag.v0.DansV0Bag
import nl.knaw.dans.easy.bagstore.BagId
import nl.knaw.dans.lib.error._

import scala.util.{ Failure, Success }

class PruneSpec extends BagProcessingFixture {

  implicit val referencedStore: BagPath = store1

  "prune" should "not prune when a file in one is a folder in the other and vice versa" in {
    val uuid = UUID.randomUUID()
    DansV0Bag.empty(File(store1) / pathFromUUID(uuid) / "bag").getOrRecover(fail(_))
      .addPayloadFile(toStream("lorum"), Paths.get("some.x")).getOrRecover(fail(_))
      .addPayloadFile(toStream("ipsum"), Paths.get("some.y/test.txt")).getOrRecover(fail(_))
      .save()
    val bagDir = File(testDir) / "bag"
    DansV0Bag.empty(bagDir).getOrRecover(fail(_))
      .addPayloadFile(toStream("doler"), Paths.get("some.x/some.txt")).getOrRecover(fail(_))
      .addPayloadFile(toStream("sit amet"), Paths.get("some.y")).getOrRecover(fail(_))
      .save()

    bagProcessing.prune(
      testDir.resolve("bag"),
      Seq(BagId(uuid))
    ) shouldBe Success(())
    (bagDir / "data").list.size shouldBe 2
    (bagDir / "fetch.txt") shouldNot exist
  }

  it should "report an invalid referenced bag" in {
    val uuid = UUID.randomUUID()
    (File(store1) / pathFromUUID(uuid) / "bag").createDirectories()
    bagProcessing.prune(
      testDir.resolve("bag"), // bag to prune which does not exist
      Seq(BagId(uuid)) // single referenced bag
    ) should matchPattern {
      case Failure(e) if containsFragments(e, Seq("The bag at", pathFromUUID(uuid), "could not be read")) =>
    }
  }

  it should "report a missing referenced bag" in {
    val uuid = UUID.randomUUID()
    bagProcessing.prune(
      testDir.resolve("bag"), // bag to prune which does not exist
      Seq(BagId(uuid)) // single referenced bag
    ) should matchPattern {
      case Failure(e) if containsFragments(e, Seq("NoSuchFileException", pathFromUUID(uuid))) =>
    }
  }

  it should "report all missing referenced bags" in {
    val referencedBags = (0 until 2).map(_ => BagId(UUID.randomUUID()))
    val msgFragments = "START OF EXCEPTION LIST" +: referencedBags.map(bagId => pathFromUUID(bagId.uuid))
    bagProcessing.prune(
      testDir.resolve("bag"), // bag to prune which does not exist
      referencedBags // they don't exist
    ) should matchPattern { // TODO "NoSuchFileException" is lost
      case Failure(e) if containsFragments(e, msgFragments) =>
    }
  }

  // TODO see https://github.com/DANS-KNAW/easy-deposit-api/blob/1f742c812d5a98754b010d32abdc282db5d256c3/src/main/scala/nl.knaw.dans.easy.deposit/package.scala#L73-L77
  def toStream(s: String): ByteArrayInputStream = new ByteArrayInputStream(s.getBytes())

  private def containsFragments(e: Throwable, msgFragments: Seq[String]): Boolean = {
    // TODO implicit class or custom matcher
    val message = e.getMessage
    msgFragments.forall(message.contains)
  }

  private def pathFromUUID(randomUUUID: UUID): String = {
    // TODO this more or less mimics FileSystemComponent.toContainer
    randomUUUID.toString
      .replaceAll("-", "")
      .replaceAll("^(..)", "$1/")
  }
}
