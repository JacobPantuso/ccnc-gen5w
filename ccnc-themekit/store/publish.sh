#!/bin/bash
# Publishes the ccNC Theme Store: builds every pack, the signed index, and uploads them as a new GitHub release of
# JacobPantuso/ccnc-themes (marked latest). ccNC Home reads .../releases/latest/download/themes.json.
#   store/publish.sh <tag>      e.g. store/publish.sh 2026.11.24
# Needs: gh (logged in), keys/ccnc-themes-store.pem. Creates the repo (public) on first use.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO=JacobPantuso/ccnc-themes
TAG=${1:?usage: store/publish.sh <tag>}
for p in packs/*/; do [ -f "$p/theme.json" ] && ./mktheme.sh "$p" >/dev/null; done
python3 -I store/mkstore.py
gh repo view "$REPO" >/dev/null 2>&1 || gh repo create "$REPO" --public --description "ccNC theme packs for Gen5W head units (Theme Store)"
gh release create "$TAG" store/out/* --repo "$REPO" --title "ccNC themes $TAG" --notes "ccNC Theme Store index and packs." --latest
echo "published: https://github.com/$REPO/releases/latest/download/themes.json"
