#!/usr/bin/env bash
# Retries Gradle in CI: dependency metadata downloads occasionally fail with
# transient 404s from Maven Central / the plugin portal (seen once on a
# docs-only commit). Each retry refreshes stale metadata before resolving.
set -uo pipefail

attempts="${GRADLE_CI_ATTEMPTS:-3}"
log="${GRADLE_CI_LOG:-build.log}"

: > "$log"
for i in $(seq 1 "$attempts"); do
  echo "=== gradle attempt $i/$attempts: $*" | tee -a "$log"
  if [[ "$i" -eq 1 ]]; then
    ./gradlew "$@" --no-daemon --console=plain --stacktrace 2>&1 | tee -a "$log" && exit 0
  else
    ./gradlew "$@" --no-daemon --console=plain --stacktrace --refresh-dependencies 2>&1 | tee -a "$log" && exit 0
  fi
  echo "attempt $i failed, retrying in 20s" | tee -a "$log"
  sleep 20
done
echo "all $attempts attempts failed" | tee -a "$log"
exit 1
