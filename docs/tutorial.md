---
title: Tutorial introduction 
layout: page
---

Why?
----
Your first question may actually be *what is a bag store?*, but a good way to answer 
that question is start with *why did we create it?* There are plenty repository systems
out there so it is not obvious why we should create yet another one. I don't want to get
lost in a philosophical discussion here&mdash;after all, this is supposed to be a *hands-on* 
tutorial&mdash;but it may yet be helpful to briefly list the requirements we had in mind
when we started this project. Afterwards, when going through the tutorial we hope to make 
clear just how the bag store implements those requirements.

So, here we go:

* Independence from any particular repository software.
* Based on an open packaging format specification.
* Extensible, modular design.

Prerequisites
-------------
Before you start, you'll need to install some software. Newer version should also work, but
I have only tested the tutorial with the versions mentioned below.

* [Vagrant 1.9.2](https://releases.hashicorp.com/vagrant/1.9.2/)
* [Ansible 2.2.1.0](http://docs.ansible.com/ansible/intro_installation.html)
* [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [Maven 3](https://maven.apache.org/download.cgi)
* [Git](https://git-scm.com/downloads)
* [BagIt](https://github.com/LibraryOfCongress/bagit-java/releases/tag/v4.12.3)

You also need to clone [this git repo](https://github.com/DANS-KNAW/easy-bag-store) to your
local disk. In a terminal type:

    git clone https://github.com/DANS-KNAW/easy-bag-store

Tutorial
--------

1. Open a command terminal, `cd` to the `easy-bag-store` project and type:
   
       mvn clean install
       
   (The `clean` goal is technically not necessary, as this is the first time you are building the project, but
   I tend to include it out of habit, so I don't forget it when I do need it. It doesn't do any harm anyway.)
2. If the previous step was successful (i.e. somewhere at the end of the output it said `[INFO] BUILD SUCCESS`)
   then proceed by typing:
        
       vagrant up
       

TUTORIAL WORK IN PROGRESS ...


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

