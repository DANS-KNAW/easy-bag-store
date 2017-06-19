easy-bag-store
==============
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-bag-store.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-bag-store)

Manage one or more bag stores


SYNOPSIS
--------

    easy-bag-store [--base-dir,-b <dir>|--store,-s <name>]
                     # operations on bags in BagStore
                     | add <bag> [<uuid>]
                     | get <item-id> <out-location>
                     | enum [[--inactive,-i|--all,-a] <bag-id>]
                     | deactivate <bag-id>
                     | reactivate <bag-id>
                     | verify [<bag-id>]
                     | erase --authority-name,-n <name> --authority-password,-p <password> 
                         --tombstone-message,-m <message> <file-id>...
                     
                     # operations on bags outside BagStore
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
it should be fairly easy to implement your own tools.

[bag-store]: bag-store.md
[tutorial]: tutorial.md

### Command line tool
By using the `easy-bag-store` command you can manage a BagStore from the command line. The sub-commands in above 
[SYNOPSIS](#synopsis) are subdivided into two groups:

* Sub-commands that target items in the bag store. These implement operations that change, check or retrieve items in a bag store.
* Sub-commands that target bag directories outside a bag store. These are typically bag directories that are intended to be 
  added to a bag store later, or that have just been retrieved from one. These sub-commands still work in the context of one or
  more bag stores, because the bag directories they operate on may contain local references to bags in those stores.
  
Some of the sub-commands require you to specify the store context you want to use. The store to operate on can be specified
on one of two ways:

* With the `--store` option. This expects the shortname of store, which is mapped to a base directory in the `stores.properties`  
  file.
* By specifying the base directory directly, using the `--base-dir` option.

If you call a sub-command that requires a store context, withouth providing one, you are prompted for a store shortname.

### HTTP service
`easy-bag-store` can also be executed as a service that accepts HTTP requests, using the sub-command `run-service`. `initd` and
`systemd` scripts are provided, to ease deployment on a Unix-like system (see [INSTALLATION AND CONFIGURATION](#installation-and-configuration)).

The following table summarizes the HTTP API. "Method" refers to the HTTP method used in the request. "Path" is the path pattern used.
In a path pattern `*` refers to any completion of the path, and placeholders for variables start with a colon. Optional part are enclosed
in square brackets, a pipe symbol denotes alternatives and parentheses are used to group those alternatives.

Method | Path                                             |Action
-------|--------------------------------------------------|------------------------------------
`GET`  | `/`                                              | Return a simple message to indicate that the service is up: "EASY Bag Store is running ..."
`GET`  | `/bags[?state=(inactive\|all)]`                  | Enumerate bags in all stores. Only active bags are returned, unless specified otherwise by the `state` query parameter.
`GET`  | `/bags/:uuid`                                    | Enumerate files from the bag with bag-id `:uuid`.
`GET`  | `/bags/:uuid/*`                                  | Get file with bag local path `*` from bag with bag-id `:uuid`, from any bag store.
`GET`  | `/stores`                                        | Enumerate all bag stores managed by this service.     
`GET`  | `/stores/:bag-store/bags[?state=(inactive\|all)]`| Enumerate bags in `:bag-store`.
`GET`  | `/stores/:bag-store/bags/:uuid`                  | Enumerate files from bag with bag-id `:uuid`, but only look in `:bag-store` .
`GET`  | `/stores/:bag-store/bags/:uuid/*`                | Get file with bag local path `*` from bag with bag-id `:uuid`, but only from `:bag-store`.
`PUT`  | `/stores/:bag-store/bags/:uuid`                  | Add the body of the request to `:bag-store` under bag-id `:uuid`.


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
