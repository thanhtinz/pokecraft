#!/usr/bin/env bash
# Build the SerpLumen .mcaddon from the two pack folders.
#
# Reproduces the project's packaging exactly: each pack is zipped on its own
# (keeping its nested SerpLumen<ver>B / SerpLumen<ver>R folder), then the two
# zips are combined into the .mcaddon. Version is read from the BP manifest.
set -euo pipefail
cd "$(dirname "$0")"

BP=$(ls -d SerpLumen*B | head -1)
RP=$(ls -d SerpLumen*R | head -1)

# version string like 3_33_0 from the BP folder name
VER=$(echo "$BP" | sed -E 's/^SerpLumen([0-9_]+)B$/\1/')
OUT="dist/SerpLumen_v${VER}.mcaddon"

echo "BP=$BP  RP=$RP  ->  $OUT"

# sanity: both manifests must parse
for m in "$BP/manifest.json" "$RP/manifest.json"; do
  node -e "JSON.parse(require('fs').readFileSync('$m','utf8'))" || { echo "bad manifest: $m"; exit 1; }
done

# syntax-check every script before packaging
for f in "$BP"/scripts/*.js; do node --check "$f"; done
echo "all scripts pass node --check"

mkdir -p dist
rm -f a.zip b.zip "$OUT"
zip -r -X -q a.zip "$BP"
zip -r -X -q b.zip "$RP"
zip -X -q "$OUT" a.zip b.zip
rm -f a.zip b.zip

echo "built $OUT ($(du -h "$OUT" | cut -f1))"
