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
package nl.knaw.dans.easy.bagstore
import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.Paths
import java.util.zip.Deflater

import better.files._


object TarBag extends App {
    implicit val encoding: Charset = StandardCharsets.UTF_8
    val staging = Paths.get("/Users/janm/Downloads/staging")
    val bagName = Paths.get("CLARIN-spullen")
    val zipping = File(staging).createChild("zipping-temp", asDirectory = true)
    val bag = File(staging.resolve(bagName))
    bag.moveTo(zipping / bagName.toString)
    zipping.zipTo(staging.resolve(s"$bagName.zip"), compressionLevel = Deflater.NO_COMPRESSION)





}
