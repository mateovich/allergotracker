#!/usr/bin/env sh
set -eu

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

[ -f "docs/verification/story-0-1-bootstrap-verification.md" ] || fail "missing verification evidence document"

grep -q 'Build verification' docs/verification/story-0-1-bootstrap-verification.md || fail "missing build verification evidence"
grep -q 'Run verification' docs/verification/story-0-1-bootstrap-verification.md || fail "missing run verification evidence"
grep -q 'No non-required backend/network dependencies introduced' docs/verification/story-0-1-bootstrap-verification.md || fail "missing dependency guardrail confirmation"
grep -q 'Ready for Story 0.2 and Story 0.3' docs/verification/story-0-1-bootstrap-verification.md || fail "missing readiness confirmation"

if grep -Eq 'retrofit|okhttp|ktor|volley' app/build.gradle.kts; then
  fail "non-required backend/network dependency found in app/build.gradle.kts"
fi

printf 'PASS: task3_verification_guardrails_test\n'
