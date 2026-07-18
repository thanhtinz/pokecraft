#!/usr/bin/env bash
# Build the SerpLumen .mcaddon from the bp/ (behavior) and rp/ (resource) packs.
#
# Version comes from bp/manifest.json (header.version). At package time each
# pack is copied into a versioned folder (SerpLumen<ver>B / SerpLumen<ver>R) and
# the two folders are zipped DIRECTLY into the .mcaddon. Minecraft (especially
# iOS/Bedrock) only recurses into folders and .mcpack files when importing a
# .mcaddon - it does NOT open inner .zip files, so a nested a.zip/b.zip layout
# imports as "Unknown Pack". Keeping the pack folders at the archive root is the
# universally-importable structure.
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
  zip -r -X -q addon.mcaddon "SerpLumen${VERUS}B" "SerpLumen${VERUS}R"
)
mkdir -p dist
rm -f "$OUT"
mv "$TMP/addon.mcaddon" "$OUT"
rm -rf "$TMP"

echo "built $OUT ($(du -h "$OUT" | cut -f1))"
