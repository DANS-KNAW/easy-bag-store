---
title: Tutorial 
layout: page
---

Table of contents
-----------------

- [Motivation](#motivation)
- [Prerequisites](#prerequisites)
- [Tutorial](#tutorial)
  * [Starting up](#starting-up)
  * [Adding a bag](#adding-a-bag)
    + [Enter: bag store operations](#enter--bag-store-operations)
  * [Retrieving an item](#retrieving-an-item)
    + [A bag](#a-bag)
    + [A single file](#a-single-file)
    + [A directory](#a-directory)
  * [Adding an updated bag](#adding-an-updated-bag)
    + [Virtually-valid](#virtually-valid)
    + [Pruning](#pruning)
    + [Round-trip: adding, retrieving, completing](#round-trip--adding--retrieving--completing)
    + [Other operations and commands](#other-operations-and-commands)
  * [Using the HTTP service](#using-the-http-service)
    + [Getting the list of bag stores](#getting-the-list-of-bag-stores)
    + [Enumerating bags and files](#enumerating-bags-and-files)
    + [Retrieving one file](#retrieving-one-file)
    + [Retrieving a bag or a directory](#retrieving-a-bag-or-a-directory)
    + [Adding bag](#adding-bag)
    + [Adding an updated bag](#adding-an-updated-bag-1)
- [Appendix I: extended motivation of features](#appendix-i--extended-motivation-of-features)
  * [Simple](#simple)
  * [Open standards](#open-standards)
  * [Software independent](#software-independent)
  * [Authenticity](#authenticity)
  * [Efficiency](#efficiency)
  * [Modular design](#modular-design)
- [Appendix II: Migrations](#appendix-ii--migrations)

Motivation
----------
We created the bag store here at [DANS], because we needed a way to store our archival packages. Our 
existing solution was becoming hard to maintain and evolve, so we decided to go back to the drawing board.
We were looking for something with the following properties:

* **Simple** 
* Based on open **standards**
* **Independent** from any particular repository software
* Support archival package **authenticity**
* Reasonably storage **efficient**
* Extensible, **modular** design 
 
For our [SWORDv2 deposit service] we had already decided on [BagIt] as the exchange format. The simplest
we could come up with, therefore, was just storing these bags in a directory on the file system. This is 
essentially what a bag store is. However, to support all the above properties we had to put on some 
additional constraints. As we go along I will introduce these constraints one by one and explain how 
they support the properties. I shall use the high-lighted words a mnemonics to refer back to the items
on the list. 

[Appendix I] tries to give a more elaborate justification for above list of properties.  

[DANS]: https://dans.knaw.nl/en 
[SWORDv2 deposit service]: https://easy.dans.knaw.nl/doc/sword2.html
[BagIt]: https://tools.ietf.org/html/draft-kunze-bagit
[Appendix I]: #appendix-i-extended-motivation-of-features

Prerequisites
-------------
You will need the following software. Newer versions will probably also work, but I have tested the tutorial
with the versions specified here:

* [Vagrant 2.0.0](https://releases.hashicorp.com/vagrant/2.0.0/)
* [VirtualBox 5.1.26](https://www.virtualbox.org/wiki/Changelog-5.1#v26)

Tutorial
--------
### Starting up
1. Create a directory for this tutorial somewhere on your filesystem and start the vagrant project. 
   
       mkdir easy-bag-store-tutorial
       cd easy-bag-store-tutorial
       vagrant init https://easy.dans.knaw.nl/boxes/easy-bag-store-tutorial-2017-12-11.box
       vagrant up

   The first time you run this tutorial, this will have to download the ~800M vagrant box so&mdash;depending on
   your internet connection&mdash;that may take some minutes. If everything goes well, you will end up with
   a virtual machine running CentOS 6 Linux and with `easy-bag-store` installed on it. Don't worry if using
   Linux in your organization is not an option: the bag store does not *require* Linux; `easy-bag-store` 
   only requires Java 8 or higher, and the bag store itself only requires a hierarchical file system.
   
2. Now test that it is working; while in the `easy-bag-store-tutorial` directory type:

        vagrant ssh   
   
   This will log you in to the virtual machine, just as if you were logging in to a remote server. 
   You should now see a prompt like this:
   
        Last login: Sun Oct 29 05:36:33 2017 from 10.0.2.2
        [vagrant@test ~]$
        
   The server's name is `test` and your user is called `vagrant`. You are currently in the user's 
   home directory. If you type `pwd` (print working directory) and Enter, you will see this:
    
        [vagrant@test ~]$ pwd
        /home/vagrant
        [vagrant@test ~]$
        
   From now on I will mark (expected) output with a `>`, like this:
     
        pwd
        > /home/vagrant    
        
   (Don't do this now, but: you can leave the VM by type `exit` and Enter and you can stop it with 
   `vagrant halt` from the `easy-bag-store-tutorial` directory. To start it again use `vagrant up`.)     
        
        
### Adding a bag     
1. First, we shall need some sample data. To make it a bit easier to use this tutorial we will use 
   a data package called sample.zip, but by all means experiment with other data.
2. Download [sample.zip](./res/sample.zip) and place it in `easy-bag-store-tutorial` directory. 
   This directory is shared between your PC or laptop and the VM. On the VM it is the directory called `/vagrant/`.
3. Now, in your ssh-session, type the following:

        unzip /vagrant/sample.zip
        > Archive:  /vagrant/sample.zip
           creating: sample/
          inflating: sample/README.TXT
           creating: sample/img/
          inflating: sample/img/image01.png
          inflating: sample/img/image02.jpeg
          inflating: sample/img/image03.jpeg
           creating: sample/path/
           creating: sample/path/with a/
           creating: sample/path/with a/space/
         extracting: sample/path/with a/space/file1.txt
         extracting: sample/path/with a/space/檔案.txt
         
   The last file has a Chinese file name, which can only be displayed by your terminal if it has fonts that include
   Chinese characters. Otherwise they will probably show up as questions marks on your screen.           
         
4. Next, we will turn the newly created `sample` directory into a bag. For this we will use a tool 
   that was created by [Library Of Congress]. It is already installed on the VM. Type the following command:
   
        bagit baginplace sample
        
   The contents of `sample` has been moved to a subdirectory called `data`. The base
   directory will contain files required by the BagIt format. To view the structure of the bag
   use the `tree` utility:
   
        tree sample
        > sample
            ├── bag-info.txt
            ├── bagit.txt
            ├── data
            │   ├── img
            │   │   ├── image01.png
            │   │   ├── image02.jpeg
            │   │   └── image03.jpeg
            │   ├── path
            │   │   └── with a
            │   │       └── space
            │   │           ├── file1.txt
            │   │           └── \346\252\224\346\241\210.txt
            │   └── README.TXT
            ├── manifest-md5.txt
            └── tagmanifest-md5.txt

   Note that `tree` apparently does't try to render the Chinese characters, but instead displays the 
   UTF-8 byte sequences that encode them as octal numbers. 

5. At this point, we are ready to add the bag to the store:

        easy-bag-store add sample    
        > OK: Added bag with bag-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43 to \
           bag store: /srv/dans.knaw.nl/bag-store
   
   The bag-id will of course be different in each case. You may also specify a UUID yourself, 
   using the `-u` option (`easy-bag-store add -u <your UUID> sample`). The great thing about 
   UUIDs is that they can be minted in a decentralized fashion while maintaining the uniqueness
   guarantee.

6. The default bag store is located in the directory `/srv/dans.knaw.nl/bag-store`. Check that
   the bag was copied into the bag store:
   
        tree /srv/dans.knaw.nl/bag-store
        > /srv/dans.knaw.nl/bag-store
        └── 8e
            └── eaeda43ae74be29f633db09b19db43
                └── sample
                    ├── bag-info.txt
                    ├── bagit.txt
                    ├── data
                    │   ├── img
                    │   │   ├── image01.png
                    │   │   ├── image02.jpeg
                    │   │   └── image03.jpeg
                    │   ├── path
                    │   │   └── with a
                    │   │       └── space
                    │   │           ├── file1.txt
                    │   │           └── \346\252\224\346\241\210.txt
                    │   └── README.TXT
                    ├── manifest-md5.txt
                    └── tagmanifest-md5.txt
                  
   As you can see, the bag-id is used to form a path from the bag store base directory to a container in which
   the bag is stored. The slashes must be put in the same places for all the bags in a bag store. So, in this case,
   the first two characters of the bag-id form the name of the parent directory and the rest (stripped of dashes)
   the child. Note that this "slashing pattern" strictly speaking doesn't need to be stored anywhere, as it 
   will be implicitly recorded when adding the first bag. However, the `easy-bag-store` tool does use a configuration
   setting for this, as "discovering" this every time would be inefficient.                
        
Wasn't that great? And it took only six steps and three pages to explain! At this point you may be thinking you might just 
as well have copied the bag to the given path yourself, and that is quite true. Actually, that is the point of the bag store:
to be so simple that manual operation would be feasible. This is how we attain the **independence** from
any repository software that we talked about earlier.

#### Enter: bag store operations
This is a good moment to introduce the rules of the bag store, because not only do we want it to be **simple** but also to
support archival package **authenticity**. That is why we limit the allowed operations to the following: 

* `ADD` - add a *valid* bag (actually a "virtually-valid" bag, but we will come to that).
* `GET` - read any part of the bag store.
* `ENUM` - enumerate the bags and/or files in a bag store. 

Note that there is no <del>`MODIFY`</del>. By making the bag store "add-only"&mdash;carving the added bags in stone, as 
it were&mdash;we are making it easier to guarantee the authenticity of the recorded data&mdash;at least, at the bit level. We might
set the permissions on the archived files to read-only and even flag them as immutable.   

This *does* mean, however, that if we should happen to add an invalid bag, we corrupt the bag store. So, 
while manual operation is feasible in theory, in practise you would probably soon be developing some scripts to:

* verify that the bag you are about to add is (virtually) valid;
* change the file permissions of the contents of the bag to read-only, so as to prevent accidental modification;
* convert the UUID to a path correctly.

These are precisely the things that `easy-bag-store add` does for you! To mess up, you now really have to make
a conscious effort. 

So, let's now move on to an even simpler task: retrieving an item.

[Library Of Congress]: https://github.com/LibraryOfCongress/bagit-java

### Retrieving an item
To retrieve a bag or any part of it, we could actually simply read it from disk, and that would not violate
the bag store rules. However, when referring to bag store items (bags, or files and directories in them) it 
is often not convenient to use local paths. That is why they have **item-id**s.

The item-id of a bag (= bag-id) is the UUID under which it was stored. The item-id of a file or directory is 
the bag-id with the percent-encoded file path appended to it. [Percent-encoding] is a way to map the path to 
a string containing only a subset of ASCII, particularly characters that can be used as part of a URI. The actual 
path may of course contain non-ASCII characters. The character encoding should be UTF-8.

This way we further support **authenticity** because we have a simple mapping between the exact location where 
each item is stored and its global identifier. The only extra information we need is the base directory of
the bag store. Also note that we build on the open **standard**s by using the percent encoding scheme from the 
URI definition (RFC3986) .

We can use `easy-bag-store` to find an item for us.

[Percent-encoding]: https://tools.ietf.org/html/rfc3986#section-2.1

#### A bag
1. Let's first enumerate the bags in the store.

        easy-bag-store enum
        > 8eeaeda4-3ae7-4be2-9f63-3db09b19db43
          OK: Done enumerating
          
   In your case the bag-id will of course be different and you will need to use the one from your output
   rather than the one given above.       
          
2. Copy the bag to some output directory:

        easy-bag-store get 8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        > FAILED: Output path already exists; not overwriting /home/vagrant/./sample
        

3. Yes, that is right. By default `easy-bag-store get` will try to copy the itme to the current directory.
   However, that would overwrite the existing directory `sample`, which is probably not what you want.
   You can specify a different output directory with the `-d` option:
   
        easy-bag-store get -d out 8eeaeda4-3ae7-4be2-9f63-3db09b19db43

    The `out` directory will be created if it doesn't exist yet.
        
3. Now to check that the bag you retrieved is equal to the one you added:

        diff -r sample out/sample        

   No output here is good. It means the directories are the same. 

#### A single file
1. Let's start by enumerating the files in our bag. This has the benefit that we don't have to 
   perform the percent-encoding ourselves:
   
        easy-bag-store enum 8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        > 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/bag-info.txt
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/bagit.txt
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/README.TXT
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image01.png
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image02.jpeg
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03.jpeg
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/file1.txt
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/%E6%AA%94%E6%A1%88.txt
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/manifest-md5.txt
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/tagmanifest-md5.txt
          OK: Done enumerating
          
    Notice that the Chinese characters and spaces appear percent-encoded. Also note that the bag
    name ("sample" in this case) is not part of the identifier.      
            
2. Select one of the item-ids from the output and:

        easy-bag-store get \
            8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03.jpeg
        > OK: Retrieved item with \
          item-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03.jpeg to \
          ./image03.jpg from bag store: default     
             
    We have now copied the selected file to the current directory, which you can check with a simple
    `ls` call.

#### A directory
1. What if we want to get all the files in a specific directory, say, `sample/data/img`? Let's try that.

        easy-bag-store get 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img    
        > FAILED: Source '/srv/dans.knaw.nl/bag-store/8e/eaeda43ae74be29f633db09b19db43/sample/data/img' \
          exists but is a directory
          
   Alas, this hasn't been implemented yet! However, there is an alternative way of getting items, which
   also supports getting directories: *archive streams*.

2. To get the directory as a [TAR] archive stream, execute the following:

        easy-bag-store stream -f tar 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img > img.tar
        > OK: Retrieved item with item-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img to stream.
        
3. This will also work for complete bags and single files. Don't forget to redirect the stream to 
   a file, or the TAR will be printed to your screen. You might also pipe the stream into the `tar`
   extract command:
   
        easy-bag-store stream -f tar 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img | tar x
        > OK: Retrieved item with item-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img to stream.
        
        ls -l img
        > total 3144
          -rw-r--r--. 1 vagrant vagrant  422887 Sep 25 07:59 image01.png
          -rw-r--r--. 1 vagrant vagrant   13829 Sep 25 07:59 image02.jpeg
          -rw-r--r--. 1 vagrant vagrant 2775738 Sep 25 07:59 image03.jpeg
       
   This will extract the `img` directory in your current working directory, so effectively does what we
   where trying to achieve at the beginning of this section.<sup>[1](#footnote1)<sup>
   
    
           
[TAR]: https://en.wikipedia.org/wiki/Tar_(computing)


### Adding an updated bag
Now guarding **authenticity** of your archival packages by disallowing any changes to them may seem
a bit draconic. In the real world packages do get updated to correct errors, add newly available data, etc.
How do we deal with that? The answer is: *in the simplest possible way; by adding a new version*. 

Keeping track of the versions is not built in to the bag store, but here is were **modular** design comes in:
you can add that capability by simply adding appropriate metadata. That could be something as simple
as a "version" metadata field to the `bag-info.txt` file, or something a bit more sophisticated. 
Our [`easy-bag-index`] module records both a timestamp and a pointer to the base revision, the combination 
of which is always enough to reconstruct the version history.

[`easy-bag-index`]: https://github.com/DANS-KNAW/easy-bag-index

#### Virtually-valid
An objection to such an approach could be, that you would be storing a lot of files 
redundantly. After all, a package update may leave many files unchanged. The bag store supports 
the **efficient** storage of collections of bags with common files. Instead of requiring 
every bag to be valid according to [the BagIt definition of valid], it must only be "virtually" valid. 

A bag is virtually-valid when:

* it is valid, *or*...
* it is incomplete, but has a [`fetch.txt`] with references to the missing files.

The idea is that the only things we need to do to make the bag valid are:

1. Download the files referenced from `fetch.txt` into the bag.
2. Remove `fetch.txt` from the bag.
3. Remove any entries for `fetch.txt` in the tag manifests, if present.

If we can prove that this would be enough to make the bag valid, then it is virtually-valid. Note that
this term was introduced by us and is nowhere to be found in the BagIt specifications document.

So, now we can store a new version of an archival package, but for all the files that haven't been
updated, we include a fetch reference to the already archived file. 

[the BagIt definition of valid]: https://tools.ietf.org/html/draft-kunze-bagit#section-3
[`fetch.txt`]: https://tools.ietf.org/html/draft-kunze-bagit-14#section-2.2.3

#### Pruning
OK, enough theory, let's try to create an update for our sample bag. The `easy-bag-store` tool has
a command to help you strip your updated bag of unnecessary files.

1. Copy `sample` to `sample-updated`:

        cp -r sample sample-updated
        
2. Make a change to one of the data files in `sample-updated`, let's say the `README.TXT`:

        echo "...and some more text" >> sample-updated/data/README.TXT

3. Let's also remove a file:
        
        rm sample-updated/data/img/image01.png

4. ...and add one:

        echo "New file content" > sample-updated/data/NEW.TXT

5. Update the checksums in the new bag, so that it is valid again:

        bagit update sample-updated

6. Make a copy of this updated bag, so that we can compare it later with the one we retrieve from the
   bag store:
   
        cp -r sample-updated sample-updated-unpruned

7. Now copy the bag-id of the version we stored earlier (use `easy-bag-store enum` if needed) and 
   use it in the following command:
   
        easy-bag-store prune sample-updated 8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        > OK: Done pruning
        
   Note that we provided as the second argument the bag-id of the bag in which the unchanged files 
   where located. You may append as many bag-ids as you like. We call these bags reference bags or **ref-bag**s.
   
8. Now let's have a look at `sample-updated`:

        tree sample-updated/
        > sample-updated/
            ├── bag-info.txt
            ├── bagit.txt
            ├── data
            │   ├── NEW.TXT
            │   ├── path
            │   │   └── with a
            │   └── README.TXT
            ├── fetch.txt
            ├── manifest-md5.txt
            └── tagmanifest-md5.txt

9. Yes, that's right: *all the other data files are gone*. Now take a look at the contents of `fetch.txt`:

        cat sample-updated/fetch.txt
        > http://localhost/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/%E6%AA%94%E6%A1%88.txt  34  data/path/with a/space/檔案.txt
          http://localhost/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03.jpeg  2775738  data/img/image03.jpeg
          http://localhost/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/file1.txt  0  data/path/with a/space/file1.txt
          http://localhost/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image02.jpeg  13829  data/img/image02.jpeg
          
   All the payload files from `sample` are included in `fetch.txt`, except `README.TXT` (which was
   changed), `NEW.TXT` (which was added) and `image01.png` (which was removed).
   
   
As you may have noticed, the URLs in `fetch.txt` all start with `http://localhost/`. These URLs are called
**local-file-uri**s. They will of course only really work, if&mdash;on this host&mdash;there is a web server to do the
item-to-location mapping  as described earlier (looking in the correct bag store). We *could* set that up, but really what
the local-file-uris are intended to do is point back into the same bag store that the `fetch.txt` file is stored in.
That makes resolving the local-file-uri trivial, as leaving off `http://localhost/` gives you the item-id.

The local-file-uris must never point to files outside the same bag store that their containing `fetch.txt` is in, even 
if we set up the web server to give back the correct file. The reason for this rule is that we want to keep the bag 
store self-contained. This supports **authenticity** of the archival packages. Being able to find all the files 
in a packages obviously is a *sine qua non* for guaranteeing its authenticity. If we cannot even do that, we should 
give up all claims of being a reliable archive.

Note, that fetching items with *non-local* URLs is still sound practice. However, it is essential to consider how the 
persistence of these URLs is guaranteed (and by whom). It *does* pose more challenges than keeping all the packages 
locally in a common base directory.
 
#### Round-trip: adding, retrieving, completing
Finally, to come full circle, we will show that the updated bag can be added, retrieved and completed to its
full content.

1. Add the updated bag:

        easy-bag-store add sample-updated

2. Retrieve it again from the bag store:

        easy-bag-store get -d out <bag-id of sample-updated>
        
3. Verify that `sample-updated-unpruned` and `out/sample-updated` are equal

        diff -r sample-updated-unpruned out/sample-updated

   Again, if you see any output from the last command, that means the directories are different and something 
   went wrong. As you can see, the `get` subcommand will, by default, also complete the bag by fetching any files
   that were pruned away before. You can also skip the "completing" step with the `-s` option. You may then use
   the `complete` subcommand to complete the bag after retrieval from the bag store.

#### Other operations and commands
You have now seen the most important bag store operations in action: `ADD`, `ENUM` and `GET`. The command line
tool implements these as the subcommands `add`, `enum` and `get`/`stream`. Furthermore, you have seen the utility subcommands
`prune` and `complete` that help you convert between virtually-valid and valid bags. For a list of all the available subcommands
type:

        easy-bag-store --help

For more exceptional situations the bag store allows you to `DEACTIVATE` a bag. This will mark it as hidden, while
you can still reference files in it. The main use case for this operation is, when you turn out to have created an
incorrect version history for a sequence of bags, which can of course not be amended by adding more versions. A planned
tutorial in [`easy-bag-index`] will explain this feature. It should probably not be used for anything else.

Support for an `ERASE` operation, which will overwrite the contents of a file with a tombstone message is also planned.
This operation is also *not* intended for regular use cases, but rather for situations where there is a strict legal
obligation to destroy data. However, in such cases a filtering migration might be a better option.

### Using the HTTP service
For simple scenarios, working with the command line interface of the bag store tool is quite sufficient. However, 
when building a complete archival system on top of the bag store, it may be more convenient to have an HTTP based interface.
Fortunately, such an interface exists. We will briefly explore it here. 

The examples work with the command line tool [cURL].

#### Getting the list of bag stores
1. Check that the service is running:

        curl http://localhost:20110/
        > EASY Bag Store is running.
          Available stores at <http://localhost:20110/stores>
          Bags from all stores at <http://localhost:20110/bags>
          
   Even though the current API is rather basic, we have tried to adhere to the [HATEOAS] principle.
       
2. Let's see what bag stores are available:

        curl http://localhost:20110/stores
        > <http://localhost:20110/stores/default>
        
   It turns out there is only one store on this VM. It *is* possible to have multiple stores. One use case for this could
   be when some of your archival packages contain sensitive data that must be stored with an added level of security.
   
3. We follow the link to the one store:

        curl http://localhost:20110/stores/default
        > Bag store 'default'.
          Bags for this store at <http://localhost:20110/stores/default/bags>
   
   We see we can access the bags in this particular bag store. 
 
#### Enumerating bags and files
1. Enumerate all the bags in your bag store:

        curl http://localhost:20110/stores/default/bags
        > <newline-separated list of UUIDs>
        
   You will get the list of all the bags currently in your bag store.

2. Pick one UUID and use it in the following:

        curl http://localhost:20110/stores/default/bags/8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        > Item 8eeaeda4-3ae7-4be2-9f63-3db09b19db43 is not a regular file.
      
3. We need to specify the media type we want to accept. Currently, for a bag you can choose between
   `text/plain`, `application/zip` and `application/x-tar`. `text/plain` will give you a listing of the regular
   files in the bag.
   
        curl -H 'Accept: text/plain' http://localhost:20110/stores/default/bags/8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        > <list of file-ids>
        
#### Retrieving one file
1. To download one file, just put its item-id in place of the bag-id, and don't specify a media type:

        curl http://localhost:20110/stores/default/bags/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image02.jpeg > image02.jpeg
        > % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                           Dload  Upload   Total   Spent    Left  Speed
          100 13829  100 13829    0     0  1042k      0 --:--:-- --:--:-- --:--:-- 1227k

#### Retrieving a bag or a directory
1. To download the contents of a bag (or a directory) simply use its item-id and specify the desired format in the 
   `Accept` header:
   
        curl -H 'Accept: application/x-tar' \
          http://localhost:20110/stores/default/bags/8eeaeda4-3ae7-4be2-9f63-3db09b19db43 > mybag.tar
        
         

*TO BE CONTINUED...*


#### Adding bag




#### Adding an updated bag




---
<a name="footnote1" /><sup>1</sup>There is a slight catch when getting a complete bag this way: if it contains a 
`fetch.txt`, this will *not* be removed, leaving the resulting bag technically incomplete. However, 
this is easily fixed by removing `fetch.txt` yourself, along with any entries for it in the tag manifests.

By the way, the `bagit` command line tool we use in this tutorial will incorrectly judge the bag to be valid
when asked so with `bagit verifyvalid`. [The section on completeness in the BagIt specs], however, seems to say
that a bag is incomplete (and therefore not valid) if it contains a `fetch.txt`. (And it is not even *incomplete*
(i.e. invalid) if there are files referenced in a payload manifest, that are present in neither the bag *nor* 
the `fetch.txt`.) The [PyBagIt] library on the other hand, *does* catch this error.


[cURL]: https://curl.haxx.se/
[HATEOAS]: https://en.wikipedia.org/wiki/HATEOAS
[The section on completeness in the BagIt specs]: https://tools.ietf.org/html/draft-kunze-bagit#section-3
[PyBagIt]: http://ahankinson.github.io/pybagit/

Appendix I: extended motivation of features
-------------------------------------------

### Simple


### Open standards



### Software independent


### Authenticity


### Efficiency


### Modular design


Appendix II: Migrations
-----------------------