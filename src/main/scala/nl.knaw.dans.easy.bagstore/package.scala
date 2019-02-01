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
package nl.knaw.dans.easy

import java.net.URI
import java.nio.file.{ Files, Path }

import org.apache.commons.io.FileUtils
import resource._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Success, Try }

package object bagstore {
  case class NoItemUriException(uri: URI, baseUri: URI) extends Exception(s"Base of URI $uri is not an item-uri: does not match base-uri; base-uri is $baseUri")
  case class IncompleteItemUriException(msg: String) extends Exception(s"URI is an item-uri but missing parts: $msg")
  case class MoveToStoreFailedException(bag: Path, containerDir: Path) extends Exception(s"Failed to move $bag to container at $containerDir")
  case class AlreadyInactiveException(bagId: BagId) extends Exception(s"$bagId is already hidden")
  case class NotInactiveException(bagId: BagId) extends Exception(s"$bagId is already visible")
  case class NoSuchBagException(bagId: BagId) extends Exception(s"Bag $bagId does not exist in BagStore")
  case class BagReaderException(bagDir: Path, cause: Throwable) extends Exception(s"The bag at '$bagDir' could not be read: ${ cause.getMessage }", cause)
  case class NoSuchPayloadManifestException(bagDir: Path, algorithm: Algorithm) extends Exception(s"The bag at '$bagDir' did not contain a payload manifest for algorithm $algorithm")
  case class NoSuchFileItemException(fileId: FileId) extends Exception(s"File $fileId does not exist in bag ${ fileId.bagId }")
  case class NoSuchItemException(itemId: ItemId) extends Exception(s"Item $itemId not found")
  case class BagIdAlreadyAssignedException(bagId: BagId, store: String) extends Exception(s"$bagId already exists in BagStore $store (bag-ids must be globally unique)")
  case class CannotIngestHiddenBagDirectoryException(bagDir: Path) extends Exception(s"Cannot ingest hidden directory $bagDir")
  case class IncorrectNumberOfFilesInBagZipRootException(n: Int) extends Exception(s"There must be exactly one file in the root directory of the zipped bag, found $n")
  case class BagBaseNotFoundException() extends Exception(s"The zipped bag contains no bag base directory")
  case class NoBagIdException(itemId: ItemId) extends Exception(s"item-id $itemId is not a bag-id")
  case class NoFileIdException(itemId: ItemId) extends Exception(s"item-id $itemId is not a file-id")
  case class CorruptBagStoreException(reason: String) extends Exception(s"BagStore seems to be corrupt: $reason")
  case class OutputAlreadyExists(path: Path) extends Exception(s"Output path already exists; not overwriting $path")
  case class InactiveException(itemId: ItemId, forceInactive: Boolean = false) extends Exception(s"Tried to retrieve an inactive bag: ${ itemId.uuid } with toggle forceInactive = $forceInactive")
  case class OutputNotADirectoryException(path: Path) extends Exception(s"Output path must be a directory; $path exists, but is not a directory.")
  case class NoBagException(cause: Throwable) extends Exception("The provided input did not contain a bag", cause)
  case class InvalidBagException(bagId: BagId, msg: String) extends Exception(s"Bag $bagId is not a valid bag: $msg")
  case class NoRegularFileException(itemId: ItemId) extends Exception(s"Item $itemId is not a regular file.")
  case class UnsupportedMediaTypeException(givenType: String, acceptedType: String) extends Exception(s"media type $givenType is not supported by this api. Supported types are '$acceptedType'")

  type BaseDir = Path

  def pathsEqual(f1: Path, f2: Path, excludeFiles: String*): Boolean = {

    @tailrec
    def rec(todo: List[(Path, Path)], acc: Boolean = true): Boolean = {
      if (acc) {
        todo match {
          case Nil => acc
          case (file1, file2) :: tail if Files.isDirectory(file1) && Files.isDirectory(file2) =>
            val files1 = listFiles(file1).filterNot(excludeFiles contains _.getFileName.toString)
            val files2 = listFiles(file2).filterNot(excludeFiles contains _.getFileName.toString)
            if (files1.size != files2.size) {
              false
            }
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

  // TODO: canditates for dans-scala-lib?
  def listDirs(dir: Path): Seq[Path] = {
    listFiles(dir).filter(Files.isDirectory(_))
  }

  def listFiles(dir: Path): Seq[Path] = {
    managed(Files.list(dir)).acquireAndGet(_.iterator().asScala.toList)
  }

  def walkFiles(dir: Path): Seq[Path] = {
    managed(Files.walk(dir)).acquireAndGet(_.iterator().asScala.toList)
  }

  /**
   * Gets the complete set of files, including implicit directories from
   * a set paths that only list regular files.
   *
   * Example use: the payload manifest of a virtually-valid bag contains all the regular files but
   * does not contain lines for the directories. Use this function to get all the files and
   * directories implied by the paths in the manifest.
   *
   * @param files the set of regular files
   * @return the set of regular files, extended with the directories implied in the paths
   */
  def getCompleteFileSet(files: Set[Path]): Set[Path] = {
    files ++ calcDirectoriesFromFileSet(files)
  }

  /**
   * Finds the directories implied by a list of file paths.
   *
   * @param files the list of file paths
   * @return a list of directory paths
   */
  def calcDirectoriesFromFileSet(files: Set[Path]): Set[Path] = {
    files.collect {
      case f => pathToParentDirectoriesSet(f)
    }.flatten
  }

  /**
   * Calculates the set of parent directories for a given path
   *
   * @param path the path to get the parent directories from
   * @return the set of parent directories
   */
  def pathToParentDirectoriesSet(path: Path): Set[Path] = {
    val list = new ListBuffer[Path]()
    for (i <- 1 until path.getNameCount) {
      list += path.subpath(0, i)
    }
    list.toSet
  }
}
