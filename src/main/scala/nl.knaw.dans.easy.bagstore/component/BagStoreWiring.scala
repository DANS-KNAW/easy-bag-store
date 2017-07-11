package nl.knaw.dans.easy.bagstore.component

import java.net.URI
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{ Files, Path, Paths }

import nl.knaw.dans.easy.bagstore.{ BagFacadeComponent, ConfigurationComponent }

import scala.collection.JavaConverters._
import scala.util.Try

trait BagStoreWiring extends BagStoresComponent with BagStoreComponent with BagProcessingComponent with FileSystemComponent {
  this: ConfigurationComponent with BagFacadeComponent =>

  override val bagStores: BagStores = new BagStores { bagStores =>
    private val properties = configuration.properties

    // read in common settings once
    private val uuidPathComponentSizes: Seq[Int] = properties.getStringArray("bag-store.uuid-component-sizes").map(_.toInt).toSeq
    private val bagPermissions: String = properties.getString("bag-store.bag-file-permissions")
    private val localBaseUri = new URI(properties.getString("bag-store.base-uri"))
    private val outputBagPermissions: String = properties.getString("output.bag-file-permissions")
    private val stagingBaseDir: Path = Paths.get(properties.getString("staging.base-dir"))

    // validate settings
    require(Files.isWritable(stagingBaseDir), s"Non-existent or non-writable staging base-dir: $stagingBaseDir")
    require(uuidPathComponentSizes.sum == 32, s"UUID-path component sizes must add up to length of UUID in hexadecimal, sum found: ${uuidPathComponentSizes.sum}")
    require(Try(PosixFilePermissions.fromString(bagPermissions)).isSuccess, s"Bag file permissions are invalid: '$bagPermissions'")
    require(Try(PosixFilePermissions.fromString(outputBagPermissions)).isSuccess, s"Bag export file permissions are invalid: '$outputBagPermissions'")

    override val stores: Map[String, BagStore] = {
      configuration.stores.getKeys.asScala.map(name => {
        val path = Paths.get(configuration.stores.getString(name)).toAbsolutePath

        val bagStore = new BagStore { bagStore =>
          override val fileSystem: FileSystem = new FileSystem {
            override val baseDir: BagPath = path
            override val uuidPathComponentSizes: Seq[Int] = bagStores.uuidPathComponentSizes
            override val bagPermissions: String = bagStores.bagPermissions
            override val localBaseUri: URI = bagStores.localBaseUri
          }
          override val processor: BagProcessing = new BagProcessing {
            override val fileSystem: FileSystem = bagStore.fileSystem
            override val outputBagPermissions: String = bagStores.outputBagPermissions
            override val stagingBaseDir: BagPath = bagStores.stagingBaseDir
          }
        }

        name -> bagStore
      }).toMap
    }
  }
}
