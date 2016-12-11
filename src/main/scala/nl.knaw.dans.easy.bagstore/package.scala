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
import collection.JavaConverters._
import org.apache.commons.io.FileUtils

import scala.annotation.tailrec

package object bagstore {
  case class NoItemUriException(uri: URI, baseUri: URI) extends Exception(s"Base of URI $uri is not an item-uri: does not match base-uri; base-uri is $baseUri")
  case class NoBagSequenceUriException(uri: URI, baseUri: URI) extends Exception(s"Base of URI $uri not a bagsequence uri: does not match base-uri; base-uri is $baseUri")
  case class IncompleteItemUriException(msg: String) extends Exception(s"URI is an item-uri but missing parts: $msg")
  case class IncompleteBagSequenceUriException(msg: String) extends Exception(s"URI is a bagsequence uri but missing parts: $msg")
  case class InvalidUuidInItemUriException(msg: String) extends Exception(s"Invalid UUID in item-uri URI: $msg")
  case class NoBagException2(bagId: BagId)(implicit baseDir: Path) extends Exception(s"Bagstore at $baseDir does not contain bag with bag-id: $bagId")
  case class NoBagException(bagId: BagId)(implicit baseDir: Path) extends Exception(s"Bagstore at $baseDir does not contain bag with bag-id: $bagId")
  case class MoveToStoreFailedException(bag: Path, containerDir: Path) extends Exception(s"Failed to move $bag to container at $containerDir")
  case class NoItemException(p: Path) extends Exception(s"Not a bag-store item: $p")
  case class NoItemIdException(s: String) extends Exception(s"Not a valid item-id string: $s")


  def pathsEqual(f1: Path, f2: Path, excludeFiles: String*): Boolean = {

    @tailrec
    def rec(todo: List[(Path, Path)], acc: Boolean = true): Boolean = {
      if (acc) {
        todo match {
          case Nil => acc
          case (file1, file2) :: tail if Files.isDirectory(file1) && Files.isDirectory(file2) =>
            val subs =
              Files.list(file1).iterator().asScala.toSeq.sorted
                .zip(Files.list(file2).iterator().asScala.toSeq.sorted).toList

            rec(tail ::: subs, acc)
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

  def walkTree(file: Path): Iterable[Path] = {
    val children = new Iterable[Path] {
      def iterator: Iterator[Path] = if (Files.isDirectory(file)) Files.list(file).iterator().asScala else Iterator.empty
    }
    children.flatMap(walkTree) ++: Seq(file)
  }

}
