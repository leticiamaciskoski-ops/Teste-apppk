#!/usr/bin/env bash
# Testa RobloxApi. Precisa do org.json: ./tests/run-api-tests.sh /caminho/json.jar [offline|live]
set -euo pipefail
cd "$(dirname "$0")/.."
JSON_JAR="${1:?informe o caminho do json.jar}"
MODE="${2:-offline}"
OUT="$(mktemp -d)"
P=app/src/main/java/com/meujogo/autoclick
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT" "$P/ServerHop.java" "$P/RobloxApi.java" tests/RobloxApiTest.java
java -cp "$OUT:$JSON_JAR" RobloxApiTest "$MODE"
