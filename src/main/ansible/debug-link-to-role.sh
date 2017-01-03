#!/usr/bin/env bash
# Creates a symbolic link to the local standard location of the role project.
# Only intended for debugging the role project. Normally, you should use the
# ansible-galaxy installed role from GitHub, which is automatically downloaded
# when doing "vagrant up".

ln -s ~/git/dtap/easy-bag-store-role `dirname $0`/roles/easy-bag-store

