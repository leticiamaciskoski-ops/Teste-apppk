#!/usr/bin/env bash
# Testa a lógica pura (server hop, rota de pesca) na JVM, sem Android SDK.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
P=app/src/main/java/com/meujogo/serverhop
javac -encoding UTF-8 -d "$OUT" "$P/HopMode.java" "$P/ServerHop.java" "$P/ServerPool.java" "$P/Config.java" \
  "$P/ColorMatch.java" "$P/TapPlan.java" "$P/RunStats.java" tests/HopLogicTest.java tests/FishTest.java
java -cp "$OUT" HopLogicTest
java -cp "$OUT" FishTest
