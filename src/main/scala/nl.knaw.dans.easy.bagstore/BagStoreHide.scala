/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.bagstore

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

trait BagStoreHide extends BagStoreContext {
  implicit val makeBaseDirTemporarilyWritable = true

  def hide(bagId: BagId): Try[Unit] = {
    for {
      _ <- checkBagExists(bagId)
      path <- toLocation(bagId)
      _ <- if (Files.isHidden(path)) Failure(AlreadyHiddenException(bagId)) else Success(())
      oldPerm <- makeWritable(path)
      newPath <- Try {
        path.getParent.resolve(s".${path.getFileName}")
      }
      _ <- Try {
        Files.move(path, newPath)
      }
      _ <- restorePermissions(newPath, oldPerm)
    } yield ()
  }

  def reveal(bagId: BagId): Try[Unit] = {
    for {
      _ <- checkBagExists(bagId)
      path <- toLocation(bagId)
      _ <- if (!Files.isHidden(path)) Failure(AlreadyVisibleException(bagId)) else Success(())
      oldPerm <- makeWritable(path)
      newPath <- Try {
        path.getParent.resolve(s"${path.getFileName.toString.substring(1)}")
      }
      _ <- Try {
        Files.move(path, newPath)
      }
      _ <- restorePermissions(newPath, oldPerm)
    } yield ()

  }

  private def makeWritable(dir: Path): Try[Set[PosixFilePermission]] = Try {
    val oldPermissions = Files.getPosixFilePermissions(dir).asScala.toSet
    val newPermissions = Set(PosixFilePermission.OWNER_WRITE) | oldPermissions
    Files.setPosixFilePermissions(dir, newPermissions.asJava)
    oldPermissions
  }

  private def restorePermissions(dir: Path, permissions: Set[PosixFilePermission]): Try[Unit] = Try {
    Files.setPosixFilePermissions(dir, permissions.asJava)
  }
}
