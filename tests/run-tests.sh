#!/usr/bin/env bash
# Testa a lógica pura do autoclick na JVM (não precisa de Android SDK).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
P=app/src/main/java/com/meujogo/autoclick
javac -encoding UTF-8 -d "$OUT" "$P/Config.java" "$P/ColorMatch.java" "$P/TapPlan.java" "$P/RunStats.java" \
  "$P/ServerHop.java" "$P/ServerPool.java" tests/AutoClickLogicTest.java tests/HopLogicTest.java
java -cp "$OUT" AutoClickLogicTest
java -cp "$OUT" HopLogicTest
