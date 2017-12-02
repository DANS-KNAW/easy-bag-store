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

DATADIR=data

echo -n "Copying test bag stores to $DATADIR..."
cp -r src/test/resources/bag-store1 $DATADIR/
cp -r src/test/resources/bag-store2 $DATADIR/


echo "OK"

echo -n "Copying test bag-sequence to $DATADIR..."
mkdir -p $DATADIR/bags
cp -r src/test/resources/bags/basic-sequence-unpruned-with-refbags $DATADIR/bags/basic-sequence-unpruned-with-refbags
echo "OK"
