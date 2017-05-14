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

NUMBER_OF_INSTALLATIONS=$1
echo "Executing POST-INSTALL. Number of current installations: $NUMBER_OF_INSTALLATIONS"

INSTALL_DIR=/opt/dans.knaw.nl/easy-bag-store
LOGDIR=/var/opt/dans.knaw.nl/log/easy-bag-store
BAG_STAGING_DIR=/srv/dans.knaw.nl/stage
DEFAULT_BAG_STORE=/srv/dans.knaw.nl/bag-store
BAG_STORE_USER=easy-bag-store
INITD_SCRIPTS=/etc/init.d
SYSTEMD_SCRIPTS=/usr/lib/systemd/system

if [ $NUMBER_OF_INSTALLATIONS -eq 1 ]; then # First install
    echo "First time install, replacing default config with RPM-aligned one"
    #
    # Temporary arrangement to make sure the default config settings align with the FHS-abiding
    # RPM installation
    #
    rm /etc/opt/dans.knaw.nl/easy-bag-store/logback-service.xml
    mv /etc/opt/dans.knaw.nl/easy-bag-store/rpm-logback-service.xml /etc/opt/dans.knaw.nl/easy-bag-store/logback-service.xml

    rm /etc/opt/dans.knaw.nl/easy-bag-store/application.properties
    mv /etc/opt/dans.knaw.nl/easy-bag-store/rpm-application.properties /etc/opt/dans.knaw.nl/easy-bag-store/application.properties
fi

if [ ! -d $LOGDIR ]; then
    mkdir -p $LOGDIR
    chown $BAG_STORE_USER $LOGDIR
fi

if [ ! -d $DEFAULT_BAG_STORE ]; then
    mkdir -p $DEFAULT_BAG_STORE
    chown $BAG_STORE_USER $DEFAULT_BAG_STORE
fi

if [ ! -d $BAG_STAGING_DIR ]; then
    mkdir $BAG_STAGING_DIR
    chown $BAG_STORE_USER $BAG_STAGING_DIR
fi
if [ -d $INITD_SCRIPTS ]; then
    cp $INSTALL_DIR/bin/easy-bag-store-initd.sh $INITD_SCRIPTS/easy-bag-store
fi

if [ -d $SYSTEMD_SCRIPTS ]; then
    cp $INSTALL_DIR/bin/easy-bag-store.service $SYSTEMD_SCRIPTS/
fi


