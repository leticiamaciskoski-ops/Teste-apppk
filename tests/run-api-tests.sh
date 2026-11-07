#!/usr/bin/env bash
# Uso: tests/run-api-tests.sh <org.json.jar> [offline|live]
set -euo pipefail
cd "$(dirname "$0")/.."
JAR="$1"; MODE="${2:-offline}"
OUT="$(mktemp -d)"
P=app/src/main/java/com/meujogo/serverhop
javac -encoding UTF-8 -cp "$JAR" -d "$OUT" "$P/HopMode.java" "$P/ServerHop.java" "$P/RobloxApi.java" tests/RobloxApiTest.java
java -cp "$OUT:$JAR" RobloxApiTest "$MODE"
