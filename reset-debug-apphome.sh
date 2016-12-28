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


rm -fr home/
cp -r src/main/assembly/dist home
cp src/test/resources/debug-config/* home/cfg/

rm -fr out/
chmod -R 777 out/
mkdir -p out/bag-store

echo "A fresh application home directory for debugging has been set up at home/"
echo "Output and logging will go to out/"
echo "Add the following VM options to your run configuration to use these directories during debugging:"
echo "-Dapp.home=home/ -Dlogback.configurationFile=home/cfg/logback.xml"