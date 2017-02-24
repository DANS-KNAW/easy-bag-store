easy-bag-store
==============
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-bag-store.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-bag-store)

Manage a bag store


SYNOPSIS
--------

    easy-bag-store [--base-dir|-b <bag-dir>] \
                     # operations on bags in bag-store
                     | add <bag> <uuid>
                     | get <item-id>
                     | enum [--hidden|--all] [<bag-id>]
                     | deactivate <bag-id>
                     | reactivate <bag-id>
                     | verify [<bag-id>]
                     | erase {--authority-name|-n} <name> {--authority-password|-p} <password> \
                          {--tombstone-message|-m <message>} <file-id>
                     
                     # operations on bags outside bag-store
                     | prune <bag-dir> <ref-bag-id>...
                     | complete <bag-dir>
                     | validate <bag-dir>
                          

DESCRIPTION
-----------

A BagStore is a way to store and identify data packages following a few very simple rules. The Files in a BagStore are in principle 
immutable after they have been added.

### Structure
1. **DEFINITION:** a **BagStore** is a collection of immutable **Bag**s (see [BagIt]) stored on a hierarchical file system under a common
   directory (**base-dir**) and with a common **base-uri**. The base-dir is particular to an individual BagStore, but the 
   base-uri may be shared with other BagStores. The base-uri may be context-dependent, e.g. `http://localhost/`.
2. **VIRTUALLY VALID:** the Bags must be **virtually-valid**. This means they are either valid already ([as defined by the BagIt specs]), or
   they are incomplete, but contain a `fetch.txt` file and can be made valid by dereferencing the URIs in it. By 
   using **file-uri**s (see point 3 below) in `fetch.txt`, storing files redundantly can be avoided. A Bag must not include 
   files from other BagStores in this way.
3. **ITEM-ID:** there are two types of identifiable **Item**s that are stored in a BagStore: Bags and **File**s.
   Their respective **item-id**s are constructed in the following manner:
    * **bag-id** ::= `<uuid>`, that is a [UUID]
    * **file-id** ::= `<bag-id>/percent-encoded(path-in-bag)`, where **percent-encoded** means that the path-components are percent
      encoded as described in [RFC3986] and **path-in-bag** is the relative path of the File in the bag.

    Note that Files that are include via a `fetch.txt` reference are considered part of the target Bag. 
    
    Each Item has an **item-uri** (**bag-uri**, **file-uri**), constructed as follows:
     
    * **item-uri** ::= `<base-uri>/<item-id>`.
4. **ITEM LOCATION:** each Item has a defined **item-location** on the file system:
    * **bag-location** ::= `<base-dir>/slashed(<uuid>)/[.]<bag-base-dir>`, where **slashed** means that the UUID is stripped of hyphens and then subdivided into several 
      groups of characters, with the slash as separator. The sizes of the groups of characters must be the same throughout the BagStore, so that it can be easily ascertained.
      The dot is inserted if the Bag is hidden (see point 5). **bag-base-dir** is the Bag's base directory.
    * **file-location** ::= `<bag-location>/<path-in-bag>`
5. **HIDDEN BAG:** a Bag is either **visible** or **hidden**; by default it is visible. The item-uris of a Bag and the files it contains stay the same wen the Bag changes 
   from visible to hidden. However, requests for a hidden Bag from the outside world should result in a `410 Gone` response. Note that the actual file may still
   be accessible through other URIs, if it is included in a visible Bag through a `fetch.txt` reference.
    

[BagIt]: https://tools.ietf.org/html/draft-kunze-bagit
[as defined by the BagIt specs]: https://tools.ietf.org/html/draft-kunze-bagit#section-3
[UUID]: https://en.wikipedia.org/wiki/Universally_unique_identifier
[RFC3986]: https://tools.ietf.org/html/rfc3986#section-2.1

#### Examples

The examples below use the following settings for BagStore:

* **base-dir** = `/data/bag-store`
* **base-uri** = `http://example-archive.org`
* **slashing** : directory name of 2 characters, followed by one of 30 characters.
* **bag-base-dir**: `example-bag


item-id, item-uri and item-location of a Bag:

Datum          | Example
---------------|------------------------------------------------------------------------------------
 bag-id        | `ce4cb5ed-f99b-4709-a7d3-7fe30426de81`                                  
 bag-uri       | `http://example-archive.org/ce4cb5ed-f99b-4709-a7d3-7fe30426de81`       
 bag-location  | `/data/bag-store/ce/4cb5edf99b4709a7d37fe30426de81/example-bag`                               

item-id, item-uri and item-location of a File:

Datum          | Example
---------------|------------------------------------------------------------------------------------
 file-id       | `ce4cb5ed-f99b-4709-a7d3-7fe30426de81/data/example/my%20file.txt`                                  
 file-uri      | `http://example-archive.org/ce4cb5ed-f99b-4709-a7d3-7fe30426de81/data/example/my%20file.txt`       
 file-location | `/data/bag-store/ce/4cb5edf99b4709a7d37fe30426de81/example-bag/data/example/my file.txt`
                                
item-id, item-uri and item-location of a File in a hidden Bag:                                
 
Datum          | Example
---------------|------------------------------------------------------------------------------------
 file-id       | `ce4cb5ed-f99b-4709-a7d3-7fe30426de81/data/example/my%20file.txt`                                  
 file-uri      | `http://example-archive.org/ce4cb5ed-f99b-4709-a7d3-7fe30426de81/data/example/my%20file.txt`       
 file-location | `/data/bag-store/ce/4cb5edf99b4709a7d37fe30426de81/.example-bag/data/example/my file.txt`                                
 

### Operations

On a bag store the following operations are allowed:

* `ADD` - add a new, virtually-valid Bag to the BagStore
* `ENUM` - enumerate all the Items in the BagStore.
* `GET` - copy an Item from the BagStore.
* `HIDE` - mark a Bag as incorrect or not fit for dissemination in some way.
* `ERASE` - erase the contents of a particular Bag **payload** File, and update the corresponding entries in the payload manifests
  accordingly. 

The `HIDE` operation marks a Bag a hidden. This involves marking the Bag directory as hidden by prepending its name with 
a dot. Note that this operation does not require copying or modifying any File data. The only "file" that is modified
is the directory containing the Bag. On some file systems this may still require write-privileges on the bag-base-dir.

The `ERASE` operation is the one exception to the rule that Bags are immutable. It should obviously be used sparingly 
and with care and instead of leaving the File empty it should contain a "tombstone message" specifying the author, date and 
reason for the erasure. Unless some legal obligation exists to destroy the data, deleting content from a Bag should be 
realized by creating a new revision that does not include that content, possibly hiding the earlier revisions.

### Summary of Structure and Operations

The following diagram summarizes the structure and operations of a BagStore.

![bag-store](bag-store.png)   


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


OPEN QUESTIONS AND CONSIDERATIONS
---------------------------------

* `ERASE` must also update the manifests of the Bags that include the erased file by reference.
* Support for serialized bags (ARC or TAR)?
