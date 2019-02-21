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

import nl.knaw.dans.easy.bagstore.ConfigurationComponent
import nl.knaw.dans.easy.bagstore.component.BagStoreWiring

trait ServerWiring extends BagStoreServerComponent with DefaultServletComponent with BagsServletComponent with StoresServletComponent {
  this: BagStoreWiring with ConfigurationComponent =>

  private val ebu = new URI(configuration.properties.getString("daemon.external-base-uri"))

  lazy val defaultServlet: DefaultServlet = new DefaultServlet {
    val externalBaseUri: URI = ebu
  }
  lazy val bagsServlet: BagsServlet = new BagsServlet {
  }
  lazy val storesServlet: StoresServlet = new StoresServlet {
    override val externalBaseUri: URI = ebu
    override val bagstoreUsername: String = configuration.properties.getString("bag-store.username")
    override val bagstorePassword: String = configuration.properties.getString("bag-store.password")
  }
  lazy val server: BagStoreServer = new BagStoreServer(configuration.properties.getInt("daemon.http.port"))
}
