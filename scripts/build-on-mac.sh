#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -x ./gradlew ]; then
  GRADLE=./gradlew
elif command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
else
  echo 'Gradle not found. Open this folder in Android Studio, or install Gradle 8.13 and create the wrapper.' >&2
  exit 1
fi
"$GRADLE" :app:testDebugUnitTest :app:assembleDebug
printf '\nAPK: app/build/outputs/apk/debug/app-debug.apk\n'
