#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Use the workspace-local toolchain when present; normal installed tools also work.
TOOLS="$(cd .. && pwd)/.toolchain"
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in "$TOOLS"/jdk-*/Contents/Home; do
    if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
  done
fi
if [ -d "$TOOLS/android-sdk" ]; then export ANDROID_HOME="$TOOLS/android-sdk"; fi
if [ -d "$TOOLS/gradle-home" ]; then export GRADLE_USER_HOME="$TOOLS/gradle-home"; fi
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
printf '\nAPK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
