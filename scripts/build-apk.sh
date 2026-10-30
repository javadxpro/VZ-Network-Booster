#!/usr/bin/env bash
# Builds the debug APK (and runs the JVM unit tests first) and copies it to dist/.
set -euo pipefail

if [[ ! -x ./gradlew ]]; then
  echo "error: ./gradlew missing — run this from the repository root" >&2
  exit 1
fi

./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain "$@"

mkdir -p dist
apk=$(find app/build/outputs/apk/debug -name '*.apk' | head -1)
if [[ -z "${apk}" ]]; then
  echo "error: no APK produced" >&2
  exit 1
fi
cp "${apk}" dist/
echo "APK: dist/$(basename "${apk}")"
