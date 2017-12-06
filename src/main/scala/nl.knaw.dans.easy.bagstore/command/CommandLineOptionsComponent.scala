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

import java.nio.file.{ Files, Path, Paths }

import nl.knaw.dans.easy.bagstore.ConfigurationComponent
import org.rogach.scallop.{ ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter }

trait CommandLineOptionsComponent {
  this: ConfigurationComponent =>

  val commandLine: CommandLineOptions

  class CommandLineOptions(args: Array[String]) extends ScallopConf(args) {
    appendDefaultToDescription = true
    editBuilder(_.setHelpWidth(110))

    printedName = "easy-bag-store"
    private val _________ = " " * (printedName.length + 2)
    private val SUBCOMMAND_SEPARATOR = "---\n"
    version(s"$printedName v${ configuration.version }")
    banner(
      s"""
         |Manage a bag store
         |
         |Usage:
         |
         |$printedName [-b,--base-dir <dir>|-s,--store-name <name>]
         |${ _________ }# operations on items in a bag store
         |${ _________ }| list
         |${ _________ }| add [-u,--uuid <uuid>] <bag>
         |${ _________ }| get [-s,--skip-completion] [-d,--directory <dir>] <item-id>
         |${ _________ }| stream [-f,--format zip|tar] <item-id>
         |${ _________ }| enum [[-i,--inactive|-a,--all] [<item-id>]]
         |${ _________ }| deactivate <bag-id>
         |${ _________ }| reactivate <bag-id>
         |${ _________ }| verify [<bag-id>]
         |${ _________ }| erase <file-id>...
         |${ _________ }|
         |${ _________ }# operations on bags outside a bag store
         |${ _________ }| prune <bag-dir> <ref-bag-id>...
         |${ _________ }| complete <bag-dir>
         |${ _________ }| validate <bag-dir>
         |${ _________ }|
         |${ _________ }# start as HTTP service
         |${ _________ }| run-service
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
        """Lists the bag stores for which a shortname has been defined. These are the bag stores
          |that are also accessible through the HTTP interface.
        """.stripMargin)
    }
    addSubcommand(list)

    val add = new Subcommand("add") {
      descr("Adds a bag to the bag-store")
      val bag: ScallopOption[Path] = trailArg(name = "bag",
        descr = "the (unserialized) bag to add")
      validatePathExists(bag)
      val uuid: ScallopOption[String] = opt(name = "uuid", short = 'u',
        descr = "UUID to use as bag-id for the bag",
        required = false)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(add)

    val get = new Subcommand("get") {
      descr("Retrieves an item by copying it to the specified directory (default: current directory).")
      val skipCompletion: ScallopOption[Boolean] = opt(name = "skip-completion",
        descr = "do not complete an incomplete bag")
      val outputDir: ScallopOption[Path] = opt(name = "directory",
        descr = "directory in which to put the item",
        default = Some(Paths.get(".")))
      val itemId: ScallopOption[String] = trailArg[String](name = "item-id",
        descr = "item-id of the item to copy")
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(get)

    val stream = new Subcommand("stream") {
      descr("Retrieves an item by streaming it to the standard output")
      val format: ScallopOption[String] = opt(name = "format",
        descr = "stream item packaged in this format (tar|zip)")
      val itemId: ScallopOption[String] = trailArg[String](name = "item-id",
        descr = "item-id of the item to stream")
    }
    addSubcommand(stream)

    val enum = new Subcommand("enum") {
      descr("Enumerates bags or Files")
      val inactive: ScallopOption[Boolean] = opt[Boolean](name = "inactive", short = 'd',
        descr = "only enumerate inactive bags")
      val all: ScallopOption[Boolean] = opt[Boolean](name = "all", short = 'a',
        descr = "enumerate all bags, including inactive ones")
      val excludeDirectories: ScallopOption[Boolean] = opt(name = "exclude-directories",
        descr = "enumerate only regular files, not directories")
      val bagId: ScallopOption[String] = trailArg[String](name = "<bagId>",
        descr = "bag of which to enumerate the Files",
        required = false)
      mutuallyExclusive(all, inactive)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(enum)

    val locate = new Subcommand("locate") {
      descr("Locates the item with <item-id> on the file system")
      val itemId: ScallopOption[String] = trailArg(name = "<item-id>",
        descr = "the item to locate",
        required = true)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(locate)

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
        descr = "inactive bag to re-activate",
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
