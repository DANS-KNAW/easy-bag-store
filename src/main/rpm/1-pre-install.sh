#!/usr/bin/env bash
#
# Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

NUMBER_OF_INSTALLATIONS=$1
MODULE_NAME=easy-bag-store
MODULE_USER=$MODULE_NAME

echo "PRE-INSTALL: START (Number of current installations: $NUMBER_OF_INSTALLATIONS)"

if [ $NUMBER_OF_INSTALLATIONS -gt 0 ]; then
    echo -n "Attempting to stop service... "
    service $MODULE_NAME stop  2> /dev/null 1> /dev/null
    if [ $? -ne 0 ]; then
        systemctl stop $MODULE_NAME 2> /dev/null 1> /dev/null
    fi
    echo "OK"
fi

id -u $MODULE_USER 2> /dev/null 1> /dev/null

if [ "$?" == "1" ]; # User not found
then
    echo -n "Creating module user: $MODULE_USER... "
    useradd $MODULE_USER 2> /dev/null
echo "OK"
else
    echo "Module user $MODULE_USER already exists. No action taken."
fi

echo "PRE-INSTALL: DONE."
