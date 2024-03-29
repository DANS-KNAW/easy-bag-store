MANUAL
======

SYNOPSIS
--------

    easy-bag-store [-b,--base-dir <dir>|-s,--store-name <name>]
        # operations on items in a bag store
          list
        | add [-m,--move] [-u,--uuid <uuid>] <bag>
        | get [-d,--directory <dir>] [-f, --force-inactive] [-s,--skip-completion] <item-id>
        | export [-f, --force-inactive] [-d,--directory <dir>] [-b, --bagid-list <file>]
        | stream [-f, --force-inactive] [--format zip|tar] <item-id>
        | enum [[-a,--all] [-f, --force-inactive] [-e,--exclude-directories] [-d, --from-date] \ 
            [-i,--inactive] <bag-id>]
        | locate [-f,--file-data-location] <item-id>
        | deactivate <bag-id>
        | reactivate <bag-id>
        
        # operations on bags outside a bag store
          prune <bag-dir> <ref-bag-id>...
        | complete [-f,--keep-fetchtxt] <bag-dir>
        | validate <bag-dir>
        
        # start as HTTP service
          run-service
                          

DESCRIPTION
-----------
A bag store is a way to store and identify data packages following a few very simple rules. See the [bag-store] page
for a quasi-formal description and see the [tutorial] page for a more informal, hands-on introduction. The `easy-bag-store` 
command line tool and HTTP-service facilitate the management of one or more bag stores, but use of these tools is optional; 
the whole point of the bag store concept is that it should be fairly easy to implement your own tools for working with it.

[bag-store]: definitions.md
[tutorial]: tutorial.md

