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

  override val fileSystem = new FileSystem {
    override val uuidPathComponentSizes: Seq[Int] = Seq(2, 30)
    override val bagPermissions: String = "rwxr-xr-x"
    override val localBaseUri: URI = new URI("http://example-archive.org")
  }

  override val processor = new BagProcessing {
    override val stagingBaseDir: BagPath = testDir
    override val outputBagPermissions: String = "rwxr-xr-x"
  }

  val bagStore1 = new BagStore {
    implicit val baseDir: BaseDir = store1
  }

  val bagStore2 = new BagStore {
    implicit val baseDir: BaseDir = store2
  }

  override val bagStores = new BagStores {
    override val stores: Map[String, BagStore] = Map(
      "store1" -> bagStore1,
      "store2" -> bagStore2
    )
  }
}
