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
the whole point of the bag store concept is that it should be fairly easy to implement your own tools.

[bag-store]: bag-store.html
[tutorial]: tutorial.html

### Command line tool
By using the `easy-bag-store` command you can manage a bag store from the command line. The sub-commands in above 
[SYNOPSIS](#synopsis) are subdivided into two groups:

* Sub-commands that target items in the bag store. These implement operations that change, check or retrieve items in a bag store.
* Sub-commands that target bag directories outside a bag store. These are typically bag directories that are intended to be 
  added to a bag store later, or that have just been retrieved from one. These sub-commands still work in the context of one or
  more bag stores, because the bag directories they operate on may contain local references to bags in those stores.
  
Some of the sub-commands require you to specify the store context you want to use. The store to operate on can be specified
on one of two ways:

* With the `--store` option. This expects the shortname of a store, which is mapped to a base directory in the `stores.properties`
  file.
* By specifying the base directory directly, using the `--base-dir` option.

If you call a sub-command that requires a store context, without providing one, you are prompted for a store shortname.

### HTTP service
`easy-bag-store` can also be executed as a service that accepts HTTP requests, using the sub-command `run-service`. `initd` and
`systemd` scripts are provided, to facilitate deployment on a Unix-like system (see [INSTALLATION AND CONFIGURATION](#installation-and-configuration)).

For details about the service API see the [OpenAPI specification].

[OpenAPI specification]: ./api.html


INSTALLATION AND CONFIGURATION
------------------------------

(These instructions presume that you are working on a Unix-like OS, such as Linux, BSD or Mac OSX. The 
scripts could, however, probably adapted for other environments, such as Windows.)

1. Unzip the tarball to a directory of your choice, typically `/usr/local/`.
2. A new directory called `easy-bag-store-<version>` will be created.
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from 
   a directory that is on the path, e.g.,
   
        ln -s /usr/local/easy-bag-store-<version>/bin/easy-bag-store /usr/bin/easy-bag-store
4. Install the service script by copying the `bin/easy-bag-store-initd.sh` script to `/etc/init.d/easy-bag-store`
   (when on a system that uses `initd`) or `bin/easy-bag-store.service` to the appropriate directory
   when using `systemd`. For `initd` `jsvc` must be installed on your system.
5. Enable and start the `easy-bag-store` service.

General configuration settings can be set in `cfg/application.properties` and logging can be configured
in `cfg/logback.xml`. The available settings are explained in comments in aforementioned files.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher

Steps:

        git clone https://github.com/DANS-KNAW/easy-bag-store.git
        cd easy-bag-store
        mvn install
