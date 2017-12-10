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

import java.net.URI

import nl.knaw.dans.easy.bagstore.component.BagStoresComponent
import org.scalatra.{ Ok, ScalatraServlet }

trait DefaultServletComponent {
  this: BagStoresComponent =>

  val defaultServlet: DefaultServlet

  trait DefaultServlet extends ScalatraServlet {

    val externalBaseUri: URI

    get("/") {
      contentType = "text/plain"
      Ok(s"""EASY Bag Store is running.
           |Available stores at <${ externalBaseUri.resolve("stores") }>
           |Bags from all stores at <${ externalBaseUri.resolve("bags") }>
           |""".stripMargin)
    }
  }
}
