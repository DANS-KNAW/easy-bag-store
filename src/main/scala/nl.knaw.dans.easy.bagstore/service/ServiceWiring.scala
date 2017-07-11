package nl.knaw.dans.easy.bagstore.service

import java.nio.file.Paths

import nl.knaw.dans.easy.bagstore.component.BagStoreWiring
import nl.knaw.dans.easy.bagstore.server.ServerWiring
import nl.knaw.dans.easy.bagstore.{ Bagit4FacadeComponent, ConfigurationComponent }

trait ServiceWiring extends ServerWiring with BagStoreWiring with Bagit4FacadeComponent with ConfigurationComponent {

  val bagFacade: BagFacade = new Bagit4Facade()
  lazy val configuration: Configuration = Configuration(Paths.get(System.getProperty("app.home")))
}
