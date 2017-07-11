package nl.knaw.dans.easy.bagstore.server

import java.net.URI

import nl.knaw.dans.easy.bagstore.ConfigurationComponent
import nl.knaw.dans.easy.bagstore.component.BagStoreWiring

trait ServerWiring extends BagStoreServerComponent with DefaultServletComponent with BagsServletComponent with StoresServletComponent {
  this: BagStoreWiring with ConfigurationComponent =>

  private val ebu = new URI(configuration.properties.getString("daemon.external-base-uri"))

  val defaultServlet: DefaultServlet = new DefaultServlet {
    val externalBaseUri: URI = ebu
  }
  val bagsServlet: BagsServlet = new BagsServlet {}
  val storesServlet: StoresServlet = new StoresServlet {
    val externalBaseUri: URI = ebu
  }
  val server: BagStoreServer = new BagStoreServer(configuration.properties.getInt("daemon.http.port"))
}
