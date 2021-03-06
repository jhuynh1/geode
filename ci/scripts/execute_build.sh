#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

BASE_DIR=$(pwd)

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  SCRIPTDIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
SCRIPTDIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

if [[ -z "${GRADLE_TASK}" ]]; then
  echo "GRADLE_TASK must be set. exiting..."
  exit 1
fi

ROOT_DIR=$(pwd)
BUILD_DATE=$(date +%s)
EMAIL_SUBJECT="results/subject"
EMAIL_BODY="results/body"

GEODE_BUILD_VERSION_FILE=${ROOT_DIR}/geode-build-version/number
GEODE_RESULTS_VERSION_FILE=${ROOT_DIR}/results/number
GEODE_BUILD_VERSION_NUMBER=$(grep "versionNumber *=" geode/gradle.properties | awk -F "=" '{print $2}' | tr -d ' ')
GEODE_BUILD_DIR=/tmp/geode-build
GEODE_PULL_REQUEST_ID_FILE=${ROOT_DIR}/geode/.git/id

if [ -e "${GEODE_PULL_REQUEST_ID_FILE}" ]; then
  GEODE_PULL_REQUEST_ID=$(cat ${GEODE_PULL_REQUEST_ID_FILE})
  FULL_PRODUCT_VERSION="geode-pr-${GEODE_PULL_REQUEST_ID}"
else
  CONCOURSE_VERSION=$(cat ${GEODE_BUILD_VERSION_FILE})
  CONCOURSE_PRODUCT_VERSION=${CONCOURSE_VERSION%%-*}
  GEODE_PRODUCT_VERSION=${GEODE_BUILD_VERSION_NUMBER}
  CONCOURSE_BUILD_SLUG=${CONCOURSE_VERSION##*-}
  BUILD_ID=${CONCOURSE_VERSION##*.}
  FULL_PRODUCT_VERSION=${GEODE_PRODUCT_VERSION}-${CONCOURSE_BUILD_SLUG}
  echo "Concourse VERSION is ${CONCOURSE_VERSION}"
  echo "Geode product VERSION is ${GEODE_PRODUCT_VERSION}"
  echo "Build ID is ${BUILD_ID}"
fi

echo -n "${FULL_PRODUCT_VERSION}" > ${GEODE_RESULTS_VERSION_FILE}

DEFAULT_GRADLE_TASK_OPTIONS="--parallel --console=plain --no-daemon"

SSHKEY_FILE="instance-data/sshkey"

INSTANCE_NAME="$(cat instance-data/instance-name)"
INSTANCE_IP_ADDRESS="$(cat instance-data/instance-ip-address)"
PROJECT="$(cat instance-data/project)"
ZONE="$(cat instance-data/zone)"


echo 'StrictHostKeyChecking no' >> /etc/ssh/ssh_config

scp -i ${SSHKEY_FILE} ${SCRIPTDIR}/capture-call-stacks.sh geode@${INSTANCE_IP_ADDRESS}:.



if [[ -n "${PARALLEL_DUNIT}" && "${PARALLEL_DUNIT}" == "true" ]]; then
  PARALLEL_DUNIT="-PparallelDunit -PdunitDockerUser=geode"
  if [ -n "${DUNIT_PARALLEL_FORKS}" ]; then
    DUNIT_PARALLEL_FORKS="-PdunitParallelForks=${DUNIT_PARALLEL_FORKS}"
  fi
else
  PARALLEL_DUNIT=""
  DUNIT_PARALLEL_FORKS=""
fi


if [ -v CALL_STACK_TIMEOUT ]; then
  ssh -i ${SSHKEY_FILE} geode@${INSTANCE_IP_ADDRESS} "tmux new-session -d -s callstacks; tmux send-keys  ~/capture-call-stacks.sh\ ${PARALLEL_DUNIT}\ ${CALL_STACK_TIMEOUT} C-m"
fi

GRADLE_COMMAND="./gradlew \
    ${DEFAULT_GRADLE_TASK_OPTIONS} \
    build install"

echo "${GRADLE_COMMAND}"
ssh -i ${SSHKEY_FILE} geode@${INSTANCE_IP_ADDRESS} "mkdir -p tmp && cd geode && ${GRADLE_COMMAND}"
