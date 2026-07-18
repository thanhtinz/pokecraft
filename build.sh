#!/usr/bin/env bash
# Build the SerpLumen .mcaddon from the bp/ (behavior) and rp/ (resource) packs.
#
# Version comes from bp/manifest.json (header.version). At package time each
# pack is copied into a versioned folder (SerpLumen<ver>B / SerpLumen<ver>R),
# each is zipped on its own, then the two zips are combined into the .mcaddon -
# the exact structure the project ships.
set -euo pipefail
cd "$(dirname "$0")"

BP=bp
RP=rp

VERDOT=$(node -e "console.log(JSON.parse(require('fs').readFileSync('$BP/manifest.json','utf8')).header.version.join('.'))")
VERUS=${VERDOT//./_}
OUT="dist/SerpLumen_v${VERUS}.mcaddon"

echo "version=$VERDOT  ->  $OUT"

# both manifests must parse
for m in "$BP/manifest.json" "$RP/manifest.json"; do
  node -e "JSON.parse(require('fs').readFileSync('$m','utf8'))" || { echo "bad manifest: $m"; exit 1; }
done

# syntax-check every script before packaging
for f in "$BP"/scripts/*.js; do node --check "$f"; done
echo "all scripts pass node --check"

TMP=$(mktemp -d)
cp -r "$BP" "$TMP/SerpLumen${VERUS}B"
cp -r "$RP" "$TMP/SerpLumen${VERUS}R"
(
  cd "$TMP"
  zip -r -X -q a.zip "SerpLumen${VERUS}B"
  zip -r -X -q b.zip "SerpLumen${VERUS}R"
  zip -X -q addon.mcaddon a.zip b.zip
)
mkdir -p dist
rm -f "$OUT"
mv "$TMP/addon.mcaddon" "$OUT"
rm -rf "$TMP"

echo "built $OUT ($(du -h "$OUT" | cut -f1))"
