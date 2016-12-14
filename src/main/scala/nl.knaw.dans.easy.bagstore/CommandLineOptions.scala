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

import java.io.File
import java.nio.file.Path

import org.apache.commons.configuration.PropertiesConfiguration
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, singleArgConverter}

class CommandLineOptions(args: Array[String], properties: PropertiesConfiguration) extends ScallopConf(args) {
  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))

  printedName = "easy-bag-store"
  private val _________ = " " * printedName.length
  version(s"$printedName v${Version()}")
  banner(s"""
            |Manage a BagStore
            |
            |Usage:
            |
            |$printedName [--base-dir|-b <dir>] \\
            |${_________}  | add --uuid|-u <uuid> <bag>
            |${_________}  | get <item-id>
            |${_________}  | enum [--hidden|--all] [<item-id>]
            |${_________}  | hide <item-id>
            |${_________}  | prune <bag-dir> <ref-bag-id>...
            |${_________}  | complete <bag-dir>
            |${_________}  | index <bag-id>
            |${_________}  | erase {--authority-name|-n} <name> {--authority-password|-p} <password> \\
            |${_________}      {--tombstone-message|-m <message>} <file-id>
            |
            |Options:
            |""".stripMargin)


  private implicit val fileConverter = singleArgConverter[File](s => new File(resolveTildeToHomeDir(s)))
  private def resolveTildeToHomeDir(s: String): String = if (s.startsWith("~")) s.replaceFirst("~", System.getProperty("user.home")) else s

  val bagStoreBaseDir: ScallopOption[File] = opt[File](name = "base-dir", short = 'b',
    descr = "bag-store base-dir to use",
    default = Some(new File(properties.getString("bag-store.base-dir"))))
  validateFileExists(bagStoreBaseDir)

  val add = new Subcommand("add") {
    descr("Adds a bag to the bag-store")
    val bag: ScallopOption[File] = trailArg[File](name = "bag",
      descr = "the (unserialized) Bag to add")
      validateFileExists(bag)
    val uuid: ScallopOption[String] = opt[String](name = "uuid", short = 'u',
      descr = "UUID to use as name for the Bag",
      required = false)
  }
  addSubcommand(add)

  val get = new Subcommand("get") {
    descr("Retrieves a Bag or File in it")
    val itemId: ScallopOption[String] = trailArg[String](name = "item-id",
      descr = "ID of the Bag or File to retrieve")
    val outputDir: ScallopOption[File] = trailArg[File](name = "<output-dir>",
      descr = "directory in which to put the Bag or File")
  }
  addSubcommand(get)

  val enum = new Subcommand("enum") {
    descr("Enumerates Bags or Files")
    val hidden: ScallopOption[Boolean] = opt[Boolean](name = "hidden", short = 'h',
      descr = "only enumerate hidden Bags")
    val all: ScallopOption[Boolean] = opt[Boolean](name = "all", short = 'a',
      descr = "enumerate visible and hidden Bags")
    val bagId: ScallopOption[String] = trailArg[String](name = "<bagId>",
      descr = "Bag of which to enumerate the Files",
      required = false)
  }
  addSubcommand(enum)

  val hide = new Subcommand("hide") {
    descr("Permanently marks a Bag as hidden")
    val bagId: ScallopOption[String] = trailArg[String](name = "<bag-id>",
      descr = "Bag to mark as hidden",
      required = true)
  }
  addSubcommand(hide)

  val prune = new Subcommand("prune") {
    descr("Removes Files from Bag, that are already found in reference Bags, replacing them by fetch.txt references")
    val bagDir: ScallopOption[Path] = trailArg[Path](name = "<bag-dir>",
      descr = "Bag directory to prune",
      required = true)
    val referenceBags: ScallopOption[List[String]] = trailArg[List[String]](name = "<ref-bag-id>...",
      descr = "One or more bag-ids of Bags in the BagStore to check for redundant Files",
      required = true)
  }
  addSubcommand(prune)


  footer("")
}

object CommandLineOptions {
  def apply(args: Array[String], properties: PropertiesConfiguration): CommandLineOptions = new CommandLineOptions(args, properties)
}