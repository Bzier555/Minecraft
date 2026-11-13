#!/usr/bin/env bash
# Build a CurseForge-importable client profile zip for the spike.
# Usage: ./build-client-zip.sh <server-dir> <output.zip> [profile name]
# Copies the server's mod jars (minus server-only tools) and adds Xaero's maps.
set -euo pipefail
SERVER="${1:?server dir}"; OUT="${2:?output zip}"; NAME="${3:-Civ Spike NeoForge 1.21.1}"
WORK="$(mktemp -d)"
mkdir -p "$WORK/overrides/mods"
for j in "$SERVER"/mods/*.jar; do
  case "$(basename "$j")" in Chunky-*|civspike-*) continue ;; esac
  cp "$j" "$WORK/overrides/mods/"
done
curl -fsL -o "$WORK/overrides/mods/xaerominimap-neoforge-1.21.1-26.6.0.jar" "https://cdn.modrinth.com/data/1bokaNcj/versions/irjR7dHk/xaerominimap-neoforge-1.21.1-26.6.0.jar"
curl -fsL -o "$WORK/overrides/mods/xaeroworldmap-neoforge-1.21.1-1.47.0.jar" "https://cdn.modrinth.com/data/NcUtCpym/versions/SnQ4SoDe/xaeroworldmap-neoforge-1.21.1-1.47.0.jar"
cat > "$WORK/manifest.json" <<EOF
{
  "minecraft": { "version": "1.21.1", "modLoaders": [ { "id": "neoforge-21.1.265", "primary": true } ] },
  "manifestType": "minecraftModpack",
  "manifestVersion": 1,
  "name": "$NAME",
  "version": "0.1.0",
  "author": "Civ project",
  "files": [],
  "overrides": "overrides"
}
EOF
rm -f "$OUT"
( cd "$WORK" && python -c "import shutil,sys; shutil.make_archive(sys.argv[1], 'zip', '.')" "${OUT%.zip}" )
rm -rf "$WORK"
echo "Wrote $OUT"
