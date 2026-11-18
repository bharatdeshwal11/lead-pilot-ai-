#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../backend"
exec ./mvnw -Dtest=MockIntentEvaluationTest test "$@"
