package nl.knaw.dans.easy.bagstore.component

import java.io.InputStream
import java.nio.file.{ Files, Path }
import java.util.UUID

import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.lib.error._
import org.apache.commons.io.FileUtils

import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

trait BagStoresComponent {
  this: BagStoreComponent =>

  val bagStores: BagStores

  trait BagStores {

    val stores: Map[String, BagStore]

    def getStore(name: String): Option[BagStore] = stores.get(name)

    def get(itemId: ItemId, output: Path, fromStore: Option[Path] = None): Try[Path] = {
      fromStore
        .flatMap(baseDir => stores.collectFirst {
          case (_, store) if store.fileSystem.baseDir == baseDir => store.get(itemId, output)
        })
        .getOrElse {
          stores.values.toStream
            .map(_.get(itemId, output))
            .find(_.isSuccess)
            .getOrElse(Failure(NoSuchBagException(BagId(itemId.getUuid))))
        }
    }

    def enumBags(includeActive: Boolean = true, includeInactive: Boolean = false, fromStore: Option[Path] = None): Try[Seq[BagId]] = {
      fromStore
        .flatMap(baseDir => stores.collectFirst {
          case (_, store) if store.fileSystem.baseDir == baseDir => store.enumBags(includeActive, includeInactive)
        })
        .getOrElse {
          stores.values
            .map(_.enumBags(includeActive, includeInactive))
            .collectResults
            .map(_.reduceOption(_ ++ _).getOrElse(Seq.empty))
        }
    }

    def enumFiles(bagId: BagId, fromStore: Option[Path] = None): Try[Seq[FileId]] = {
      fromStore
        .flatMap(baseDir => stores.collectFirst {
          case (_, store) if store.fileSystem.baseDir == baseDir => store.enumFiles(bagId)
        })
        .getOrElse {
          def recurse(storesToSearch: List[BagStore]): Try[Seq[FileId]] = {
            storesToSearch match {
              case Nil => Failure(NoSuchBagException(bagId))
              case store :: remainingStores =>
                store.enumFiles(bagId) match {
                  case s@Success(_) => s
                  case Failure(_) => recurse(remainingStores)
                }
            }
          }

          recurse(stores.values.toList)
        }
    }

    def putBag(inputStream: InputStream, bagStore: BagStore, uuidString: String): Try[BagId] = {
      for {
        uuid <- Try { UUID.fromString(bagStore.fileSystem.formatUuidStrCanonically(uuidString.filterNot('-' ==))) }
        _ <- checkBagDoesNotExist(BagId(uuid))
        staging <- bagStore.processor.unzipBag(inputStream)
        staged <- bagStore.processor.findBagDir(staging)
        bagId <- bagStore.add(staged, Some(uuid), skipStage = true)
        _ = FileUtils.deleteDirectory(staging.toFile)
      } yield bagId
    }

    private def checkBagDoesNotExist(bagId: BagId): Try[Unit] = {
      stores.map { case (name, store) =>
        store.fileSystem.toContainer(bagId)
          .flatMap {
            case file if Files.exists(file) && Files.isDirectory(file) => Failure(BagIdAlreadyAssignedException(bagId, name))
            case file if Files.exists(file) => Failure(CorruptBagStoreException("Regular file in the place of a container: $f"))
            case _ => Success(())
          }
      }.collectResults.map(_ => ())
    }

    def deactivate(bagId: BagId, fromStore: Option[Path] = None): Try[Unit] = {
      fromStore
        .flatMap(baseDir => stores.collectFirst {
          case (_, store) if store.fileSystem.baseDir == baseDir => store.deactivate(bagId)
        })
        .getOrElse {
          stores.values.toStream
            .map(_.deactivate(bagId))
            .find {
              case Success(_) | Failure(AlreadyInactiveException(_)) => true
              case _ => false
            }
            .getOrElse(Failure(NoSuchBagException(bagId)))
        }
    }

    def reactivate(bagId: BagId, fromStore: Option[Path] = None): Try[Unit] = {
      fromStore
        .flatMap(baseDir => stores.collectFirst {
          case (_, store) if store.fileSystem.baseDir == baseDir => store.reactivate(bagId)
        })
        .getOrElse {
          stores.values.toStream
            .map(_.reactivate(bagId))
            .find {
              case Success(_) | Failure(NotInactiveException(_)) => true
              case _ => false
            }
            .getOrElse(Failure(NoSuchBagException(bagId)))
        }
    }
  }
}