### Command line tool
By using the `easy-bag-store` command you can manage a bag store from the command line. The sub-commands in above 
[SYNOPSIS](#synopsis) are subdivided into two groups:

* Sub-commands that target items in the bag store. These implement operations that change, check or retrieve items in a bag store.
* Sub-commands that target bags outside a bag store. These are typically bags that are intended to be 
  added to a bag store later, or that have just been retrieved from one. These sub-commands still work in the context of one or
  more bag stores, because the bag directories they operate on may contain [local references] to bags in those stores.
  
Some of the sub-commands require you to specify the store context you want to use. The store to operate on can be specified
in one of two ways:

* With the `--store` option. This expects the shortname of a store, which is mapped to a base directory in the `stores.properties`
  configuration file.
* By specifying the base directory directly, using the `--base-dir` option.

If you call a sub-command that requires a store context, without providing one, you are prompted for a store shortname.

### HTTP service
`easy-bag-store` can also be executed as a service that accepts HTTP requests, using the sub-command `run-service`. `initd` and
`systemd` scripts are provided, to facilitate deployment on a Unix-like system (see [INSTALLATION AND CONFIGURATION](#installation-and-configuration)).

For details about the service API see the [OpenAPI specification].

[OpenAPI specification]: ./api.html
[local references]: definitions.md#local-item-uri

ARGUMENTS
---------

      -b, --base-dir  <arg>     bag store base-dir to use
      -s, --store-name  <arg>   Configured store to use
      -h, --help                Show help message
      -v, --version             Show version of this program
    
    Subcommand: list - Lists the bag stores for which a shortname has been defined. These are the bag stores
    that are also accessible through the HTTP interface.
    
      -h, --help   Show help message
    ---
    
    Subcommand: add - Adds a bag to the bag-store
      -m, --move          move (rather than copy) the bag when adding it to the bag
                          store
      -u, --uuid  <arg>   UUID to use as bag-id for the bag
      -h, --help          Show help message
    
     trailing arguments:
      bag (required)   the (unserialized) bag to add
    ---
    
    Subcommand: get - Retrieves an item by copying it to the specified directory (default: current directory).
      -d, --directory  <arg>   directory in which to put the item (default = .)
      -f, --force-inactive     force retrieval of an inactive item (by default
                               inactive items are not retrieved)
      -s, --skip-completion    do not complete an incomplete bag
      -h, --help               Show help message
    
     trailing arguments:
      item-id (required)   item-id of the item to copy
    ---
    
    Subcommand: export - Exports bags to directories named with the bag-id of the bag. The bags are always valid, so virtually valid bags in the store are first completed.
      -b, --bagid-list  <arg>   newline-separated list of ids of the bags to export
      -d, --directory  <arg>    existing empty directory in which to put the
                                exported bags (default = .)
      -f, --force-inactive      force retrieval of an inactive item (by default
                                inactive items are not retrieved)
      -h, --help                Show help message
    ---
    
    Subcommand: stream - Retrieves an item by streaming it to the standard output
      -f, --force-inactive   force retrieval of an inactive item (by default
                             inactive items are not retrieved)
      --format  <arg>        stream item packaged in this format (tar|zip)
      -h, --help             Show help message
    
     trailing arguments:
      item-id (required)   item-id of the item to stream
    ---
    
    Subcommand: enum - Enumerates bags or Files
      -a, --all                   enumerate all bags, including inactive ones
      -e, --exclude-directories   enumerate only regular files, not directories
      -f, --force-inactive        force enumeration of files of an inactive bag (by
                                  default the files of an inactive bag are not
                                  enumerated)
      -d, --from-date  <arg>      Enumerate only bags that are created after this
                                  time. Format is yyyy-MM-ddTHH:mm:ss (e.g.
                                  2021-08-25T10:25:10)
      -i, --inactive              only enumerate inactive bags
      -h, --help                  Show help message
    
     trailing arguments:
      <bagId> (not required)   bag of which to enumerate the Files
    ---
    
    Subcommand: locate - Locates the item with <item-id> on the file system
      -f, --file-data-location   resolve to file-data-location
      -h, --help                 Show help message
    
     trailing arguments:
      <item-id> (required)   the item to locate
    ---
    
    Subcommand: deactivate - Marks a bag as inactive
      -h, --help   Show help message
    
     trailing arguments:
      <bag-id> (required)   bag to mark as inactive
    ---
    
    Subcommand: reactivate - Reactivates an inactive bag
      -h, --help   Show help message
    
     trailing arguments:
      <bag-id> (required)   inactive bag to re-activate
    ---
    
    Subcommand: prune - Removes Files from bag, that are already found in reference bags, replacing them with
      fetch.txt references.
    
      -h, --help   Show help message
    
     trailing arguments:
      <bag-dir> (required)         bag directory to prune
      <ref-bag-id>... (required)   One or more bag-ids of bags in the bag store to
                                   check for redundant Files
    ---
    
    Subcommand: complete - Resolves fetch.txt references from the bag store and copies them into <bag-dir>
      -f, --keep-fetchtxt   do not delete fetch.txt, if present
      -h, --help            Show help message
    
     trailing arguments:
      <bag-dir> (required)   bag directory to complete
    ---
    
    Subcommand: validate - Checks that <bag-dir> is a virtually-valid bag
      -h, --help   Show help message
    
     trailing arguments:
      <bag-dir> (required)   bag directory to validate
    ---
      
    Subcommand: run-service - Starts the EASY Bag Store as a daemon that services HTTP requests
      -h, --help   Show help message
    ---


INSTALLATION AND CONFIGURATION
------------------------------
Currently this project is build only as an RPM package for RHEL7/CentOS7 and later. The RPM will install the binaries to
`/opt/dans.knaw.nl/easy-bag-store`, the configuration files to `/etc/opt/dans.knaw.nl/easy-bag-store`,
and will install the service script for `systemd`. It will also set up a default bag store
at `/srv/dans.kanw.nl/bag-store`.

BUILDING FROM SOURCE
--------------------
Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* RPM

Steps:
    
    #!bash
    git clone https://github.com/DANS-KNAW/easy-bag-store.git
    cd easy-bag-store
    mvn install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM 
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.