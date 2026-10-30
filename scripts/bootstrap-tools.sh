#!/usr/bin/env bash
# Installs the exact toolchain this project pins, on a Linux/macOS machine that
# already has a JDK 17 and internet access to dl.google.com.
#
#   scripts/bootstrap-tools.sh              # install into $ANDROID_SDK_ROOT (default ~/Android/Sdk)
#   ANDROID_SDK_ROOT=/opt/android scripts/bootstrap-tools.sh
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
CMDLINE_TOOLS_ZIP_VERSION="11076708"   # commandlinetools-linux-11076708_latest.zip
CMDLINE_TOOLS_SHA256="2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258"
PACKAGES=(
  "platform-tools"
  "platforms;android-35"
  "build-tools;35.0.0"
  "ndk;27.0.12077973"
  "cmake;3.22.1"
)

if ! command -v java >/dev/null 2>&1; then
  echo "error: JDK 17 required. Install Temurin 17 and re-run." >&2
  exit 1
fi

mkdir -p "$SDK_ROOT/cmdline-tools" "$SDK_ROOT/.tmp"

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  url="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_ZIP_VERSION}_latest.zip"
  echo "downloading $url"
  curl --fail --location --retry 5 --output "$SDK_ROOT/.tmp/cmdline-tools.zip" "$url"
  echo "${CMDLINE_TOOLS_SHA256}  $SDK_ROOT/.tmp/cmdline-tools.zip" | sha256sum --check
  unzip -q -d "$SDK_ROOT/cmdline-tools" "$SDK_ROOT/.tmp/cmdline-tools.zip"
  mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -f "$SDK_ROOT/.tmp/cmdline-tools.zip"
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null
sdkmanager "${PACKAGES[@]}"

cat <<TXT

Toolchain installed in $SDK_ROOT
Add this to your shell profile:

  export ANDROID_SDK_ROOT="$SDK_ROOT"
  export ANDROID_HOME="\$ANDROID_SDK_ROOT"
  export PATH="\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools:\$ANDROID_SDK_ROOT/build-tools/35.0.0:\$PATH"

Then: make env-check && make apk
TXT
