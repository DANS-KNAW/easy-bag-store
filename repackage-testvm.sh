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


PROJECT_NAME=easy-bag-store
VM_TO_REPACKAGE=test
VMLISTFILE=$TMPDIR/vms
BOX_FILE=${TMPDIR%/}/$VM_TO_REPACKAGE-$(date  +"%Y-%m-%d").box

echo -n "Removing old box file $BOX_FILE if it exists..."
rm $BOX_FILE > /dev/null
echo "OK"

echo -n "Getting name of VM..."
VBoxManage list vms | grep ${PROJECT_NAME}_$VM_TO_REPACKAGE | sed -E 's/^"(.*)".*$/\1/' > $VMLISTFILE
echo "OK"

MATCHING_VMS=$(cat $VMLISTFILE)
echo "Matching VM:"
echo "$MATCHING_VMS"

NUMBER_OF_VMS=$(cat $VMLISTFILE | wc -l)

echo $NUMBER_OF_VMS

if (( $NUMBER_OF_VMS > 1 )); then
    echo "More than one instance found for $VM_TO_REPACKAGE, please make sure there is only one. FAILED."
    exit 1
fi

echo -n "Executing prepare-for-repackage-testvm.sh script on current VM..."
vagrant ssh $VM_TO_REPACKAGE -c 'bash -s' < src/test/resources/prepare-for-repackage.sh
echo "OK"
echo -n "Repackaging VM $MATCHING_VMS..."
vagrant package --base $(echo $MATCHING_VMS) --output $BOX_FILE
echo "OK"
echo "Vagrant box created at: $BOX_FILE"
