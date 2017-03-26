easy-bag-store
==============
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-bag-store.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-bag-store)

Manage one or more BagStores


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
                          

DESCRIPTION
-----------

A BagStore is a way to store and identify data packages following a few very simple rules. See the [BagStore] page
for a description. The `easy-bag-store` command line tool and REST-service facilitate the management of one or
more BagStores.

[BagStore]: bag-store.md


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
   when using `systemd`.
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
