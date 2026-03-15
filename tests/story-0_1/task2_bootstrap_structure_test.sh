#!/usr/bin/env sh
set -eu

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

[ -f "gradlew" ] || fail "missing gradlew"
[ -f "gradlew.bat" ] || fail "missing gradlew.bat"
[ -f "gradle/wrapper/gradle-wrapper.properties" ] || fail "missing gradle wrapper properties"
[ -f "README.md" ] || fail "missing README.md onboarding notes"

[ -d "app/src/main/java/com/example/app/features/ui" ] || fail "missing features/ui structure"
[ -d "app/src/main/java/com/example/app/features/domain" ] || fail "missing features/domain structure"
[ -d "app/src/main/java/com/example/app/features/data" ] || fail "missing features/data structure"
[ -d "app/src/main/java/com/example/app/core" ] || fail "missing core structure"
[ -d "app/src/main/java/com/example/app/navigation" ] || fail "missing navigation structure"

grep -q 'distributionUrl=https\\://services.gradle.org/distributions/gradle-9.4.0-bin.zip' gradle/wrapper/gradle-wrapper.properties || fail "Gradle wrapper is not 9.4.0"
grep -q '## Local setup' README.md || fail "README missing local setup section"
grep -q '## Build and run' README.md || fail "README missing build and run section"

printf 'PASS: task2_bootstrap_structure_test\n'
