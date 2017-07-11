package nl.knaw.dans.easy.bagstore

import java.nio.file.{ Files, Path }

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterEach

trait BagStoreFixture extends BeforeAndAfterEach {
  this: TestSupportFixture with BagFacadeComponent =>

  val store1: Path = testDir.resolve("bag-store-1")
  val store2: Path = testDir.resolve("bag-store-2")

  override def beforeEach(): Unit = {
    super.beforeEach()

    FileUtils.deleteDirectory(store1.toFile)
    FileUtils.deleteDirectory(store2.toFile)

    Files.createDirectories(store1)
    Files.createDirectories(store2)
  }
}
