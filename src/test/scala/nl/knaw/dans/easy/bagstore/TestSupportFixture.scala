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

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Paths}

import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers, OneInstancePerTest}

trait TestSupportFixture extends FlatSpec with Matchers with OneInstancePerTest with BeforeAndAfter {
  val testDir = Paths.get(s"target/test/${getClass.getSimpleName}").toAbsolutePath
  FileUtils.deleteQuietly(testDir.toFile)
  Files.createDirectories(testDir)

}


