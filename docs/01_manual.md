---
title: Manual
layout: home
---

Manual
======

TABLE OF CONTENTS
-----------------

* [SYNOPSIS](#synopsis)
* [DESCRIPTION](#description)
    + [Command line tool](#command-line-tool)
    + [HTTP service](#http-service)
* [INSTALLATION AND CONFIGURATION](#installation-and-configuration)
* [BUILDING FROM SOURCE](#building-from-source)

SYNOPSIS
--------

    easy-bag-store [--base-dir,-b <dir>|--store,-s <name>]
                     # operations on bags in a bag store
                     | add [--uuid,-u] <bag>
                     | get <item-id> <out-location>
                     | enum [[--inactive,-i|--all,-a] <bag-id>]
                     | deactivate <bag-id>
                     | reactivate <bag-id>
                     | verify [<bag-id>]
                     | erase <file-id>...
                     
                     # operations on bags outside a bag store
                     | prune <bag-dir> <ref-bag-id>...
                     | complete <bag-dir>
                     | validate <bag-dir>
                     
                     # Start as HTTP service
                     | run-service
                          

DESCRIPTION
-----------
A bag store is a way to store and identify data packages following a few very simple rules. See the [bag-store] page
for a quasi-formal description and see the [tutorial] page for a more informal, hands-on introduction. The `easy-bag-store` 
command line tool and HTTP-service facilitate the management of one or more bag stores, but use of these tools is optional; 
the whole point of the bag store concept is that it should be fairly easy to implement your own tools for working with it.

[bag-store]: 03_definitions.html
[tutorial]: 04_tutorial.html

### Command line tool
By using the `easy-bag-store` command you can manage a bag store from the command line. The sub-commands in above 
[SYNOPSIS](#synopsis) are subdivided into two groups:

* Sub-commands that target items in the bag store. These implement operations that change, check or retrieve items in a bag store.
* Sub-commands that target bags outside a bag store. These are typically bags that are intended to be 
  added to a bag store later, or that have just been retrieved from one. These sub-commands still work in the context of one or
  more bag stores, because the bag directories they operate on may contain [local references] to bags in those stores.
  
Some of the sub-commands require you to specify the store context you want to use. The store to operate on can be specified
on one of two ways:

* With the `--store` option. This expects the shortname of a store, which is mapped to a base directory in the `stores.properties`
  configuration file.
* By specifying the base directory directly, using the `--base-dir` option.

If you call a sub-command that requires a store context, without providing one, you are prompted for a store shortname.

### HTTP service
`easy-bag-store` can also be executed as a service that accepts HTTP requests, using the sub-command `run-service`. `initd` and
`systemd` scripts are provided, to facilitate deployment on a Unix-like system (see [INSTALLATION AND CONFIGURATION](#installation-and-configuration)).

For details about the service API see the [OpenAPI specification].

[OpenAPI specification]: ./api.html
[local references]: 03_definitions.html#local-item-uri

ARGUMENTS
---------

      -b, --base-dir  <arg>     BagStore base-dir to use
      -s, --store-name  <arg>   Configured store to use
          --help                Show help message
          --version             Show version of this program
    
    Subcommand: list - Lists the bag-stores for which a shortname has been defined. 
    These are the bag-stores that are accessible through the HTTP interface
    
          --help   Show help message
    Subcommand: add - Adds a bag to the bag-store
      -u, --uuid  <arg>   UUID to use as name for the Bag
          --help          Show help message
    
     trailing arguments:
      bag (required)   the (unserialized) Bag to add
    ---
    
    Subcommand: get - Retrieves a Bag or File in it
          --help   Show help message
    
     trailing arguments:
      item-id (required)        ID of the Bag or File to retrieve
      <output-dir> (required)   directory in which to put the Bag or File
    ---
    
    Subcommand: enum - Enumerates Bags or Files
      -a, --all        enumerate all Bags, including inactive ones
      -d, --inactive   only enumerate inactive Bags
          --help       Show help message
    
     trailing arguments:
      <bagId> (not required)   Bag of which to enumerate the Files
    ---
    
    Subcommand: deactivate - Marks a Bag as inactive
          --help   Show help message
    
     trailing arguments:
      <bag-id> (required)   Bag to mark as inactive
    ---
    
    Subcommand: reactivate - Reactivates an inactive Bag
          --help   Show help message
    
     trailing arguments:
      <bag-id> (required)   Inactive Bag to re-activate
    ---
    
    Subcommand: prune - Removes Files from Bag, that are already found in 
    reference Bags, replacing them with fetch.txt references
          --help   Show help message
    
     trailing arguments:
      <bag-dir> (required)         Bag directory to prune
      <ref-bag-id>... (required)   One or more bag-ids of Bags in the BagStore to
                                   check for redundant Files
    ---
    
    Subcommand: complete - Resolves fetch.txt references from the BagStore 
    and copies them into <bag-dir>
          --help   Show help message
    
     trailing arguments:
      <bag-dir> (required)   Bag directory to complete
    ---
    
    Subcommand: validate - Checks that <bag-dir> is a virtually-valid Bag
          --help   Show help message
    
     trailing arguments:
      <bag-dir> (required)   Bag directory to validate
    ---
    
    Subcommand: run-service - Starts the EASY Bag Store as a daemon that 
    services HTTP requests
          --help   Show help message
    ---


INSTALLATION AND CONFIGURATION
------------------------------
The preferred way of install this module is using the RPM package. This will install the binaries to
`/opt/dans.knaw.nl/easy-bag-store`, the configuration files to `/etc/opt/dans.knaw.nl/easy-bag-store`,
and will install the service script for `initd` or `systemd`. It will also set up a default bag store
at `/srv/dans.kanw.nl/bag-store`.

If you are on a system that does not support RPM, you can use the tarball. You will need to copy the
service scripts to the appropiate locations yourself.

BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* RPM (if you want to build the RPM package).

Steps:

    git clone https://github.com/DANS-KNAW/easy-bag-store.git
    cd easy-bag-store
    mvn install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM 
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.