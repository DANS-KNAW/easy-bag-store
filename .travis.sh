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

GH_ORG=DANS-KNAW

set -e

# Relies on Travis checking out repo to a dir with the repo's name.
GH_REPO=$(basename $TRAVIS_BUILD_DIR)
REMOTE="https://${GH_TOKEN}@github.com/${GH_ORG}/${GH_REPO}"
git remote set-url origin ${REMOTE}

echo "START installing required Python packages..."
pip3 install mkdocs
pip3 install pygments
pip3 install pymdown-extensions
pip3 install pyyaml
pip3 install mkdocs-markdownextradata-plugin
echo "DONE installing required Python packages."

echo "START installing DANS mkdocs theme..."
git clone https://github.com/Dans-labs/mkdocs-dans $HOME/mkdocs-dans
pushd $HOME/mkdocs-dans
git pull
python3 build.py pack
popd
echo "DONE installing DANS mkdocs theme."

echo "START deploying docs to GitHub pages..."
mkdocs gh-deploy --force
echo "DONE deploying docs to GitHub pages."
