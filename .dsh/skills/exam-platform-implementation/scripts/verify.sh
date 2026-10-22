#!/usr/bin/env bash
# Standard verification. Exits non-zero on any failure.
set -euo pipefail

echo "== backend =="
( cd backend && ./mvnw -q clean verify )

echo "== frontend lint =="
( cd frontend && npm run lint )

echo "== frontend types =="
( cd frontend && npx tsc --noEmit )

echo "== frontend tests =="
( cd frontend && npm test -- --run )

echo "== frontend build =="
( cd frontend && npm run build )

echo "== ok =="