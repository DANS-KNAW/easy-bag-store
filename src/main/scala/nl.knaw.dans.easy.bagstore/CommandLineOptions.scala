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

import java.nio.file.{Path, Paths}

import org.apache.commons.configuration.PropertiesConfiguration
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, singleArgConverter}

class CommandLineOptions(args: Array[String], properties: PropertiesConfiguration) extends ScallopConf(args) {
  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))

  printedName = "easy-bag-store"
  private val _________ = " " * printedName.length
  private val SUBCOMMAND_SEPARATOR = "---\n"
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
            |${_________}  | (un)delete <item-id>
            |${_________}  | prune <bag-dir> <ref-bag-id>...
            |${_________}  | complete <bag-dir>
            |${_________}  | validate <bag-dir>
            |${_________}  | erase {--authority-name|-n} <name> {--authority-password|-p} <password> \\
            |${_________}      {--tombstone-message|-m <message>} <file-id> <bag-id>...
            |
            |Options:
            |""".stripMargin)


  private implicit val fileConverter = singleArgConverter[Path](s => Paths.get(resolveTildeToHomeDir(s)))
  private def resolveTildeToHomeDir(s: String): String = if (s.startsWith("~")) s.replaceFirst("~", System.getProperty("user.home")) else s

  val bagStoreBaseDir: ScallopOption[Path] = opt[Path](name = "base-dir", short = 'b',
    descr = "bag-store base-dir to use",
    default = Some(Paths.get(properties.getString("bag-store.base-dir"))))
  validatePathExists(bagStoreBaseDir)

  val add = new Subcommand("add") {
    descr("Adds a bag to the bag-store")
    val bag: ScallopOption[Path] = trailArg[Path](name = "bag",
      descr = "the (unserialized) Bag to add")
      validatePathExists(bag)
    val uuid: ScallopOption[String] = opt[String](name = "uuid", short = 'u',
      descr = "UUID to use as name for the Bag",
      required = false)
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(add)

  val get = new Subcommand("get") {
    descr("Retrieves a Bag or File in it")
    val itemId: ScallopOption[String] = trailArg[String](name = "item-id",
      descr = "ID of the Bag or File to retrieve")
    val outputDir: ScallopOption[Path] = trailArg[Path](name = "<output-dir>",
      descr = "directory in which to put the Bag or File")
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(get)

  val enum = new Subcommand("enum") {
    descr("Enumerates Bags or Files")
    val hidden: ScallopOption[Boolean] = opt[Boolean](name = "deleted", short = 'd',
      descr = "only enumerate deleted Bags")
    val all: ScallopOption[Boolean] = opt[Boolean](name = "all", short = 'a',
      descr = "enumerate all Bags, including deleted ones")
    val bagId: ScallopOption[String] = trailArg[String](name = "<bagId>",
      descr = "Bag of which to enumerate the Files",
      required = false)
    mutuallyExclusive(all, hidden)
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(enum)

  val delete = new Subcommand("delete") {
    descr("Marks a Bag as deleted")
    val bagId: ScallopOption[String] = trailArg[String](name = "<bag-id>",
      descr = "Bag to mark as deleted",
      required = true)
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(delete)

  val undelete = new Subcommand("undelete") {
    descr("Reverses the effect of delete")
    val bagId: ScallopOption[String] = trailArg[String](name = "<bag-id>",
      descr = "Deleted Bag to restore",
      required = true)
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(undelete)

  val prune = new Subcommand("prune") {
    descr("Removes Files from Bag, that are already found in reference Bags, replacing them with fetch.txt references")
    val bagDir: ScallopOption[Path] = trailArg[Path](name = "<bag-dir>",
      descr = "Bag directory to prune",
      required = true)
    val referenceBags: ScallopOption[List[String]] = trailArg[List[String]](name = "<ref-bag-id>...",
      descr = "One or more bag-ids of Bags in the BagStore to check for redundant Files",
      required = true)
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(prune)

  val complete = new Subcommand("complete") {
    descr("Resolves fetch.txt references from the BagStore and copies them into <bag-dir>")
    val bagDir: ScallopOption[Path] = trailArg[Path](name = "<bag-dir>",
      descr = "Bag directory to complete",
      required = true)
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(complete)

  val validate = new Subcommand("validate") {
    descr("Checks that <bag-dir> is a virtually-valid Bag")
    val bagDir: ScallopOption[Path] = trailArg[Path](name = "<bag-dir>",
      descr = "Bag directory to validate",
      required = true)
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(validate)

  val runService = new Subcommand("run-service") {
    descr(
      "Starts the EASY Bag Store as a daemon that services HTTP requests")
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(runService)

  footer("")
}

object CommandLineOptions {
  def apply(args: Array[String], properties: PropertiesConfiguration): CommandLineOptions = new CommandLineOptions(args, properties)
}
