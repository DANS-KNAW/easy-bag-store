/*
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
import java.util.UUID

import nl.knaw.dans.lib.string._
import nl.knaw.dans.easy.bagstore.ArchiveStreamType.ArchiveStreamType
import nl.knaw.dans.easy.bagstore.{ ArchiveStreamType, ConfigurationComponent, FromDateException }
import org.rogach.scallop.{ ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter, stringConverter }

trait CommandLineOptionsComponent {
  this: ConfigurationComponent =>

  val commandLine: CommandLineOptions

  class CommandLineOptions(args: Array[String]) extends ScallopConf(args) {
    appendDefaultToDescription = true
    editBuilder(_.setHelpWidth(100))
    private val forceInactiveDescription = "force retrieval of an inactive item (by default inactive items are not retrieved)"

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
         |${ _________ }| add [-m,--move] [-u,--uuid <uuid>] <bag>
         |${ _________ }| get [-d,--directory <dir>] [-f, --force-inactive] [-s,--skip-completion] <item-id>
         |${ _________ }| export [-f, --force-inactive] [-d,--directory <dir>] [-b, --bagid-list <file>]
         |${ _________ }| stream [-f, --force-inactive] [--format zip|tar] <item-id>
         |${ _________ }| enum [[-a,--all] [-f, --force-inactive] [-e,--exclude-directories] [-d, --from-date] [-i,--inactive] <bag-id>]
         |${ _________ }| locate [-f,--file-data-location] <item-id>
         |${ _________ }| deactivate <bag-id>
         |${ _________ }| reactivate <bag-id>
         |${ _________ }|
         |${ _________ }# operations on bags outside a bag store
         |${ _________ }| prune <bag-dir> <ref-bag-id>...
         |${ _________ }| complete [-f,--keep-fetchtxt] <bag-dir>
         |${ _________ }| validate <bag-dir>
         |${ _________ }|
         |${ _________ }# start as HTTP service
         |${ _________ }| run-service
         |Options:
         |""".stripMargin)

    private implicit val fileConverter: ValueConverter[Path] = singleArgConverter[Path](s => Paths.get(resolveTildeToHomeDir(s)))
    private implicit val uuidParser: ValueConverter[UUID] = stringConverter.flatMap(_.toUUID.fold(e => Left(e.getMessage), uuid => Right(Option(uuid))))
    private implicit val archiveStreamTypeParser: ValueConverter[ArchiveStreamType.Value] = singleArgConverter {
      case "zip" => ArchiveStreamType.ZIP
      case "tar" => ArchiveStreamType.TAR
    }

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
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(list)

    val add = new Subcommand("add") {
      descr("Adds a bag to the bag-store")
      val bag: ScallopOption[Path] = trailArg(name = "bag",
        descr = "the (unserialized) bag to add")
      validatePathExists(bag)
      val uuid: ScallopOption[UUID] = opt(name = "uuid", short = 'u',
        descr = "UUID to use as bag-id for the bag",
        required = false)
      val move: ScallopOption[Boolean] = opt(name = "move", short = 'm',
        descr = "move (rather than copy) the bag when adding it to the bag store")
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(add)

    val get = new Subcommand("get") {
      descr("Retrieves an item by copying it to the specified directory (default: current directory).")
      val skipCompletion: ScallopOption[Boolean] = opt(name = "skip-completion",
        descr = "do not complete an incomplete bag")
      val forceInactive: ScallopOption[Boolean] = opt(name = "force-inactive", short = 'f', descr = forceInactiveDescription)
      val outputDir: ScallopOption[Path] = opt(name = "directory", short = 'd',
        descr = "directory in which to put the item",
        default = Some(Paths.get(".")))
      val itemId: ScallopOption[String] = trailArg[String](name = "item-id",
        descr = "item-id of the item to copy")
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(get)

    val export = new Subcommand("export") {
      descr("Exports bags to directories named with the bag-id of the bag. The bags are always valid, so virtually valid bags in the store are first completed.")
      val forceInactive: ScallopOption[Boolean] = opt(name = "force-inactive", short = 'f', descr = forceInactiveDescription)
      val outputDir: ScallopOption[Path] = opt(name = "directory", short = 'd',
        descr = "directory in which to put the exported bags",
        default = Some(Paths.get(".")))
      val items: ScallopOption[Path] = opt[Path](name = "bagid-list", short = 'b', required = true,
        descr = "newline-separated list of ids of the bags to export")
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(export)

    val stream = new Subcommand("stream") {
      descr("Retrieves an item by streaming it to the standard output")
      val format: ScallopOption[ArchiveStreamType] = opt(name = "format", noshort = true,
        descr = "stream item packaged in this format (tar|zip)")
      val forceInactive: ScallopOption[Boolean] = opt(name = "force-inactive", short = 'f', descr = forceInactiveDescription)
      val itemId: ScallopOption[String] = trailArg[String](name = "item-id",
        descr = "item-id of the item to stream")
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(stream)

    val enum = new Subcommand("enum") {
      descr("Enumerates bags or Files")
      val inactive: ScallopOption[Boolean] = opt[Boolean](name = "inactive", short = 'i',
        descr = "only enumerate inactive bags")
      val all: ScallopOption[Boolean] = opt[Boolean](name = "all", short = 'a',
        descr = "enumerate all bags, including inactive ones")
      val excludeDirectories: ScallopOption[Boolean] = opt(name = "exclude-directories", short = 'e',
        descr = "enumerate only regular files, not directories")
      val forceInactive: ScallopOption[Boolean] = opt(name = "force-inactive", short = 'f', descr = forceInactiveDescription)
      val fromDate: ScallopOption[String] = opt(name = "from-date", short = 'd',
        descr = "Enumerate only bags that are created after this time. Format is yyyy-MM-ddTHH:mm:ss (e.g. 2021-08-25T10:25:10)")
      val bagId: ScallopOption[String] = trailArg[String](name = "<bagId>",
        descr = "bag of which to enumerate the Files",
        required = false)
      mutuallyExclusive(all, inactive)
      footer(SUBCOMMAND_SEPARATOR)
    }
    addSubcommand(enum)

    val locate = new Subcommand("locate") {
      descr("Locates the item with <item-id> on the file system")
      val fileDataLocation: ScallopOption[Boolean] = opt(name = "file-data-location",
        descr = "resolve to file-data-location")
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
      descr(
        """Removes Files from bag, that are already found in reference bags, replacing them with
          |fetch.txt references.
          |""".stripMargin)
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
      val keepFetchTxt: ScallopOption[Boolean] = opt(name = "keep-fetchtxt", short = 'f',
        descr = "do not delete fetch.txt, if present")
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
