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
package nl.knaw.dans.easy.bagstore.command

import java.nio.file.{ Path, Paths }

import nl.knaw.dans.easy.bagstore.ConfigurationComponent
import org.rogach.scallop.{ ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter }

trait CommandLineOptionsComponent {
  this: ConfigurationComponent =>

  val commandLine: CommandLineOptions

  class CommandLineOptions(args: Array[String]) extends ScallopConf(args) {
    appendDefaultToDescription = true
    editBuilder(_.setHelpWidth(110))

    printedName = "easy-bag-store"
    private val _________ = " " * printedName.length
    private val SUBCOMMAND_SEPARATOR = "---\n"
    version(s"$printedName v${ configuration.version }")
    banner(
      s"""
         |Manage a bag store
         |
         |Usage:
         |
         |$printedName [{--base-dir|-b <dir>}|{-store|-s <name>}] \\
         |${ _________ }| add --uuid|-u <uuid> <bag>
         |${ _________ }| get <item-id>
         |${ _________ }| enum [--inactive|--all] [<item-id>]
         |${ _________ }| {deactivate|reactivate} <item-id>
         |${ _________ }| prune <bag-dir> <ref-bag-id>...
         |${ _________ }| complete <bag-dir>
         |${ _________ }| validate <bag-dir>
         |${ _________ }| erase {--authority-name|-n} <name> {--authority-password|-p} <password> \\
         |${ _________ }      {--tombstone-message|-m <message>} <file-id> <bag-id>...
         |
         |Options:
         |""".stripMargin)


    private implicit val fileConverter: ValueConverter[Path] = singleArgConverter[Path](s => Paths.get(resolveTildeToHomeDir(s)))

    private def resolveTildeToHomeDir(s: String): String = if (s.startsWith("~")) s.replaceFirst("~", System.getProperty("user.home"))
                                                           else s

    val bagStoreBaseDir: ScallopOption[Path] = opt[Path](name = "base-dir", short = 'b',
      descr = "bag store base-dir to use")
    val storeName: ScallopOption[String] = opt[String](name = "store-name", short = 's',
      descr = "Configured store to use")
    mutuallyExclusive(bagStoreBaseDir, storeName)

    val list = new Subcommand("list") {
      descr(
        """Lists the bag-stores for which a shortname has been defined. These are the bag-stores
          |that are accessible through the HTTP interface
        """.stripMargin)
    }
    addSubcommand(list)

    val add = new Subcommand("add") {
      descr("Adds a bag to the bag-store")
      val bag: ScallopOption[Path] = trailArg[Path](name = "bag",
        descr = "the (unserialized) bag to add")
      validatePathExists(bag)
      val uuid: ScallopOption[String] = opt[String](name = "uuid", short = 'u',
        descr = "UUID to use as name for the bag",
        required = false)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(add)

    val get = new Subcommand("get") {
      descr("Retrieves a bag or File in it")
      val itemId: ScallopOption[String] = trailArg[String](name = "item-id",
        descr = "ID of the bag or File to retrieve")
      val outputDir: ScallopOption[Path] = trailArg[Path](name = "<output-dir>",
        descr = "directory in which to put the bag or File")
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(get)

    val enum = new Subcommand("enum") {
      descr("Enumerates bags or Files")
      val inactive: ScallopOption[Boolean] = opt[Boolean](name = "inactive", short = 'd',
        descr = "only enumerate inactive bags")
      val all: ScallopOption[Boolean] = opt[Boolean](name = "all", short = 'a',
        descr = "enumerate all bags, including inactive ones")
      val bagId: ScallopOption[String] = trailArg[String](name = "<bagId>",
        descr = "bag of which to enumerate the Files",
        required = false)
      mutuallyExclusive(all, inactive)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(enum)

    val deactivate = new Subcommand("deactivate") {
      descr("Marks a bag as inactive")
      val bagId: ScallopOption[String] = trailArg[String](name = "<bag-id>",
        descr = "bag to mark as inactive",
        required = true)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(deactivate)

    val reactivate = new Subcommand("reactivate") {
      descr("Reactivates an inactive bag")
      val bagId: ScallopOption[String] = trailArg[String](name = "<bag-id>",
        descr = "Inactive bag to re-activate",
        required = true)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(reactivate)

    val prune = new Subcommand("prune") {
      descr("Removes Files from bag, that are already found in reference bags, replacing them with fetch.txt references")
      val bagDir: ScallopOption[Path] = trailArg[Path](name = "<bag-dir>",
        descr = "bag directory to prune",
        required = true)
      val referenceBags: ScallopOption[List[String]] = trailArg[List[String]](name = "<ref-bag-id>...",
        descr = "One or more bag-ids of bags in the bag store to check for redundant Files",
        required = true)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(prune)

    val complete = new Subcommand("complete") {
      descr("Resolves fetch.txt references from the bag store and copies them into <bag-dir>")
      val bagDir: ScallopOption[Path] = trailArg[Path](name = "<bag-dir>",
        descr = "bag directory to complete",
        required = true)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(complete)

    val validate = new Subcommand("validate") {
      descr("Checks that <bag-dir> is a virtually-valid bag")
      val bagDir: ScallopOption[Path] = trailArg[Path](name = "<bag-dir>",
        descr = "bag directory to validate",
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
}
