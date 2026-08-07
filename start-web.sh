#!/usr/bin/env bash
#
# Launches the browser version: a Spring Boot REST API over the same domain core, plus a small
# single-page front end served from the same process.
#
# Open http://localhost:8080 once it has started. Ctrl-C to stop.
# Set PORT to use a different port, e.g.  PORT=9090 ./start-web.sh

set -euo pipefail
cd "$(dirname "$0")"

PORT="${PORT:-8080}"

echo "Building... (first run downloads dependencies)" >&2
./mvnw --quiet --batch-mode -DskipTests package

echo >&2
echo "  ATM web interface:  http://localhost:${PORT}" >&2
echo "  Press Ctrl-C to stop." >&2
echo >&2

exec java -jar atm-web/target/atm-web.jar --server.port="${PORT}"
