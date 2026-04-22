#!/usr/bin/env sh
set -eu

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

[ -f "settings.gradle.kts" ] || fail "missing settings.gradle.kts"
[ -f "app/build.gradle.kts" ] || fail "missing app/build.gradle.kts"
[ -f "app/src/main/java/io/github/mateovich/allergotracker/MainActivity.kt" ] || fail "missing MainActivity.kt"

grep -q 'kotlin("android") version "2.3.10"' build.gradle.kts || fail "kotlin 2.3.10 not configured"
grep -q 'platform("androidx.compose:compose-bom:2026.02.01")' app/build.gradle.kts || fail "Compose BOM not configured"
grep -q 'distributionUrl=https\\://services.gradle.org/distributions/gradle-9.4.0-bin.zip' gradle/wrapper/gradle-wrapper.properties || fail "Gradle 9.4.0 not configured"

printf 'PASS: task1_baseline_test\n'
