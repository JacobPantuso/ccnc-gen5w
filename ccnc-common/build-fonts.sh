#!/bin/bash
# Subsets the full CCNC signature fonts (../ccnc-home/fonts) into lean per-brand
# bundles under fonts/<brand>/ for APK assets. Requires a python venv with fonttools;
# falls back to copying the full fonts when pyftsubset is unavailable.
set -euo pipefail
cd "$(dirname "$0")"

SRC="../ccnc-home/fonts"
OUT="fonts"

# ASCII + Latin-1 supplement + typographic punctuation + symbols we render
# (degree, arrows, math, private-use icon range used by icomoon).
UNICODE="U+0020-007E,U+00A0-00FF,U+0100-017F,U+2013-2014,U+2018-201A,U+201C-201E,U+2020-2022,U+2026,U+2030,U+2039-203A,U+20AC,U+2122,U+2190-2193,U+21B5,U+2260-2265,U+00B0,U+00B7,U+2212,U+F000-F1FF,U+1F000-1F0FF,U+1F300-1FAFF"

PYFTSUBSET=""
if command -v pyftsubset >/dev/null 2>&1; then
  PYFTSUBSET=pyftsubset
elif [ -x "/tmp/fontenv/bin/pyftsubset" ]; then
  PYFTSUBSET=/tmp/fontenv/bin/pyftsubset
fi

subset() { # brand srcfile outname
  local brand="$1" src="$2" out="$3"
  mkdir -p "$OUT/$brand"
  if [ -n "$PYFTSUBSET" ]; then
    "$PYFTSUBSET" "$SRC/$src" --output-file="$OUT/$brand/$out" \
      --unicodes="$UNICODE" --layout-features='*' --flavor= \
      --name-IDs='*' --name-legacy --name-languages='*' \
      --notdef-outline --recalc-bounds --drop-tables=
    echo "  $brand/$out (subset $(du -h "$OUT/$brand/$out" | cut -f1))"
  else
    cp "$SRC/$src" "$OUT/$brand/$out"
    echo "  $brand/$out (full $(du -h "$OUT/$brand/$out" | cut -f1); install fonttools for subsets)"
  fi
}

# Keep --flavor= empty and --drop-tables= empty: KitKat's minikin handles plain TTF best.
echo "Hyundai bundle:"
subset hyundai "HyundaiSansUI_JP_KR_Latin-Regular.ttf"    sans_regular.ttf
subset hyundai "HyundaiSansUI_JP_KR_Latin-SemiBold.ttf"   sans_semibold.ttf
subset hyundai "HyundaiSansUI_JP_KR_Latin-Bold.ttf"      sans_bold.ttf
subset hyundai "HyundaiSansUI_Numeric-Regular.ttf"       numeric_regular.ttf
subset hyundai "HyundaiSansUI_Numeric-SemiBold.ttf"      numeric_semibold.ttf

echo "Kia bundle:"
subset kia "KiaSignatureUI_JP_KR_Latin-Regular.ttf"  sans_regular.ttf
subset kia "KiaSignatureUI_JP_KR_Latin-SemiBold.ttf" sans_semibold.ttf
subset kia "KiaSignatureUI_JP_KR_Latin-Bold.ttf"     sans_bold.ttf
subset kia "KiaSignatureUI_Numeric-Regular.ttf"      numeric_regular.ttf
subset kia "KiaSignatureUI_Numeric-SemiBold.ttf"     numeric_semibold.ttf

echo "Done: $(du -sh "$OUT" | cut -f1) total"
