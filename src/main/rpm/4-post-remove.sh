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

#!/usr/bin/env bash

#
# Note that we DO NOT REMOVE ALL resources created by the installer, but only those that are unlikely
# to have changed during the time that the program was installed. Particularly, we do NOT remove the
# log files and the the bag stores that my have been created.
#

INITD_SCRIPT=/etc/init.d/easy-bag-store
SYSTEMD_UNIT=/usr/lib/systemd/system/easy-bag-store.service

if [ $1 -eq 1 ]; then # Last install, so remove service scripts
    if [ -f $INITD_SCRIPT ]; then
        rm $INITD_SCRIPT
    fi

    if [ -f $SYSTEMD_UNIT ]; then
        rm $SYSTEMD_UNIT
    fi
fi
