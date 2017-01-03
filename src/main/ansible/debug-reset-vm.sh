#!/usr/bin/env bash

ansible-galaxy install -f -r `dirname $0`/requirements.yml
vagrant destroy -f
vagrant up