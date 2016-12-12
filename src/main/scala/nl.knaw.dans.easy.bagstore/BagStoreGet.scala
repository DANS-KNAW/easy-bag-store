package nl.knaw.dans.easy.bagstore

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils

import scala.util.Try

trait BagStoreGet extends BagStoreContext {

  def get(itemId: ItemId, output: Path): Try[Unit] = {
    itemId match {
      case bagId: BagId => toLocation(bagId) map {
        path =>
          val target = if (Files.isDirectory(output)) output.resolve(path.getFileName) else output
          Files.createDirectory(target)
          FileUtils.copyDirectory(path.toFile, target.toFile)
          walkTree(output).foreach {
            path => Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr--")) // TODO: make configurable
          }
      }
      case fileId: FileId => toRealLocation(fileId) map {
        path =>
          val target = if (Files.isDirectory(output)) output.resolve(path.getFileName) else output
          Files.copy(path, target)
          Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr--")) // TODO: make configurable
      }
    }
  }
}
