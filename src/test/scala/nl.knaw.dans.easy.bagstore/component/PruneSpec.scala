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

import java.util.UUID

import nl.knaw.dans.easy.bagstore.BagId

import scala.util.Failure

class PruneSpec extends BagProcessingFixture {

  "prune" should "report the missing referenced bag" in {
    val uuid = UUID.randomUUID()
    bagProcessing.prune(
      testDir.resolve("bag"), // bag to prune which does not exist
      Seq(BagId(uuid)) // single referenced bag that doesn't exist
    )(store1) should matchPattern {
      case Failure(e)
        if e.getMessage.contains("NoSuchFileException")
          && e.getMessage.contains(pathFromUUID(uuid))
      =>
    }
  }

  it should "report all missing referenced bags" in {
    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()
    bagProcessing.prune(
      testDir.resolve("bag"), // bag to prune which does not exist
      Seq(BagId(uuid1), BagId(uuid2)) // multiple referenced bags that don't exist
    )(store1) should matchPattern {
      case Failure(e) // TODO "NoSuchFileException" is lost
        if e.getMessage.contains("START OF EXCEPTION LIST")
          && e.getMessage.contains(pathFromUUID(uuid1))
          && e.getMessage.contains(pathFromUUID(uuid2))
      =>
    }
  }

  private def pathFromUUID(randomUUUID: UUID) = {
    randomUUUID.toString
      .replaceAll("-", "")
      .replaceAll("^(..)", "$1/")
  }
}
