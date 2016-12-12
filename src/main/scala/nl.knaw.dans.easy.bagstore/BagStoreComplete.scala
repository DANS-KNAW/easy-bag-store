package nl.knaw.dans.easy.bagstore

import java.nio.file.{Files, Path}

import scala.util.{Failure, Success, Try}

trait BagStoreComplete extends BagStoreContext {

  /**
   * @param bagDir
   * @return
   */
  def complete(bagDir: Path): Try[Unit] = {
    trace(bagDir)
    mapProjectedToRealLocation(bagDir) map {
      mappings =>
        mappings.map {
          case (projected, real) =>
            debug(s"copying $real -> $projected")
            if(!Files.isDirectory(projected.getParent)) {
              debug(s"create directory: ${projected.getParent}")
              Files.createDirectories(projected.getParent)
            }
            Files.copy(real, projected)
        }
    } flatMap {
      _ =>
        // TODO: change IllegalStateException into something more appropriate
        Try {
          Files.deleteIfExists(bagDir.resolve(bagFacade.FETCH_TXT_FILENAME))
          // TODO: remove fetch.txt from all tagmanifests.
          if (!bagFacade.isValid(bagDir).get)
            throw new IllegalStateException("Bag still not valid after completion")
        }
    }
  }
}
