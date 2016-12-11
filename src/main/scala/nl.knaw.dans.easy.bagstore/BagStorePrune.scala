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

import java.nio.file.Path

import scala.util.Try

trait BagStorePrune extends BagStoreContext {

  /**
   * Takes a virtually-valid Bag and a list of bag-ids of reference Bags. The Bag is searched for files that are already in one
   * of the Reference Bags. These files are removed from the Bag and included from one of the reference Bags through a
   * fetch.txt entry. This way the Bag stays virtually-valid while possibly taking up less storage space.
   *
   * @param bagDir the Bag to prune
   * @param refBag the reference Bags to search
   * @return
   */
  def prune(bagDir: Path, refBag: BagId*): Try[Unit] = {




    ???
  }



}
