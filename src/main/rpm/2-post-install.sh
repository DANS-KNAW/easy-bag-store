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
INSTALL_DIR=/opt/dans.knaw.nl/$MODULE_NAME
LOG_DIR=/var/opt/dans.knaw.nl/log/$MODULE_NAME
INITD_SCRIPTS_DIR=/etc/init.d
SYSTEMD_SCRIPTS_DIR=/usr/lib/systemd/system
BAG_STAGING_DIR=/srv/dans.knaw.nl/stage
DEFAULT_BAG_STORE=/srv/dans.knaw.nl/bag-store

echo "POST-INSTALL: START (Number of current installations: $NUMBER_OF_INSTALLATIONS)"

if [ $NUMBER_OF_INSTALLATIONS -eq 1 ]; then # First install
    echo "First time install, replacing default config with RPM-aligned one"
    #
    # Temporary arrangement to make sure the default config settings align with the FHS-abiding
    # RPM installation
    #
    rm /etc/opt/dans.knaw.nl/$MODULE_NAME/logback-service.xml
    mv /etc/opt/dans.knaw.nl/$MODULE_NAME/rpm-logback-service.xml /etc/opt/dans.knaw.nl/$MODULE_NAME/logback-service.xml

    rm /etc/opt/dans.knaw.nl/$MODULE_NAME/application.properties
    mv /etc/opt/dans.knaw.nl/$MODULE_NAME/rpm-application.properties /etc/opt/dans.knaw.nl/$MODULE_NAME/application.properties
fi

if [ ! -d $LOG_DIR ]; then
    echo -n "Creating directory for logging... "
    mkdir -p $LOG_DIR
    chown $MODULE_USER $LOG_DIR
    echo "OK"
fi

if [ ! -d $DEFAULT_BAG_STORE ]; then
    echo -n "Creating default bag store... "
    mkdir -p $DEFAULT_BAG_STORE
    chown $MODULE_USER $DEFAULT_BAG_STORE
    echo "OK"
fi

if [ ! -d $BAG_STAGING_DIR ]; then
    echo -n "Creating bag staging directory... "
    mkdir $BAG_STAGING_DIR
    chown $MODULE_USER $BAG_STAGING_DIR
    echo "OK"
fi
if [ -d $INITD_SCRIPTS_DIR ]; then
    echo -n "Installing initd service script... "
    cp $INSTALL_DIR/bin/$MODULE_NAME-initd.sh $INITD_SCRIPTS_DIR/$MODULE_NAME
    chmod u+x $INITD_SCRIPTS_DIR/$MODULE_NAME
    echo "OK"
fi

if [ -d $SYSTEMD_SCRIPTS_DIR ]; then
    echo -n "Installing systemd service script... "
    cp $INSTALL_DIR/bin/$MODULE_NAME.service $SYSTEMD_SCRIPTS_DIR/
    echo "OK"
fi
