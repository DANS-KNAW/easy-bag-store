---
title: Tutorial 
layout: page
---

Motivation
----------
We created the bag store here at [DANS], because we needed a way to store our archival packages. Our 
existing solution was becoming hard to maintain and evolve, so we decided to go back to the drawing board.
We were looking for something with the following properties:

* Simple
* Not *too* much redundant storage
* Independent from any particular repository software
* At the same time: based on open formats
* Extensible, modular design 
 
For our [SWORDv2 deposit service] we had already decided on [BagIt] as the exchange format. The simplest
we could come up with, therefore, was just storing these bags in a directory on the file system, and such
a directory is essentially a bag store. However, we have put some additional constraints on it in order
to protect against possibility of data loss, to make it easier to evolve your archival packaging layout, ...

As we go along I will point out how the bag store rules are designed to improve these points ...

<!-- TODO: laatste stuk herformuleren -->


[DANS]: https://dans.knaw.nl/en 
[SWORDv2 deposit service]: https://easy.dans.knaw.nl/doc/sword2.html
[BagIt]: https://tools.ietf.org/html/draft-kunze-bagit

Prerequisites
-------------
You will need the following software. Newer versions will probably also work, but I have tested the tutorial
with the versions specified here:

* [Vagrant 2.0.0](https://releases.hashicorp.com/vagrant/2.0.0/)
* [VirtualBox 5.1.26](https://www.virtualbox.org/wiki/Changelog-5.1#v26) 

Tutorial
--------
### Starting up
1. Create a directory for this tutorial somewhere on your filesystem and start the vagrant project:
   
       cd ~
       mkdir easy-bag-store-tutorial
       cd easy-bag-store-tutorial
       vagrant init https://easy.dans.knaw.nl/boxes/easy-bag-store-tutorial.box
       vagrant up

   The first time you run this tutorial, this will have to download the 850M vagrant box so, depending on
   your internet connection, that may take some minutes. If everything goes well, you will end up with
   a virtual machine running CentOS 6 Linux and with `easy-bag-store` installed on it. Don't worry if you
   are not a Linux person: the bag store does not *require* Linux. `easy-bag-store` only requires Java 8
   or higher, and the bag store itself only requires a hierarchical file system.
   
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
        
### Adding a bag     
1. First, we shall need some sample data. Let's turn to [EASY] for that. EASY is our very own long term 
   preservation archive for scientific research data. We will look for an open access dataset: at the 
   right-hand side click on the facet `Open (everyone)`. Select a dataset and click on the tab 
   `Data files`. Now click on `Download` to download the whole dataset. (You may also want to select
   some files first, of course, especially if the dataset is large.) 
2. Place the resulting zip file in the `easy-bag-store-tutorial` directory. This directory is shared
   between your PC or laptop and the VM. On the VM it is the directory called `/vagrant/`.
3. Now, in your ssh-session, type the following, replacing `<name of your downloaded dataset>` with the
   filename of your download:

        unzip /vagrant/<name of your downloaded dataset>.zip -d my-example-bag
        > Archive:  /vagrant/<name of your downloaded dataset>.zip
        >   inflating: <file 1>
        >   inflating: <file 2>
        
4. Next, we will turn the newly created `my-example-bag` directory into a bag (which&mdash;despite its
   name&mdash;it isn't yet). For this we will use a tool that was created by [Library Of Congress]. 
   It is already installed on the VM. Type the following command:
   
        bagit baginplace my-example-bag
        
   The contents of `my-example-bag` has been moved to a subdirectory called `data`. The base
   directory will contain files required by the BagIt format. To view the structure of the bag
   use the `tree` utility:
   
        tree my-example-bag
        >  my-example-bag/
           ├── bag-info.txt
           ├── bagit.txt
           ├── data
           │   ├── manifest-sha1.txt
           │   ├── meta
           │   │   ├── file_metadata.xml
           │   │   └── general_conditions_DANS.pdf
           │   └── <other files in dataset>
           │   
           ├── manifest-md5.txt
           └── tagmanifest-md5.txt

5. At this point, we are ready to add the bag to the store:

        easy-bag-store add my-example-bag    
        > OK: Added Bag with bag-id: aecd427f-45a8-4551-ab06-8edd2a580354 to \
            BagStore: default    
   
   The bag-id will of course be different in each case. You may also specify a UUID to use as bag-id,
   using the `-u` option (`easy-bag-store add -u <your UUID> my-example-bag`). The great thing about 
   UUIDs is that they can be minted in a decentralized fashion and still be guaranteed to be unique.

6. The default bag store is located in the directory `/srv/dans.knaw.nl/bag-store`. Check that
   the bag was copied into the bag store:
   
        tree /srv/dans.knaw.nl/bag-store
        > ae
          └── cd427f45a84551ab068edd2a580354
              └── my-example-bag
                  ├── bag-info.txt
                  ├── bagit.txt
                  ├── data
                  │   ├── manifest-sha1.txt
                  │   ├── meta
                  │   │   ├── file_metadata.xml
                  │   │   └── general_conditions_DANS.pdf
                  │   └── <other files in dataset>
                  │   
                  ├── manifest-md5.txt
                  └── tagmanifest-md5.txt
                  
   As you can see the bag-id is used to form a path from the bag store base directory to a container in which
   the bag is then stored. The slashes must be put in the same places for all the bags in a bag store. So in this
   the first two characters of the bag-id form the name of the parent directory and the rest (stripped of dashes)
   the child. Note that this "slashing pattern" strictly speaking doesn't need to be stored anywhere, as it 
   will be implicitly recorded when adding the first bag. However, the `easy-bag-store` tool does use a configuration
   setting for this, as "discovering" this every time would be inefficient.                
        
Wasn't that great? And it took only six steps and three pages to explain ;) ! At this point you may think you might just 
as well have copied the bag to the given path yourself, and that is quite true. Actually, that is the point of the bag store:
to be so simple that manual operation would be feasible. 

#### Enter: bag store operations
This is a good moment to introduce the rules of the bag store. The only operations allowed on it are:

* `ADD` - add a *valid* bag (actually a "virtually-valid" bag, but we will come to that).
* `GET` - read any part of the bag store.
* `ENUM` - enumerate the bags and/or files in a bag store. 

Note that there is no <del>`MODIFY`</del>. If we should happen to add an invalid bag, we corrupt the bag store. So, 
while manual operation is feasible in theory, in practise you would soon be developing some scripts to:

* verify that the bag you are about to add is valid (actually "virtually valid", but we explain that below);
* change the file permissions of the contents of the bag to read-only, so as to prevent accidentally modifying them;
* convert the UUID to a path correctly.

These are precisely the things that `easy-bag-store add` does for you! To mess up you now really have to make
a conscious effort. 

So, let's now move on to an even simpler task: retrieving an item.

### Retrieving an item
To retrieve a bag or any part of it, we could actually simply read it from disk, and that would not violate
the bag store rules. However, when referring to bag store items (bags, or files and directories in them) it 
is often not convenient to use local paths. That is why they have **item-id**s.

The item-id of a bag (= bag-id) is the UUID under which it was stored. The item-id of a file or directory is 
the bag-id with the percent-encoded file path appended to it. [Percent-encoding] is a way to map the path to 
an ASCII string without spaces. The actual path may of course contain non-ASCII characters. The character 
encoding should be UTF-8. 

We can use `easy-bag-store` to find and item for us.

#### A bag
1. Let's first enumerate the bags in the store.

        easy-bag-store enum
        > <uuid of my-example-bag>
          OK: Done enumerating
          
2. Copy the bag to some output directory:

        easy-bag-store get <uuid of my-example-bag> out
        > OK: Retrieved item with item-id: <uuid of my-example-bag> to out \
            from BagStore: /srv/dans.knaw.nl/bag-store
        
3. Now to check that the bag you retrieved is equal to the one you added:

        diff -r my-example-bag out        

   No output here is good. It means the directories are the same. 

#### A single file
1. Let's start by enumerating the files in our bag. This has the benefit that we don't have to 
   perform the percent-encoding ourselves:
   
        easy-bag-store enum <uuid of my-example-bag>
        > <uuid of my-example-bag>/path/to/some/file
          ... more files
          OK: Done enumerating
            
2. Select one of the item-ids from the output and:

        easy-bag-store get <item-id> .
        > OK: Retrieved item with item-id: <item-id> to . \
            from BagStore: /srv/dans.knaw.nl/bag-store     
             
    We have now copies the select file to the current directory, which you can check with a simple
    `ls` call.

#### A directory

<!-- TODO -->

[Percent-encoding]: https://tools.ietf.org/html/rfc3986#section-2.1


### Adding an updated bag




### Using the HTTP service

   
   
[EASY]: https://easy.dans.knaw.nl/ui/browse
[Library Of Congress]: https://github.com/LibraryOfCongress/bagit-java
       
3. Adding a bag to the store
    - Add an example bag through the command line
    - Inspecting the location where it is stored
    - Verifying the bag in storage
4. Bagger: 
    - creating a Bag
    - adding it
5. Adding an update:
    - prune the update
    - add it
    - Showing that non-virtually-valid bag is not accepted

