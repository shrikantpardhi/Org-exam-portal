#!/usr/bin/env bash
# Usage: context-pack.sh <area>
# area: identity | academic | content | assessment | delivery | evaluation |
#       pilot | analytics | commerce | communication | admin | hardening | reporting
# Prints the minimal file list and the sed commands to load only the needed ranges.
set -euo pipefail

AREA="${1:?usage: context-pack.sh <area>}"
SPECS="specs"

case "$AREA" in
  identity)      FILES=("$SPECS/10-P1-Identity-V1.5.md");                RANGES=("1,400p") ;;
  academic)      FILES=("$SPECS/11-P2-Academic-V1.5.md");                RANGES=("1,400p") ;;
  content)       FILES=("$SPECS/12-P3-Content-V1.5.md" "$SPECS/08-Exam-Engine-and-CDUI-Contract-V1.5.md"); RANGES=("101,200p" "41,47p") ;;
  assessment)    FILES=("$SPECS/13-P4-Assessment-V1.5.md");              RANGES=("1,400p") ;;
  delivery)      FILES=("$SPECS/14-P5-Delivery-V1.5.md");                RANGES=("21,58p") ;;
  evaluation)    FILES=("$SPECS/15-P6-Evaluation-V1.5.md");              RANGES=("5,120p") ;;
  pilot)         FILES=("$SPECS/16-P7-Pilot-V1.5.md");                   RANGES=("24,120p") ;;
  analytics)     FILES=("$SPECS/17-P8-Analytics-V1.5.md");               RANGES=("1,400p") ;;
  commerce)      FILES=("$SPECS/18-P9-Commerce-V1.5.md");                RANGES=("1,300p") ;;
  communication) FILES=("$SPECS/19-P10-Communication-V1.5.md");          RANGES=("1,300p") ;;
  admin)         FILES=("$SPECS/20-P11-Administration-V1.5.md");         RANGES=("1,300p") ;;
  hardening)     FILES=("$SPECS/21-P12-Hardening-V1.5.md");              RANGES=("1,300p") ;;
  reporting)     FILES=("$SPECS/22-P13-Reporting-V1.5.md");              RANGES=("1,300p") ;;
  *) echo "unknown area: $AREA" >&2; exit 1 ;;
esac

echo "Read only these ranges:"
for i in "${!FILES[@]}"; do
  printf '  sed -n %q %q\n' "${RANGES[$i]}" "${FILES[$i]}"
done
echo
echo "Then:"
echo "  scripts/gap-lookup.sh <keyword>"
echo "  cat AGENTS.md"