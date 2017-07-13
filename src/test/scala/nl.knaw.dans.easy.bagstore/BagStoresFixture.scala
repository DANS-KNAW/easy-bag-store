package nl.knaw.dans.easy.bagstore

import java.net.URI

import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }

trait BagStoresFixture extends BagStoreFixture {
  test: TestSupportFixture
    with BagFacadeComponent
    with BagStoresComponent
    with BagStoreComponent
    with BagProcessingComponent
    with FileSystemComponent =>

  val fs = new FileSystem {
    override val baseDir: BagPath = store1
    override val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
    override val bagPermissions: String = "rwxr-xr-x"
    override val localBaseUri: URI = new URI("http://example-archive.org")
  }

  val processor = new BagProcessing {
    override val fileSystem: FileSystem = fs
    override val stagingBaseDir: BagPath = testDir
    override val outputBagPermissions: String = "rwxr-xr-x"
  }

  val bagStore1 = new BagStore {
    override val fileSystem: FileSystem = fs
    override val processor: BagProcessing = test.processor
  }

  val bagStore2 = new BagStore { bs2 =>
    override val fileSystem: FileSystem = new FileSystem {
      override val baseDir: BagPath = store2
      override val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
      override val bagPermissions: String = "rwxr-xr-x"
      override val localBaseUri: URI = new URI("http://example-archive.org")
    }
    override val processor: BagProcessing = new BagProcessing {
      override val fileSystem: FileSystem = bs2.fileSystem
      override val stagingBaseDir: BagPath = testDir
      override val outputBagPermissions: String = "rwxr-xr-x"
    }
  }

  override val bagStores = new BagStores {
    override val stores: Map[String, BagStore] = Map(
      "store1" -> bagStore1,
      "store2" -> bagStore2
    )
  }
}
