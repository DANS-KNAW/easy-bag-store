Tutorial
========

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

[Appendix] tries to give a more elaborate justification for above list of properties.  

[DANS]: https://dans.knaw.nl/en 
[SWORDv2 deposit service]: https://easy.dans.knaw.nl/doc/sword2.html
[BagIt]: https://tools.ietf.org/html/draft-kunze-bagit
[Appendix]: #appendix-extended-motivation-of-bag-store-properties

Prerequisites
-------------
You will need the following software. Newer versions will probably also work, but I have tested the tutorial
with the versions specified here:

* [Vagrant 2.2.5](https://releases.hashicorp.com/vagrant/2.2.5/)
* [VirtualBox 6.0.10](https://www.virtualbox.org/wiki/Download_Old_Builds_6_0)

Tutorial
--------
### Starting up
1. Create a directory for this tutorial somewhere on your filesystem and start the vagrant project. 
   
        #!bash
        mkdir easy-bag-store-tutorial
        cd easy-bag-store-tutorial
        vagrant init {{ tutorial_vagrant_box }}
        vagrant up
   The first time you run this tutorial, this will have to download the ~800M vagrant box so&mdash;depending on
   your internet connection&mdash;that may take some minutes. If everything goes well, you will end up with
   a virtual machine running CentOS 7 Linux and with `easy-bag-store` installed on it. Don't worry if using
   Linux in your organization is not an option: the bag store does not *require* Linux; `easy-bag-store` 
   only requires Java 8 or higher, and the bag store itself only requires a hierarchical file system.
   
2. Now test that it is working; while in the `easy-bag-store-tutorial` directory type:

        #!bash
        vagrant ssh   
   This will log you in to the virtual machine, just as if you were logging in to a remote server. 
   You should now see a prompt like this:
   
        Last login: Sun Oct 29 05:36:33 2017 from 10.0.2.2
        [vagrant@tutorial ~]$
   The server's name is `test` and your user is called `vagrant`. You are currently in the user's 
   home directory. If you type `pwd` (print working directory) and Enter, you will see this:
    
        [vagrant@tutorial ~]$ pwd
        /home/vagrant
        [vagrant@tutorial ~]$
   (Don't do this now, but: you can leave the VM by type `exit` and Enter and you can stop it with 
   `vagrant halt` from the `easy-bag-store-tutorial` directory. To start it again use `vagrant up`.)     
        
        
### Adding a bag     
1. First, we shall need some sample data. To make it a bit easier to use this tutorial we will use 
   a data package called sample.zip, but by all means experiment with other data.
2. Download [sample.zip]({{ tutorial_sample_data }}) to `vagrant`'s home directory: 

        #!bash
        wget {{ tutorial_sample_data }}
   This will place the file `sample.zip` in `/home/vagrant`. 

3. Now, type the following:

        #!bash    
        unzip sample.zip
    Output:        
        
        Archive:  sample.zip
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
         
4. Next, we will turn the newly created `sample` directory into a bag. For this we will use 
   [a tool that was created by Library Of Congress]. It is already installed on the VM. Type the following command:
   
        #!bash
        bagit baginplace sample
   The contents of `sample` has been moved to a subdirectory called `data`. The base
   directory will contain files required by the BagIt format. To view the structure of the bag
   use the `tree` utility:
   
        #!bash
        tree sample
    Output:
        
        sample
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
   Note that `tree` apparently does not try to render the Chinese characters, but instead displays the 
   UTF-8 byte sequences that encode them as octal numbers. 

5. At this point, we are ready to add the bag to the store:
        
        #!bash
        easy-bag-store add -u 8eeaeda4-3ae7-4be2-9f63-3db09b19db43 sample
    Output:
            
        OK: Added bag with bag-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43 to \
           bag store: /srv/dans.knaw.nl/bag-store
   The bag-id must of course be unique. There are a lot of tools that can generate unique UUIDs for you,
   for example the `uuidgen` command. If you leave off the `-u` option in above call, `easy-bag-store` will 
   use the a Java Runtime Library function to generate a UUID. The great thing about UUIDs is 
   that they can be minted decentrally while still guaranteed to be unique. For the
   purpose of this tutorial, however, it is easier to reuse the above UUID, as this will allow
   you to copy-paste the example commands in the next steps without having to edit them.  
   
6. The default bag store is located in the directory `/srv/dans.knaw.nl/bag-store`. Check that
   the bag was copied into the bag store:
   
        #!bahs
        tree /srv/dans.knaw.nl/bag-store
    Output:        
        
        /srv/dans.knaw.nl/bag-store
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
   setting for this, as "discovering" this every time would be inefficient (and impossible for the first bag).               
        
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

[a tool that was created by Library Of Congress]: {{ bagit_java_github_repo }}

### Retrieving an item
To retrieve a bag or any part of it, we could actually simply read it from disk, and that would not violate
the bag store rules. However, when referring to bag store items (bags, or files and directories in them) it 
is often not convenient to use local paths. That is why they have **item-id**<!---->s.

The item-id of a bag (= bag-id) is the UUID under which it was stored. The item-id of a file or directory is 
the bag-id with the file path appended to it. [Percent-encoding] of all characters except alphanumerical and the 
underscore must be applied to the path segments.

This way we further support **authenticity** because we have a simple mapping between the exact location where 
each item is stored and its global identifier. The only extra information we need is the base directory of
the bag store. Also note that we build on the open **standard**<!---->s by using [the percent encoding scheme from the 
URI definition (RFC3986)]({{ percent_encoding }}) .

We can use `easy-bag-store` to find an item for us.

[Percent-encoding]: {{ percent_encoding }}

#### A bag
1. Let's first enumerate the bags in the store.

        #!bash
        easy-bag-store enum
    Output:        
        
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        OK: Done enumerating
   In your case the bag-id will of course be different and in the following steps you will need to use the one from your output
   rather than the one given above.       
          
2. Copy the bag to some output directory:

        #!bash
        easy-bag-store get 8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        
    Output:        
        
        FAILED: Output path already exists; not overwriting /home/vagrant/./sample
        

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
   
        #!bash
        easy-bag-store enum 8eeaeda4-3ae7-4be2-9f63-3db09b19db43
   Output:
        
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/bag%2Dinfo%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/bagit%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/README%2ETXT
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image01%2Epng
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image02%2Ejpeg
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03%2Ejpeg
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/file1%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/%E6%AA%94%E6%A1%88%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/manifest%2Dmd5%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/tagmanifest%2Dmd5%2Etxt   
        OK: Done enumerating
          
    Notice that:
     
    * the Chinese characters, spaces, hyphens and full stops appear percent-encoded;
    * the bag name ("sample" in this case) is not part of the identifier.      
            
2. Select one of the item-ids from the output and:

        #!bash
        easy-bag-store get \
            8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03%2Ejpeg
    Output:        
        
        OK: Retrieved item with \
          item-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03%2Ejpeg to \
          ./image03.jpg from bag store: default     
             
    We have now copied the selected file to the current directory, which you can check with a simple
    `ls` call.

#### A directory
1. What if we want to get all the files in a specific directory, say, `sample/data/img`? Let's try that.

        #!bash
        easy-bag-store get 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img   
   Output:     
        
        FAILED: Source '/srv/dans.knaw.nl/bag-store/8e/eaeda43ae74be29f633db09b19db43/sample/data/img' \
          exists but is a directory
   Alas, this hasn't been implemented yet! However, there is an alternative way of getting items, which
   also supports getting directories: *archive streams*.

2. To get the directory as a [TAR] archive stream, execute the following:

        #!bash
        easy-bag-store stream --format tar \
          8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img > img.tar
    Output:
            
        OK: Retrieved item with item-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img to stream.
        
3. This will also work for complete bags and single files. Don't forget to redirect the stream to 
   a file, or the TAR will be printed to your screen. You might also pipe the stream into the `tar`
   extract command:
   
        #!bash
        easy-bag-store stream --format tar 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img | tar x
    Output:
            
        OK: Retrieved item with item-id: 8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img to stream.
        
    To check this:       
    
        #!bash
        ls -l img
    Output:        
        
        total 3144
        -rw-r--r--. 1 vagrant vagrant  422887 Sep 25  2017 image01.png
        -rw-r--r--. 1 vagrant vagrant   13829 Sep 25  2017 image02.jpeg
        -rw-r--r--. 1 vagrant vagrant 2775738 Sep 25  2017 image03.jpeg       
   This will extract the `img` directory in your current working directory, so effectively does what we
   where trying to achieve at the beginning of this section.[^1]
           
[TAR]: https://en.wikipedia.org/wiki/Tar_(computing)


### Adding an updated bag
Now guarding **authenticity** of your archival packages by disallowing any changes to them may seem
a bit draconic. In the real world packages do get updated to correct errors, add newly available data, etc.
How do we deal with that? The answer is: *in the simplest possible way; by adding a new version*. 

Keeping track of the versions is not built in to the bag store, but here is were **modular** design comes in:
you can add that capability by simply adding appropriate metadata. That could be something as simple
as a "version" metadata field to the `bag-info.txt` file, or something a bit more sophisticated. 
Our [easy-bag-index] module records both a timestamp and a pointer to the base revision, the combination 
of which is always enough to reconstruct the version history.

[easy-bag-index]: {{ easy_bag_index_repo }}

#### Virtually-valid
An objection to such an approach could be, that you would be storing a lot of files 
redundantly. After all, a package update may leave many files unchanged. The bag store supports 
the **efficient** storage of collections of bags with common files. Instead of requiring 
every bag to be valid according to [the BagIt definition of valid], it must only be "virtually" valid. 

A bag is virtually-valid when:

* it is valid, *or*...
* it is incomplete, but has a [fetch.txt] with references to the missing files.

The idea is that the only things we need to do to make the bag valid are:

1. Download the files referenced from `fetch.txt` into the bag.
2. Remove `fetch.txt` from the bag.
3. Remove any entries for `fetch.txt` in the tag manifests, if present.

If we can prove that this would be enough to make the bag valid, then it *is* virtually valid, 
in other words: virtually-valid. Note that this term was introduced by us and is nowhere to be 
found in the BagIt specifications document.

So, now we can store a new version of an archival package, but for all the files that haven't been
updated, we include a fetch reference to the already archived file. 

[the BagIt definition of valid]: {{ bagit_valid }}
[fetch.txt]: {{ bagit_fetchtxt }}

#### Pruning
OK, enough theory, let's try to create an update for our sample bag. The `easy-bag-store` tool has
a command to help you strip your updated bag of unnecessary files.

1. Copy `sample` to `sample-updated`:

        #!bash
        cp -r sample sample-updated
        
2. Make a change to one of the data files in `sample-updated`, let's say the `README.TXT`:

        #!bash
        echo "...and some more text" >> sample-updated/data/README.TXT

3. Let's also remove a file:
        
        #!bash
        rm sample-updated/data/img/image01.png

4. ...and add one:

        #!bash
        echo "New file content" > sample-updated/data/NEW.TXT

5. Update the checksums in the new bag, so that it is valid again:

        #!bash
        bagit update sample-updated
        bagit verifyvalid sample-updated

6. Make a copy of this updated bag, so that we can compare it later with the one we retrieve from the
   bag store:
   
        #!bash
        cp -r sample-updated sample-updated-unpruned

7. Now let's strip the updated bag of files that are already archived, and therefore can be included by fetch-reference.
   
        #!bash
        easy-bag-store prune sample-updated 8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        
   Note that we provided as the second argument the bag-id of the bag in which the unchanged files 
   where located. You may append as many bag-ids as you like. We call these bags "reference bags" or **ref-bag**<!---->s.
   
8. Now let's have a look at `sample-updated`:

        #!bash
        tree sample-updated/
    Output:        
        
        sample-updated/
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

        #!bash
        cat sample-updated/fetch.txt
    Output:        
        
        http://localhost/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/file1%2Etxt  0  data/path/with a/space/file1.txt
        http://localhost/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image02%2Ejpeg  13829  data/img/image02.jpeg
        http://localhost/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/%E6%AA%94%E6%A1%88%2Etxt  34  data/path/with a/space/檔案.txt
        http://localhost/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03%2Ejpeg  2775738  data/img/image03.jpeg
          
   All the payload files from `sample` are included in `fetch.txt`, except `README.TXT` (which was
   changed), `NEW.TXT` (which was added) and `image01.png` (which was removed).
   
   
As you may have noticed, the URLs in `fetch.txt` all start with `http://localhost/`. These URLs are called
**local-file-uri**<!---->s. They will of course only really work, if&mdash;on this host&mdash;there is a web server to do the
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

        #!bash
        easy-bag-store add -u d01fd36f-181c-419a-90eb-bbc7230d6a86 sample-updated
   Again, we are forcing a specific UUID for convenience. 

2. Retrieve it again from the bag store:

        #!bash
        easy-bag-store get -d out-updated d01fd36f-181c-419a-90eb-bbc7230d6a86
        
3. Verify that `sample-updated-unpruned` and `out-updated/sample-updated` are equal

        #!bash
        diff -r sample-updated-unpruned out-updated/sample-updated
   Again, if you see any output from the last command, that means the directories are different and something 
   went wrong. 
   
   As you can see, the `get` subcommand will, by default, also complete the bag by fetching any files
   that were pruned away before. You can also skip the "completing" step with the `-s` option. You may then use
   the `complete` subcommand to complete the bag after retrieval from the bag store.

#### Other operations and commands
You have now seen the most important bag store operations in action: `ADD`, `ENUM` and `GET`. The command line
tool implements these as the subcommands `add`, `enum` and `get`/`stream`, respectively. Furthermore, you have seen the utility subcommands
`prune` and `complete` that help you convert between virtually-valid and valid bags. For a list of all the available subcommands
type:
        
    #!bash    
    easy-bag-store --help

For more exceptional situations the bag store allows you to `DEACTIVATE` a bag. This will mark it as hidden, while
you can still reference files in it. The main use case for this operation is, when you turn out to have created an
incorrect version history for a sequence of bags, which can of course not be amended by adding more versions. A planned
tutorial in [easy-bag-index] will explain this feature. It should probably not be used for anything else.

Support for an `ERASE` operation, which will overwrite the contents of a file with a tombstone message is also planned.
This operation is also *not* intended for regular use cases, but rather for situations where there is a strict legal
obligation to destroy data. However, in such cases a filtering migration might be a better option.

### Using the HTTP service
For simple scenarios, working with the command line interface of the bag store tool is quite sufficient. However, 
when building a complete archival system on top of the bag store, it may be more convenient to have an HTTP based interface.
Fortunately, such an interface exists. We will briefly explore it here. 

The examples work with the command line tool [cURL], which is installed on the VM.

#### Starting the service ###
1. Start the service:

        #!bash
        sudo systemctl start easy-bag-store
        sudo systemctl status easy-bag-store
   Output:
   
        ● easy-bag-store.service - EASY Bag Store Service
           Loaded: loaded (/usr/lib/systemd/system/easy-bag-store.service; disabled; vendor preset: disabled)
          Drop-In: /etc/systemd/system/easy-bag-store.service.d
                   └─override.conf
           Active: active (running) since Fri 2019-10-04 12:31:17 UTC; 63ms ago
         Main PID: 19358 (java)
           CGroup: /system.slice/easy-bag-store.service
                   └─19358 /bin/java -Dlogback.configurationFile=/etc/opt/dans.knaw.nl/easy-bag-store/logback-service.xml -Dapp.home=/opt/dans.knaw.nl/easy-bag-store -Dorg.scala...
        
        Oct 04 12:31:17 tutorial systemd[1]: Started EASY Bag Store Service.

2. Optionally, you can enable the service, so that it will automatically start on system boot:

        #!bash
        sudo systemctl enable easy-bag-store

#### Getting the list of bag stores
1. Check that the service is running. By default the service listens on port 20110. This can be configured in 
   `/etc/opt/dans.knaw.nl/easy-bag-store/application.properties`.

        #!bash
        curl http://localhost:20110/
    Output:        
        
        EASY Bag Store is running v1.4.1
        .
        Available stores at <http://localhost:20110/stores>
        Bags from all stores at <http://localhost:20110/bags>
   Even though the current API is rather basic, we have tried to adhere to the [HATEOAS] principle, so you can 
   follow the links given in the response to navigate the service.
       
3. Let's see what bag stores are available:

        #!bash
        curl http://localhost:20110/stores
    Output:        
        
        <http://localhost:20110/stores/default>
   It turns out there is only one store on this VM. It *is* possible to have multiple stores. One use case for this could
   be when some of your archival packages contain sensitive data that must be stored with an added level of security.
   
4. We follow the link to the one store:

        #!bash
        curl http://localhost:20110/stores/default
    Output:        
        
        Bag store 'default'.
        Bags for this store at <http://localhost:20110/stores/default/bags>
   
   We see we can access the bags in this particular bag store. 
 
#### Enumerating bags and files
1. Enumerate all the bags in your bag store:

        #!bash
        curl http://localhost:20110/stores/default/bags
   You will get the list of all the bags currently in your bag store:
   
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43
        d01fd36f-181c-419a-90eb-bbc7230d6a86

2. Pick one UUID and use it in the following:

        #!bash
        curl http://localhost:20110/stores/default/bags/8eeaeda4-3ae7-4be2-9f63-3db09b19db43
    Output: 
            
        Item 8eeaeda4-3ae7-4be2-9f63-3db09b19db43 is not a regular file.
   We need to specify the media type we want to accept. Currently, for a bag you can choose between
   `text/plain`, `application/zip` and `application/x-tar`. `text/plain` will give you a listing of the regular
   files in the bag.
   
3. Let's get the list of files in the bag:   
   
        #!bash
        curl -H 'Accept: text/plain' \
          http://localhost:20110/stores/default/bags/8eeaeda4-3ae7-4be2-9f63-3db09b19db43
   Output:
   
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/bag%2Dinfo%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/bagit%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/README%2ETXT
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image01%2Epng
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image02%2Ejpeg
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image03%2Ejpeg
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/file1%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/path/with%20a/space/%E6%AA%94%E6%A1%88%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/manifest%2Dmd5%2Etxt
        8eeaeda4-3ae7-4be2-9f63-3db09b19db43/tagmanifest%2Dmd5%2Etxt      
            
                 
#### Retrieving one file
1. To download one file, just put its item-id in place of the bag-id, and don't specify a media type:
        
        #!bash
        wget http://localhost:20110/stores/default/bags/8eeaeda4-3ae7-4be2-9f63-3db09b19db43/data/img/image02%2Ejpeg
   I am using `wget` for a change; this will also work with `curl`, but don't forget to redirect the output to a file.

#### Retrieving a bag or a directory
1. To download the contents of a bag (or a directory) simply use its item-id and specify the desired format in the 
   `Accept` header:
   
        #!bash
        curl -H 'Accept: application/x-tar' \
          http://localhost:20110/stores/default/bags/8eeaeda4-3ae7-4be2-9f63-3db09b19db43 > mybag.tar
    Output:
    
        % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                         Dload  Upload   Total   Spent    Left  Speed
        100 3147k    0 3147k    0     0  44.3M      0 --:--:-- --:--:-- --:--:-- 45.1M    

2. Check the contents of `mybag.tar`, without extracting it:

        #!bash
        tar tf mybag.tar 
    Output:
    
        sample/bag-info.txt
        sample/bagit.txt
        sample/data/README.TXT
        sample/data/img/image01.png
        sample/data/img/image02.jpeg
        sample/data/img/image03.jpeg
        sample/data/path/with a/space/file1.txt
        sample/data/path/with a/space/檔案.txt
        sample/manifest-md5.txt
        sample/tagmanifest-md5.txt
        
3. We can do the same with the updated bag. As you will remember, it was pruned of files that were already in the 
   first version. When we download it, it will automatically be completed:
   
        #!bash
        curl -H 'Accept: application/x-tar' \
                  http://localhost:20110/stores/default/bags/d01fd36f-181c-419a-90eb-bbc7230d6a86 > mybag-updated.tar
        mkdir out-mybag-updated
        tar -C out-mybag-updated -xf mybag-updated.tar
        diff -r out-mybag-updated/sample-updated/ sample-updated-unpruned/
    Output:
    
        Only in out-mybag-updated/sample-updated/: fetch.txt         
    Okay, so there is a small catch here: the `fetch.txt` file is not removed after completion.[^2] Except for that,
    however, the downloaded bag is the same as the unpruned bag we added earlier.
    
4. Now we will also get a directory as a zipped file. To show off we will get a directory that is only "virtually"
   present in the bag store:
    
        #!bash 
        curl -H 'Accept: application/zip' \
                  http://localhost:20110/stores/default/bags/d01fd36f-181c-419a-90eb-bbc7230d6a86/data/img \
                    > mybag-updated-img.zip
        mkdir out-mybag-updated-img
        unzip -d out-mybag-updated-img mybag-updated-img.zip
        diff -r out-mybag-updated-img/img sample-updated-unpruned/data/img
    Again, the `diff` command tells you that your are getting back exactly the same as you put in earlier.
    To prove that these files were included by reference, execute the following command:
    
        #!bash
        tree /srv/dans.knaw.nl/bag-store/d0/1fd36f181c419a90ebbbc7230d6a86               
    Output:
    
        /srv/dans.knaw.nl/bag-store/d0/1fd36f181c419a90ebbbc7230d6a86
        └── sample-updated
            ├── bag-info.txt
            ├── bagit.txt
            ├── data
            │   ├── NEW.TXT
            │   ├── path
            │   │   └── with\ a
            │   └── README.TXT
            ├── fetch.txt
            ├── manifest-md5.txt
            └── tagmanifest-md5.txt
     As you can see the directory `data/img` is not present in this bag. Isn't that magic now?

#### Adding an (updated) bag
As with the command-line-interface, you can also add a bag through the HTTP-interface. We will add an update of the 
previous bag and also use "auto-pruning" by adding a special file called `refbags.txt`. (Yes, a bit hacky, I know.)

1. Let's first create yet another updated version of our previous bag:

        #!bash
        # Copy the last version
        rm -fr sample-updated-unpruned2* # Just to be sure 
        cp -r sample-updated-unpruned sample-updated-unpruned2
        
        # Just change the NEXT.txt
        echo "...newer is better" >> sample-updated-unpruned2/data/NEW.TXT
        
        # Make it valid 
        bagit update sample-updated-unpruned2/
        
        # Add a refbags.txt with the UUIDs of the previous to revisions
        echo "8eeaeda4-3ae7-4be2-9f63-3db09b19db43" > sample-updated-unpruned2/refbags.txt
        echo "d01fd36f-181c-419a-90eb-bbc7230d6a86" >> sample-updated-unpruned2/refbags.txt
        
        # Zip the bag
        zip -r sample-updated-unpruned2.zip sample-updated-unpruned2
        
2. Now upload it to a new UUID with an HTTP `PUT`-call. This operation is secured with a username/password.
   Needless to say you should configure safer values that the defaults (in `/etc/opt/dans.knaw.nl/easy-bag-store/application.properties`)
   and also make sure that this configuration file can be read only by the `easy-bag-store` user.
   
        #!bash
        curl -u easy-bag-store:changeme \
          -X PUT --data-binary @sample-updated-unpruned2.zip \
          -H 'Content-Type: application/zip' \
           http://localhost:20110/stores/default/bags/7f4323ed-ad68-4543-b9ba-c08c5d2e24d9
    Notice that we have to mint a UUID prior to the call and that you need to specify the mediatype of the content.
    
We shall leave verifying that the bag was correctly pruned and getting it back as an exercise to the reader.           

[^1]: There is a slight catch when getting a complete bag this way: if it contains a 
`fetch.txt`, this will *not* be removed, leaving the resulting bag technically incomplete. However, 
this is easily fixed by removing `fetch.txt` yourself, along with any entries for it in the tag manifests.

[^2]: In the BagIt specs prior to V1.0 there was an ambiguity about whether such a bag was to be considered valid.
It was possible to read [the section on completeness in the BagIt specs] as saying that whenever there was a `fetch.txt` file,
the bag could not be valid. This has been removed from V1.0, so we'll consider the point as academic.

[cURL]: https://curl.haxx.se/
[HATEOAS]: https://en.wikipedia.org/wiki/HATEOAS
[the section on completeness in the BagIt specs]: https://tools.ietf.org/html/draft-kunze-bagit-14#section-3
[PyBagIt]: http://ahankinson.github.io/pybagit/

Appendix: extended motivation of bag-store properties
-----------------------------------------------------
As stated in the introduction to this tutorial, the motivation for creating the bag-store format and service was derived
from the wish to get away from the complexity and dependency on specific software that characterized our previous archival system.
Here I summarize some properties that we are trying to realize in the new archive. 

### Simple
First of all: don't create more complexity than inherently present in the problem space. Sofware developers have been known
to violate this rule&mdash;from time to time. 

### Open standards
Using open standards is one means of outsourcing the complexity of part of your problem space. For example, by using the BagIt-specs
we avoided the need to design a packaging format for our archival packages. Also, we were able to reuse the `fetch.txt` file to
store the package-versions more efficiently.   

### Software independent
This goes in tandem with simplicity and open standards. If the way your packages are store depends on specific software, you 
may have to reverse-engineer the packaging format if at some point you need to move away from that software. The simpler the
packaging format the easier it becomes to build new software that can work with it. If it uses open standards, there will probably
even be software for it already.

### Authenticity
A core concern in a digital archive is the authenticity of the data stored. Collecting and storing evidence of the authenticity
is key. The data and the evidence need to be guarded at the lower levels by fixity metadata. BagIt does a good job here, by defining the standard
locations to store checksums.

### Efficiency
Simplicity may easily become very wasteful. That is why (reasonable) efficiency is also on our list.
Efficiency was a concern to the extent that storing each revision of a deposit as a separate bag would result in many files 
being stored redundantly. The fetch-references are a simple way to avoid that in most cases. 

### Modular design
Having read up to here, you may be surprised at how little the bag-store is concerned with. It is hardly a complete digital
preservation system! This is by design. The bag-store is supposed to only concern itself with the packaging information and 
fixity of the [AIP]. It is the lower layer on which other layers are being built. The [DANS BagIt Profile] adds rules and 
metadata schemas for bags stored in the DANS Archive. The [easy-bag-index] services uses that to retrieve the revision history
of a dataset. In this way we plan to build more services that extend the functionality of the bag-store.   

[DANS BagIt Profile]: https://github.com/DANS-KNAW/dans-bagit-profile 
