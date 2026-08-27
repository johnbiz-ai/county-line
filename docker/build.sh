#!/usr/bin/env bash
# Run a Gradle task inside the County Line build container as the current host
# user, so anything written to build/ and .gradle/ stays owned by you.
#
#   ./docker/build.sh                       # -> ./gradlew assembleDebug
#   ./docker/build.sh :core:test
#   ./docker/build.sh assembleDebug test lint
#
# First run builds the image (~2.5 GB, downloads the Android SDK). Subsequent
# runs reuse it and the cached Gradle dependencies.
set -euo pipefail

cd "$(dirname "$0")/.."

export DOCKER_UID="$(id -u)"
export DOCKER_GID="$(id -g)"

docker compose build android

if [ "$#" -eq 0 ]; then
    set -- assembleDebug
fi

exec docker compose run --rm android ./gradlew "$@"
