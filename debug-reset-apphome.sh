#!/usr/bin/env bash
#
# Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

APPHOME=home
TEMPDIR=data

rm -fr $APPHOME
cp -r src/main/assembly/dist $APPHOME
cp src/test/resources/debug-config/* $APPHOME/cfg/

mv $TEMPDIR $TEMPDIR-`date  +"%Y-%m-%d@%H:%M:%S"`
mkdir -p $TEMPDIR/bag-store
chmod -R 777 $TEMPDIR

echo "A fresh application home directory for debugging has been set up at $APPHOME"
echo "Output and logging will go to $TEMPDIR"
echo "Add the following VM options to your run configuration to use these directories during debugging:"
echo "-Dapp.home=$APPHOME -Dlogback.configurationFile=$APPHOME/cfg/logback.xml"