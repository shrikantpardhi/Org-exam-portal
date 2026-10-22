#!/usr/bin/env bash
# Usage: gap-lookup.sh <keyword>
# Prints matching rows from docs/known-gaps.md, one gap per line.
set -euo pipefail

KEYWORD="${1:?usage: gap-lookup.sh <keyword>}"
REGISTER="docs/known-gaps.md"

[ -f "$REGISTER" ] || { echo "register not found: $REGISTER" >&2; exit 1; }

# Match the keyword in any column, print the ID, Area, Status, and Target.
awk -F'|' -v kw="$KEYWORD" '
  /^\|/ && $0 !~ /^\|---/ && $0 !~ /ID[[:space:]]*\|/ {
    if (index(tolower($0), tolower(kw))) {
      gsub(/^[ \t]+|[ \t]+$/, "", $2)
      gsub(/^[ \t]+|[ \t]+$/, "", $3)
      gsub(/^[ \t]+|[ \t]+$/, "", $7)
      gsub(/^[ \t]+|[ \t]+$/, "", $8)
      printf "%s | %s | %s | %s\n", $2, $3, $8, $7
    }
  }
' "$REGISTER"