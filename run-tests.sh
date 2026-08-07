#!/usr/bin/env bash
#
# Runs the whole test suite and prints a summary.

set -euo pipefail
cd "$(dirname "$0")"

./mvnw --batch-mode test
