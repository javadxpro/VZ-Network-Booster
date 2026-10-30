#!/usr/bin/env bash
# Reports exactly which build tools exist and which are missing.
# Usage: scripts/verify-toolchain.sh [--check]
#   --check : print a short status table (exit 1 if a required tool is missing)
#   default : full report including pinned versions and install hints
set -uo pipefail

CHECK_ONLY=0
[[ "${1:-}" == "--check" ]] && CHECK_ONLY=1

REQUIRED_JDK_MAJOR=17
REQUIRED_GRADLE=8.9
REQUIRED_PLATFORM=android-35
REQUIRED_BUILD_TOOLS=35.0.0
REQUIRED_NDK=27.0.12077973
REQUIRED_CMAKE=3.22.1

missing=0
status_rows=()

have() { command -v "$1" >/dev/null 2>&1; }

jdk_version=""
if have java; then
  jdk_version=$(java -version 2>&1 | head -1)
fi

sdk_home="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -z "$sdk_home" && -d "$HOME/Android/Sdk" ]] && sdk_home="$HOME/Android/Sdk"

check_java() {
  if [[ -z "$jdk_version" ]]; then
    status_rows+=("JDK ${REQUIRED_JDK_MAJOR} | MISSING | install Temurin 17: https://adoptium.net/temurin/releases/?version=17")
    missing=1
    return
  fi
  local major
  major=$(echo "$jdk_version" | sed -n 's/.*version "\([0-9]*\).*/\1/p')
  if [[ "$major" == "$REQUIRED_JDK_MAJOR" ]]; then
    status_rows+=("JDK ${REQUIRED_JDK_MAJOR} | OK | ${jdk_version}")
  else
    status_rows+=("JDK ${REQUIRED_JDK_MAJOR} | WRONG | found: ${jdk_version}")
    missing=1
  fi
}

check_gradle() {
  if [[ -x ./gradlew ]]; then
    status_rows+=("Gradle wrapper | OK | gradle-wrapper.properties -> ${REQUIRED_GRADLE}")
  else
    status_rows+=("Gradle wrapper | MISSING | ./gradlew not found (should be committed)")
    missing=1
  fi
}

check_sdk_dir() {
  local path="$1" label="$2" hint="$3"
  if [[ -n "$sdk_home" && -e "$path" ]]; then
    status_rows+=("$label | OK | $path")
  else
    status_rows+=("$label | MISSING | $hint")
    missing=1
  fi
}

check_java
check_gradle

if [[ -n "$sdk_home" ]]; then
  check_sdk_dir "$sdk_home/platforms/$REQUIRED_PLATFORM" "Android Platform 35" \
    "sdkmanager \"platforms;$REQUIRED_PLATFORM\""
  check_sdk_dir "$sdk_home/build-tools/$REQUIRED_BUILD_TOOLS" "Build Tools 35.0.0" \
    "sdkmanager \"build-tools;$REQUIRED_BUILD_TOOLS\""
  check_sdk_dir "$sdk_home/ndk/$REQUIRED_NDK" "NDK 27.0.12077973 (optional)" \
    "sdkmanager \"ndk;$REQUIRED_NDK\""
  check_sdk_dir "$sdk_home/cmake/$REQUIRED_CMAKE" "CMake 3.22.1 (optional)" \
    "sdkmanager \"cmake;$REQUIRED_CMAKE\""
else
  status_rows+=("Android SDK | MISSING | set ANDROID_SDK_ROOT or install via cmdline-tools")
  missing=1
fi

if have ninja; then
  status_rows+=("Ninja | OK | $(ninja --version)")
else
  status_rows+=("Ninja | MISSING | apt install ninja-build (needed only for native builds)")
fi

printf '%-28s | %-8s | %s\n' "TOOL" "STATUS" "DETAIL"
printf '%-28s-+-%-8s-+-%s\n' "----------------------------" "--------" "----------------------------------------"
for row in "${status_rows[@]}"; do
  printf '%-28s\n' "$row" | awk -F'|' '{printf "%-28s | %-8s | %s\n", $1, $2, $3}'
done

echo
echo "Pinned (project contract): JDK ${REQUIRED_JDK_MAJOR}, Gradle ${REQUIRED_GRADLE}, compileSdk 35," \
     "build-tools ${REQUIRED_BUILD_TOOLS}, NDK ${REQUIRED_NDK}, CMake ${REQUIRED_CMAKE}, ninja (system)."
echo "compatibility: AGP 8.7.x requires Gradle >= 8.9 and JDK 17; compileSdk 35 requires AGP >= 8.6."

if [[ $CHECK_ONLY -eq 1 && $missing -ne 0 ]]; then
  exit 1
fi
exit 0
