#!/usr/bin/env bash
# Testa a lógica pura do server hop na JVM (não precisa de Android SDK).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
P=app/src/main/java/com/meujogo/serverhop
javac -encoding UTF-8 -d "$OUT" "$P/HopMode.java" "$P/ServerHop.java" "$P/ServerPool.java" "$P/Config.java" tests/HopLogicTest.java
java -cp "$OUT" HopLogicTest
