#!/usr/bin/env bash
#
# Launches the ATM command line application.
#
# The first run downloads Maven and the project's dependencies, so it takes a minute or two and
# needs internet access. Subsequent runs start immediately from the local cache.
#
# Every invocation starts an empty bank: state is held in memory only, so stopping the process is
# the only reset there is.

set -euo pipefail
cd "$(dirname "$0")"

echo "Building... (first run downloads dependencies)" >&2
./mvnw --quiet --batch-mode -DskipTests package

echo >&2
exec java -jar atm-cli/target/atm-cli.jar "$@"
