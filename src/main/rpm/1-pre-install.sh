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
echo "Executing PRE-INSTALL. Number of current installations: $NUMBER_OF_INSTALLATIONS"

USER_NAME=easy-bag-store
id -u $USER_NAME 2> /dev/null 1> /dev/null

if [ "$?" == "1" ]; # User not found
then
    useradd $USER_NAME 2> /dev/null
fi
