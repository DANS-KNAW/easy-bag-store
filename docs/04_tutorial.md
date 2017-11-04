---
title: Tutorial 
layout: page
---

Motivation
----------
We created the bag store here at [DANS], because we needed a way to store our archival packages. Our 
existing solution was becoming hard to maintain and evolve, so we decided to go back to the drawing board.
We were looking for something with the following properties:

* **Simple** 
* Based on **open** formats
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
       vagrant init https://easy.dans.knaw.nl/boxes/easy-bag-store-tutorial.box
       vagrant up

   The first time you run this tutorial, this will have to download the ~850M vagrant box so&mdash;depending on
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
        > OK: Added Bag with bag-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43 to \
            BagStore: default    
   
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
                  
   As you can see the bag-id is used to form a path from the bag store base directory to a container in which
   the bag is stored. The slashes must be put in the same places for all the bags in a bag store. So in this case
   the first two characters of the bag-id form the name of the parent directory and the rest (stripped of dashes)
   the child. Note that this "slashing pattern" strictly speaking doesn't need to be stored anywhere, as it 
   will be implicitly recorded when adding the first bag. However, the `easy-bag-store` tool does use a configuration
   setting for this, as "discovering" this every time would be inefficient.                
        
Wasn't that great? And it took only six steps and three pages to explain! At this point you may think you might just 
as well have copied the bag to the given path yourself, and that is quite true. Actually, that is the point of the bag store:
to be so simple that manual operation would be feasible. 

#### Enter: bag store operations
This is a good moment to introduce the rules of the bag store, because not only do we want it to be simple but also to
support archival package authenticity. That is why we limit the allowed operations to the following: 

* `ADD` - add a *valid* bag (actually a "virtually-valid" bag, but we will come to that).
* `GET` - read any part of the bag store.
* `ENUM` - enumerate the bags and/or files in a bag store. 

Note that there is no <del>`MODIFY`</del>. If we should happen to add an invalid bag, we corrupt the bag store. So, 
while manual operation is feasible in theory, in practise you would soon be developing some scripts to:

* verify that the bag you are about to add is (virtually) valid;
* change the file permissions of the contents of the bag to read-only, so as to prevent accidental modification;
* convert the UUID to a path correctly.

These are precisely the things that `easy-bag-store add` does for you! To mess up you now really have to make
a conscious effort. 

So, let's now move on to an even simpler task: retrieving an item.

[Library Of Congress]: https://github.com/LibraryOfCongress/bagit-java

### Retrieving an item
To retrieve a bag or any part of it, we could actually simply read it from disk, and that would not violate
the bag store rules. However, when referring to bag store items (bags, or files and directories in them) it 
is often not convenient to use local paths. That is why they have **item-id**s.

The item-id of a bag (= bag-id) is the UUID under which it was stored. The item-id of a file or directory is 
the bag-id with the percent-encoded file path appended to it. [Percent-encoding] is a way to map the path to 
an ASCII string without spaces. The actual path may of course contain non-ASCII characters. The character 
encoding should be UTF-8. 

We can use `easy-bag-store` to find and item for us.

[Percent-encoding]: https://tools.ietf.org/html/rfc3986#section-2.1

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

*WORK IN PROGRESS*


### Adding an updated bag
Now you will probably say: "Okay, so keeping your archival packages immutable may go a long way to 
guarding their authenticity over time, but out here in the real world data tends to get updated. How,
do we deal with that?" The answer is: in the simplest possible way; by adding a new version. Keeping
track of the versions is not built in to the bag store, but here is were modular design comes in:
you can add that capability by simply adding appropriate metadata. That could be something as simple
as a "version" metadata field, or something a bit more sophisticated. Our [`easy-bag-index`] module takes a
different approach by recording both a timestamp and a pointer to the base revision, the combination
of which is always enough to reconstruct the version history.

[`easy-bag-index`]: https://github.com/DANS-KNAW/easy-bag-index

#### Virtually-valid
However, an objection to such an approach could be that you would be storing a lot of files 
redundantly. The bag store does have some support to ameliorate that. Instead of requiring every bag 
to be valid according to [the BagIt definition of valid], it must be "virtually" valid. A bag is
virtually-valid when:

* it is valid, *or*...
* it is incomplete, but has a [`fetch.txt`] with references to the missing files.

The idea is that the only things we need to do to make the bag valid are:

* Download the files referenced from `fetch.txt` into the bag.
* Remove `fetch.txt` from the bag.
* Remove any entries for `fetch.txt` in the tag manifests, if present.

If we can prove that this would be enough to make the bag valid, then it is virtually-valid. Note that
this term was introduced by us and is nowhere to be found in the BagIt specifications document.

So, now we can store a new version of a archival package, but for all the files that haven't been
updated, we include a fetch reference to the already archived file. 

[the BagIt definition of valid]: https://tools.ietf.org/html/draft-kunze-bagit#section-3
[`fetch.txt`]: https://tools.ietf.org/html/draft-kunze-bagit-14#section-2.2.3

#### Pruning
OK, enough theory, let's try to create an update for our example bag. The `easy-bag-store` tool has
a command to help you strip your updated bag of unnecessary files.

1. Copy `my-example-bag` to `my-example-bag-v2`
2. Make a change to one of the data files in `my-example-bag-v2`. (Remember which one.)
3. Now copy the bag-id of the version we stored earlier (use `easy-bag-store enum` if needed) and 
   use it in the following command:
   
        easy-bag-store prune my-example-bag-v2 <the bag-id>
        > OK: Done pruning
   
4. Now let's have a look at `my-example-bag-v2`:

        tree my-example-bag-v2
        > my-example-bag-v2/
          ├── bag-info.txt
          ├── bagit.txt
          ├── data
          │   └── <the one file you changed>
          ├── fetch.txt
          ├── manifest-md5.txt
          └── tagmanifest-md5.txt

5. Yes, that's right: all the other data files are gone. Take a look at the contents of `fetch.txt`

        cat my-example-bag-v2/fetch.txt
        > http://localhost/<bag-id of my-example-bag>/data/path/to/unchanged/file1  <file size>  data/path/to/unchanged/file1
          ... more lines
          
   All the payload files from `my-example-bag` should be included in `fetch.txt`, except for the 
   one changed file of course.
   
As you may have noticed, the URLs in `fetch.txt` all start with `http://localhost/`. This means that 
where these URLs resolve to depends on if there is a web server listening on localhost:80, and how *it*
will resolve the paths passed to it. To get the correct behavior we could therefore set up such a
server and implement the fairly simple mapping from item-id to item-location. However, that hardly
seems worth the trouble and `easy-bag-store` takes a s
 
#### Round-trip: adding, retrieving, completing
Finally, to come full circle, do the following steps:

1. Add the updated bag:

        easy-bag-store add my-example-bag-v2

2. Retrieve it again from the bag store:

        easy-bag-store get <bag-id of my-example-bag-v2> out2
        
3. Note that the retrieved bag is still only virtually-valid, and not valid. We will use the 
   `easy-bag-store complete` for that.
   
        easy-bag-store complete out2

4. Verify that `my-example-bag-v2` and `out2` are equal

        diff -r my-example-bag-v2 out2

 
### Using the HTTP service


Appendix I: extended motivation of features
-------------------------------------------

### Simple


### Open formats



### Software independent


### Authenticiy


### Efficiency


### Modular design


