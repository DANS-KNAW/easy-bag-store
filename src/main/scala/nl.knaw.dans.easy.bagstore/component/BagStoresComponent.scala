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
    def storeShortnames: Map[String, BaseDir]
    def getBaseDirByShortname(name: String): Option[BaseDir] = storeShortnames.get(name)

    def copyToDirectory(itemId: ItemId, output: Path, skipCompletion: Boolean = false, fromStore: Option[BaseDir] = None): Try[(Path, BaseDir)] = {
      fromStore
        .map(BagStore(_).copyToDirectory(itemId, output, skipCompletion))
        .getOrElse {
          storeShortnames.values.toStream
            .map(BagStore(_).copyToDirectory(itemId, output, skipCompletion))
            .find {
              case Failure(_: NoSuchBagException) => false
              case _ => true
            }
            .getOrElse(Failure(NoSuchBagException(BagId(itemId.uuid))))
        }
    }

    def copyToStream(itemId: ItemId, archiveStreamType: Option[ArchiveStreamType], outputStream: => OutputStream, fromStore: Option[BaseDir] = None): Try[Unit] = {
      fromStore
        .map(BagStore(_).copyToStream(itemId, archiveStreamType, outputStream))
        .getOrElse {
          storeShortnames.values.toStream
            .map(BagStore(_).copyToStream(itemId, archiveStreamType, outputStream))
            .find {
              case Failure(_: NoSuchBagException) => false
              case _ => true
            }
            .getOrElse(Failure(NoSuchBagException(BagId(itemId.uuid))))
        }
    }

    def enumBags(includeActive: Boolean = true, includeInactive: Boolean = false, fromStore: Option[BaseDir] = None): Try[Seq[BagId]] = {
      fromStore
        .map(BagStore(_).enumBags(includeActive, includeInactive))
        .getOrElse {
          storeShortnames.values.toStream
            .map(BagStore(_).enumBags(includeActive, includeInactive))
            .collectResults
            .map(_.reduceOption(_ ++ _).getOrElse(Seq.empty))
        }
    }

    def enumFiles(itemId: ItemId, includeDirectories: Boolean = true, fromStore: Option[BaseDir] = None): Try[Seq[FileId]] = {
      fromStore
        .map(BagStore(_).enumFiles(itemId, includeDirectories))
        .getOrElse {
          storeShortnames.values.toStream
            .map(BagStore(_).enumFiles(itemId, includeDirectories))
            .find {
              case Failure(_: NoSuchBagException) => false
              case _ => true
            }
            .getOrElse(Failure(NoSuchBagException(BagId(itemId.uuid))))
        }
    }

    def putBag(inputStream: InputStream, baseDir: BaseDir, uuid: UUID): Try[BagId] = {
      trace(baseDir, uuid)
      val bagStore = BagStore(baseDir)
      for {
        _ <- checkBagDoesNotExist(BagId(uuid))
        staging <- bagProcessing.unzipBag(inputStream)
        staged <- bagProcessing.findBagDir(staging)
        bagId <- bagStore.add(staged, Some(uuid), skipStage = true)
        _ = logger.info(s"Successfully added bag with bag-id $uuid to bag store at $baseDir")
        _ = FileUtils.deleteDirectory(staging.toFile)
      } yield bagId
    }

    private def checkBagDoesNotExist(bagId: BagId): Try[Unit] = {
      storeShortnames.map { case (name, store) =>
        implicit val baseDir: BaseDir = store
        fileSystem.toContainer(bagId)
          .flatMap {
            case file if Files.exists(file) && Files.isDirectory(file) => Failure(BagIdAlreadyAssignedException(bagId, name))
            case file if Files.exists(file) => Failure(CorruptBagStoreException("Regular file in the place of a container: $f"))
            case _ => Success(())
          }
      }.collectResults.map(_ => ())
    }

    def deactivate(bagId: BagId, fromStore: Option[BaseDir] = None): Try[Unit] = {
      fromStore
        .map(BagStore(_).deactivate(bagId))
        .getOrElse {
          storeShortnames.values.toStream
            .map(BagStore(_).deactivate(bagId))
            .find {
              case Success(_) | Failure(AlreadyInactiveException(_)) => true
              case _ => false
            }
            .getOrElse(Failure(NoSuchBagException(bagId)))
        }
    }

    def reactivate(bagId: BagId, fromStore: Option[BaseDir] = None): Try[Unit] = {
      fromStore
        .map(BagStore(_).reactivate(bagId))
        .getOrElse {
          storeShortnames.values.toStream
            .map(BagStore(_).reactivate(bagId))
            .find {
              case Success(_) | Failure(NotInactiveException(_)) => true
              case _ => false
            }
            .getOrElse(Failure(NoSuchBagException(bagId)))
        }
    }

    def locate(itemId: ItemId, fileDataLocation: Boolean = false, fromStore: Option[Path] = None): Try[Path] = {
      fromStore
        .map(BagStore(_).locate(itemId, fileDataLocation))
        .getOrElse {
          storeShortnames.values.toStream
            .map(BagStore(_).locate(itemId, fileDataLocation))
            .find(_.isSuccess)
            .getOrElse(Failure(NoSuchItemException(itemId)))
        }
    }
  }
}
