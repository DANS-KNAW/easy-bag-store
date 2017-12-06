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

#include <service.sh>

NUMBER_OF_INSTALLATIONS=$1
MODULE_NAME=easy-bag-store
PHASE="POST-REMOVE"

echo "$PHASE: START (Number of current installations: $NUMBER_OF_INSTALLATIONS)"
service_remove_initd_service_script $MODULE_NAME $NUMBER_OF_INSTALLATIONS
service_remove_systemd_unit $MODULE_NAME $NUMBER_OF_INSTALLATIONS
echo "$PHASE: DONE"
