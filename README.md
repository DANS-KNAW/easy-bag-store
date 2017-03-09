easy-bag-store
==============
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-bag-store.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-bag-store)

Manage one or more BagStores


SYNOPSIS
--------

    easy-bag-store [--base-dir,-b <dir>|--store,-s <name>]
                     # operations on bags in BagStore
                     | add <bag> [<uuid>]
                     | get <item-id>
                     | enum [--inactive,-i|--all,-a] [<bag-id>]
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

A BagStore is a way to store and identify data packages following a few very simple rules. 

### Structure
1. **DEFINITION**: a **BagStore** is a collection of immutable **Bag**s (see [BagIt]) stored on a 
   hierarchical files system, under a common directory, the **base-dir**.
2. **VIRTUALLY-VALID**: all the the Bags in the BagStore must be **virtually-valid**. A Bag is
   virtually-valid when:
    - it is valid ([as defined by the BagIt specs]), or
    - it is incomplete, but contains a `fetch.txt` file and can be made valid by fetching the files
      listed in it (and removing `fetch.txt` and its checksums from the Bag). If local-item-uris (see
      next point) are used, they must references Items in the same BagStore.
3. **ITEM-ID:** an **Item** is a Bag or a **File** in a Bag. Each Item has an **item-id**.
    - **bag-id** = `<uuid>`, that is a [UUID]
    - **file-id** = `<bag-id>/percent-encoded(path-in-bag)`, where **percent-encoded** means that the 
      path-components are percent encoded as described in [RFC3986] and **path-in-bag** is the relative
      path of the File in the bag, after `fetch.txt` has been resolved.
    
    Each Item also has a locally resolvable URI:

    - **local-item-uri** = `http://localhost/``<item-id>`

    (Globally resolvable URIs may also be defined for Items. How these are mapped to **item-id**s is
    up to the implementor of the BagStore.)
4. **ITEM-LOCATION:** each Item has a defined **item-location** on the file system:
    - **bag-location** = `<base-dir>/slashed(<uuid>)/[.]<bag-base-dir>`, where **slashed** means that
      the UUID is stripped of hyphens and then subdivided into several groups of characters, with the
      slash as separator. The sizes of the groups of characters must be the same throughout the
      BagStore, so that it can be easily ascertained. The dot is inserted if the Bag is inactive (see
      point 5). **bag-base-dir** is the Bag's base directory.
    - **file-location** = `<bag-location>/<path-in-bag>`. If a File is included through the `fetch.txt`
      file, its actual location can be obtained by resolving its URL in `fetch.txt`.
5. **INACTIVE**: a Bag is either **active** or **inactive**. It starts off active. The item-ids of a Bag       
   and the Files it contains stay the same whether the Bag is active or inactive. 

[BagIt]: https://tools.ietf.org/html/draft-kunze-bagit
[as defined by the BagIt specs]: https://tools.ietf.org/html/draft-kunze-bagit#section-3
[UUID]: https://en.wikipedia.org/wiki/Universally_unique_identifier
[RFC3986]: https://tools.ietf.org/html/rfc3986#section-2.1

### Operations
On a BagStore the following operations are allowed:

* `ADD` - add a new, virtually-valid Bag to the BagStore.
* `ENUM` - enumerate all the Items in the BagStore or all the items in one Bag.
* `GET` - copy an Item from the BagStore.
* `DEACTIVATE` - mark a Bag as incorrect or not fit for dissemination in some way.
* `REACTIVATE` - reverse a deactivation.
* `ERASE` - erase the contents of a particular Bag **payload** File, and update the corresponding 
   entries in any affected payload manifests accordingly. 

The `DEACTIVATE` operation marks a Bag a inactive. This involves marking the Bag directory as hidden 
by prepending its name with a dot. Note that this operation does not require copying or modifying any 
File data. The only "file" that is modified is the directory containing the Bag. On some file systems 
this may still require write-privileges on the bag-base-dir.

The `ERASE` operation is the one exception to the rule that Bags are immutable. It should obviously 
be used sparingly and with care and instead of leaving the File empty it should contain a 
"tombstone message" specifying the author, date and reason for the erasure. Unless some legal 
obligation exists to destroy the data, deleting content from a Bag should be realized by creating a 
new revision that does not include that content, possibly hiding the earlier revisions.

### Summary of Structure and Operations

The following diagram summarizes the structure and operations of a BagStore.

![bag-store](./bag-store.png)   


### Migrations

A **Migration** is a BagStore-wide transformation. The input of a Migration is always one or more BagStores. The output
may in principle be anything depending on the Migration procedure. Below we will define some Migrations whose outputs are also one or
more BagStores. Migrations are riskier than normal operations and should normally be avoided.


#### Merge BagStores

Merging two BagStores can be done in at most two steps:

1. Harmonize the slashing settings of both BagStores. (This can of course be equal to the settings of one of the existing BagStores.)
2. Copy the base-dirs and their contents to the new base-dir. (Again, this can be one of the existing base-dirs.)

If the slashing settings are already equal, this step can be foregone.


#### Split BagStore

Splitting a BagStore is a bit more involved, because BagStores must not include files by reference from other BagStores. It is therefore
necessary to determine which bags form a self-contained sub-set:

1. Determine which bags you want to split off.
2. For each bag determine if it has `fetch.txt` that includes Files, add the Bag that actually contains them to the BagStore to be
   split off, recursively.

#### Change bag-id slashing

This Migration only changes directory names:

1. From base-dir downwards find the last directory-level that has the desired name-length already. 
2. In each directories at this level:
   
   1. For each bag contained in the directory:

      1. Remove the slashes from the remaining path to the bag
      2. Insert the slashes in the remaining path in the new positions
      3. Move the bag to the location pointed to by the new remaining path.


### Technical considerations

#### Slashing the bag-id

The rationale for this is that some file systems suffer a performance loss when a directory gets overfull. By distributing the
contents of the BagStore over subdirectories this problem is avoided. How deep to make the directory tree depends on the
number of (expected) bags. However, note that it is relatively easy to change this later on, with a series of move operations.
In most (all?) file systems this means no data needs to be actually copied if the BagStore is contained in a single 
storage volume.


INSTALLATION AND CONFIGURATION
------------------------------

(These instructions presume that you are working on a Unix-like OS, such as Linux, BSD or Mac OSX. The 
scripts could, however, probably adapted for other environments, such as Windows.)

1. Unzip the tarball to a directory of your choice, typically `/usr/local/`
2. A new directory called easy-bag-store-<version> will be created
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from 
   a directory that is on the path, e.g. 
   
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
