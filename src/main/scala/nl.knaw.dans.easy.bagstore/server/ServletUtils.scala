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
package nl.knaw.dans.easy.bagstore.server

import nl.knaw.dans.easy.bagstore.ArchiveStreamType

import scala.language.implicitConversions

trait ServletUtils {
  type IncludeActive = Boolean
  type IncludeInactive = Boolean

  def includedStates(state: Option[String]): (IncludeActive, IncludeInactive) = {
    state match {
      case Some("all") => (true, true)
      case Some("inactive") => (false, true)
      case _ => (true, false)
    }
  }

  implicit def acceptToArchiveStreamType(accept: String): Option[ArchiveStreamType.Value] = {
    accept match {
      case "application/zip" => Some(ArchiveStreamType.ZIP)
      case "application/x-tar" => Some(ArchiveStreamType.TAR)
      case _ => None
    }
  }
}
