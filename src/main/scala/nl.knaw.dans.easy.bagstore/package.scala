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
package nl.knaw.dans.easy

import java.net.URI
import java.nio.file.{Files, Path}
import java.util.Properties

import org.apache.commons.io.FileUtils

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

package object bagstore {
  case class NoItemUriException(uri: URI, baseUri: URI) extends Exception(s"Base of URI $uri is not an item-uri: does not match base-uri; base-uri is $baseUri")
  case class IncompleteItemUriException(msg: String) extends Exception(s"URI is an item-uri but missing parts: $msg")
  case class MoveToStoreFailedException(bag: Path, containerDir: Path) extends Exception(s"Failed to move $bag to container at $containerDir")
  case class AlreadyDeletedException(bagId: BagId) extends Exception(s"$bagId is already hidden")
  case class NotDeletedException(bagId: BagId) extends Exception(s"$bagId is already visible")
  case class NoSuchBagException(bagId: BagId) extends Exception(s"$bagId does not exist in BagStore")
  case class BagIdAlreadyAssignedException(bagId: BagId) extends Exception(s"$bagId already exists in BagStore")
  case class CannotIngestHiddenBagDirectory(bagDir: Path) extends Exception(s"Cannot ingest hidden directory $bagDir")
  case class IncorrectNumberOfFilesInBagZipRootException(n: Int) extends Exception(s"There must be exactly one file in the root directory of the zipped bag, found $n")
  case class BagBaseNotFoundException() extends Exception(s"The zipped bag contains no bag base directory")
  case class NoBagIdException(itemId: ItemId) extends Exception(s"item-id $itemId is not a bag-id")
  case class NoFileIdException(itemId: ItemId) extends Exception(s"item-id $itemId is not a file-id")

  object Version {
    def apply(): String = {
      val props = new Properties()
      props.load(getClass.getResourceAsStream("/Version.properties"))
      props.getProperty("application.version")
    }
  }

  def pathsEqual(f1: Path, f2: Path, excludeFiles: String*): Boolean = {

    @tailrec
    def rec(todo: List[(Path, Path)], acc: Boolean = true): Boolean = {
      if (acc) {
        todo match {
          case Nil => acc
          case (file1, file2) :: tail if Files.isDirectory(file1) && Files.isDirectory(file2) =>
            val files1 = Files.list(file1).iterator().asScala.toSeq
            val files2 = Files.list(file2).iterator().asScala.toSeq
            if (files1.size != files2.size)
              false
            else
              rec(tail ::: files1.sorted.zip(files2.sorted).toList, acc)
          case (file1, file2) :: tail if Files.isRegularFile(file1) && Files.isRegularFile(file2) =>
            rec(tail, excludeFiles.contains(f1.relativize(file1).toString) ||
              (acc &&
                file1.getFileName == file2.getFileName &&
                FileUtils.contentEquals(file1.toFile, file2.toFile)))
          case _ => false
        }
      }
      else
        false
    }
    rec(List((f1, f2)))
  }

  implicit class TryExtensions[T](val t: Try[T]) extends AnyVal {
    // TODO candidate for dans-scala-lib, see also implementation/documentation in easy-split-multi-deposit
    def onError[S >: T](handle: Throwable => S): S = {
      t match {
        case Success(value) => value
        case Failure(throwable) => handle(throwable)
      }
    }
  }
}
