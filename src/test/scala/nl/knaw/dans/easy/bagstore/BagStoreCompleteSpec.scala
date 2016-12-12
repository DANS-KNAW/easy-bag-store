package nl.knaw.dans.easy.bagstore

import java.nio.file.Paths
import java.util.UUID

import org.apache.commons.io.FileUtils

import scala.util.Success

class BagStoreCompleteSpec extends BagStoreFixture with BagStoreComplete with BagStoreAdd {
  private val TEST_BAGS_DIR = Paths.get("src/test/resources/bags")
  private val TEST_BAGS_PRUNED = TEST_BAGS_DIR.resolve("basic-sequence-pruned")
  private val TEST_BAGS_UNPRUNED = TEST_BAGS_DIR.resolve("basic-sequence-unpruned")
  private val TEST_BAG_PRUNED_A = TEST_BAGS_PRUNED.resolve("a")
  private val TEST_BAG_PRUNED_B = TEST_BAGS_PRUNED.resolve("b")
  private val TEST_BAG_PRUNED_C = TEST_BAGS_PRUNED.resolve("c")
  private val TEST_BAG_UNPRUNED_C = TEST_BAGS_UNPRUNED.resolve("c")


  "complete" should "make pruned Bag whole again" in {
    add(TEST_BAG_PRUNED_A, Some(UUID.fromString("00000000-0000-0000-0000-000000000001")))
    add(TEST_BAG_PRUNED_B, Some(UUID.fromString("00000000-0000-0000-0000-000000000002")))
    val testDirBagC = testDir.resolve("c")
    FileUtils.copyDirectory(TEST_BAG_PRUNED_C.toFile, testDirBagC.toFile)
    val result = complete(testDirBagC)
    result shouldBe a[Success[_]]
    pathsEqual(TEST_BAG_UNPRUNED_C, testDirBagC) shouldBe true
  }






}
