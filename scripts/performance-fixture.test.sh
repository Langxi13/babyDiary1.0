#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/performance/fixtures/staging-scale.sql"

expected_tables=(
  fixture_ones
  fixture_tens
  fixture_hundreds
  fixture_thousands
  fixture_ten_thousands
)

for table in "${expected_tables[@]}"; do
  grep -q "CREATE TEMPORARY TABLE $table" "$FIXTURE"
done

number_sources="$({
  sed -n '/^INSERT INTO fixture_numbers$/,/^WHERE .* < 20000;$/p' "$FIXTURE" |
    sed -nE 's/^(FROM|CROSS JOIN) (fixture_[a-z_]+).*/\2/p'
} | sort -u)"

if [ "$(printf '%s\n' "$number_sources" | sed '/^$/d' | wc -l)" -ne 5 ]; then
  echo "Performance fixture must use five distinct temporary tables to avoid MySQL ERROR 1137" >&2
  exit 1
fi

for table in "${expected_tables[@]}"; do
  if ! grep -qx "$table" <<<"$number_sources"; then
    echo "Performance fixture number generator does not reference $table" >&2
    exit 1
  fi
done

echo "performance fixture avoids reopening a MySQL temporary table"
