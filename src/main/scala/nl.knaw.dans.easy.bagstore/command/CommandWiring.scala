package nl.knaw.dans.easy.bagstore.command

import java.nio.file.Paths

import nl.knaw.dans.easy.bagstore.{ Bagit4FacadeComponent, ConfigurationComponent }
import nl.knaw.dans.easy.bagstore.component.BagStoreWiring

trait CommandWiring extends BagStoreWiring with Bagit4FacadeComponent with CommandLineOptionsComponent with ConfigurationComponent {
  override val bagFacade: BagFacade = new Bagit4Facade()
  override lazy val configuration: Configuration = Configuration(Paths.get(System.getProperty("app.home")))
}
