#!/usr/bin/env sh
set -eu

for t in tests/story-0_1/*_test.sh; do
  sh "$t"
done

printf 'All tests passed\n'
