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
INSTALL_DIR=/opt/dans.knaw.nl/$MODULE_NAME
PHASE="POST-INSTALL"

echo "$PHASE: START (Number of current installations: $NUMBER_OF_INSTALLATIONS)"
service_install_initd_service_script "$INSTALL_DIR/install/$MODULE_NAME-initd.sh" $MODULE_NAME
service_install_systemd_unit "$INSTALL_DIR/install/$MODULE_NAME.service" $MODULE_NAME "$INSTALL_DIR/install/override.conf"
service_create_log_directory $MODULE_NAME
echo "$PHASE: DONE"
