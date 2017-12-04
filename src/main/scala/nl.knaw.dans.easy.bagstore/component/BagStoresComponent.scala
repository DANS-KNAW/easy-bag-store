/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.bagstore.component

import java.io.{ InputStream, OutputStream }
import java.nio.file.{ Files, Path }
import java.util.UUID

import nl.knaw.dans.easy.bagstore.ArchiveStreamType.ArchiveStreamType
import nl.knaw.dans.easy.bagstore._
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils

import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

trait BagStoresComponent {
  this: FileSystemComponent with BagProcessingComponent with BagStoreComponent with DebugEnhancedLogging =>

  val bagStores: BagStores

  trait BagStores {


    def stores2: Map[String, BaseDir]

    def getStore(name: String): Option[BagStore] = stores.get(name)

    def getStore2(name: String): Option[BaseDir] = stores2.get(name)

    def stores: Map[String, BagStore]
    def getStore(dir: BaseDir): BagStore = stores.values.find(_.baseDir == dir).getOrElse(BagStore(dir))


    def getToDirectory(itemId: ItemId, output: Path, fromStore: Option[BaseDir] = None): Try[Path] = {
      fromStore
        .map(getStore)
        .map(_.get(itemId, output))
        .getOrElse {
          stores.values.toStream
            .map(_.get(itemId, output))
            .find(_.isSuccess)
            .getOrElse(Failure(NoSuchBagException(BagId(itemId.uuid))))
        }
    }

    def getToStream(itemId: ItemId, archiveStreamType: Option[ArchiveStreamType], outputStream: => OutputStream, fromStore: Option[BaseDir] = None): Try[Unit] = {
      fromStore
        .map(getStore)
        .map(_.getToStream(itemId, archiveStreamType, outputStream))
        .getOrElse {
          stores.values.toStream
            .map(_.getToStream(itemId, archiveStreamType, outputStream))
            .find(_.isSuccess)
            .getOrElse(Failure(NoSuchBagException(BagId(itemId.uuid))))
        }
    }

    def enumBags(includeActive: Boolean = true, includeInactive: Boolean = false, fromStore: Option[BaseDir] = None): Try[Seq[BagId]] = {
      fromStore
        .map(getStore)
        .map(_.enumBags(includeActive, includeInactive))
        .getOrElse {
          stores.values
            .map(_.enumBags(includeActive, includeInactive))
            .collectResults
            .map(_.reduceOption(_ ++ _).getOrElse(Seq.empty))
        }
    }

    def enumFiles(itemId: ItemId, includeDirectories: Boolean = true, fromStore: Option[BaseDir] = None): Try[Seq[FileId]] = {
      fromStore
        .flatMap(baseDir => stores.collectFirst {
          case (_, store) if store.baseDir == baseDir => store.enumFiles(itemId, includeDirectories)
        })
        .getOrElse {
          def recurse(storesToSearch: List[BagStore]): Try[Seq[FileId]] = {
            storesToSearch match {
              case Nil => Failure(NoSuchBagException(BagId(itemId.uuid)))
              case store :: remainingStores =>
                store.enumFiles(itemId, includeDirectories) match {
                  case s @ Success(_) => s
                  case Failure(e) =>
                    debug(s"Failure returned from store $store: ${e.getMessage}")
                    logger.error("Exception", e)
                    recurse(remainingStores)
                }
            }
          }

          recurse(stores.values.toList)
        }
    }


    def putBag(inputStream: InputStream, bagStore: BagStore, uuid: UUID): Try[BagId] = {
      for {
        _ <- checkBagDoesNotExist(BagId(uuid))
        staging <- bagProcessing.unzipBag(inputStream)
        staged <- bagProcessing.findBagDir(staging)
        bagId <- bagStore.add(staged, Some(uuid), skipStage = true)
        _ = FileUtils.deleteDirectory(staging.toFile)
      } yield bagId
    }

    private def checkBagDoesNotExist(bagId: BagId): Try[Unit] = {
      stores.map { case (name, store) =>
        implicit val baseDir: BaseDir = store.baseDir
        fileSystem.toContainer(bagId)
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
          case (_, store) if store.baseDir == baseDir => store.deactivate(bagId)
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
          case (_, store) if store.baseDir == baseDir => store.reactivate(bagId)
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

    def locate(itemId: ItemId, fromStore: Option[Path] = None): Try[Path] = {
      fromStore
        .flatMap(baseDir => stores.collectFirst {
          case (_, store) if store.baseDir == baseDir => store.locate(itemId)
        })
        .getOrElse {
          stores.values.toStream
            .map(_.locate(itemId))
            .find(_.isSuccess)
            .getOrElse(Failure(NoSuchItemException(itemId)))
        }
    }
  }
}
