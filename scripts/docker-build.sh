#!/usr/bin/env bash
# Baut die Debug-APK in einem Docker-Container (Gradle-Cache bleibt im Volume erhalten).
set -euo pipefail
cd "$(dirname "$0")/.."
docker build -t freedarts-builder .
docker run --rm -v "$PWD":/src -v freedarts-gradle:/root/.gradle freedarts-builder
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
