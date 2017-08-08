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

set -e # abort when a command fails

DATADIR=data
if [ -e ${DATADIR} ]; then
    mv ${DATADIR} ${DATADIR}-$(date  +"%Y-%m-%d@%H:%M:%S")
fi
mkdir ${DATADIR}

echo -n "Copying test bag stores to $DATADIR..."
mkdir -p $DATADIR/bag-store1
cp -r src/test/resources/bag-store/00 $DATADIR/bag-store1/01
mkdir -p $DATADIR/bag-store2
cp -r src/test/resources/bag-store/00 $DATADIR/bag-store2/02
echo "OK"

echo -n "Copying test bag-sequence to $DATADIR..."
mkdir -p $DATADIR/bags
cp -r src/test/resources/bags/basic-sequence-unpruned-with-refbags $DATADIR/bags/basic-sequence-unpruned-with-refbags
echo "OK"
