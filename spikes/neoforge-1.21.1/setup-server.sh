#!/usr/bin/env bash
# Phase 0 platform spike: NeoForge 1.21.1 dedicated server with Exotic Worlds + core mods.
# Usage: ./setup-server.sh <target-dir> [level-type]
# Versions resolved 2026-11-08 (Modrinth API + ldtteam maven). Re-pin before reuse.
set -euo pipefail
TARGET="${1:?target dir}"
LEVEL_TYPE="${2:-exotic_worlds\\:torus_medium}"
NEOFORGE=21.1.265
LDT=https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam

mkdir -p "$TARGET/mods"
cd "$TARGET"

if [ ! -f run.bat ] && [ ! -f run.sh ]; then
  curl -fL -o neoforge-installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEOFORGE/neoforge-$NEOFORGE-installer.jar"
  java -jar neoforge-installer.jar --installServer
  rm -f neoforge-installer.jar
fi

dl() { [ -f "mods/$1" ] || curl -fL -o "mods/$1" "$2"; }
# Modrinth
dl exotic_worlds-1.3.3+mc1.21.1.jar "https://cdn.modrinth.com/data/CdjG7l5z/versions/IfPpBJgZ/exotic_worlds-1.3.3%2Bmc1.21.1.jar"
dl recruits-1.21.1-1.15.2.jar "https://cdn.modrinth.com/data/WOg9lm4u/versions/IYZdzeH9/recruits-1.21.1-1.15.2.jar"
dl open-parties-and-claims-neoforge-1.21.1-0.33.0.jar "https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar"
dl tectonic-3.0.28-neoforge-21.1.jar "https://cdn.modrinth.com/data/lWDHr9jE/versions/n4iyW7aB/tectonic-3.0.28-neoforge-21.1.jar"
dl Terralith_1.21.1_v2.6.2_Neoforge.jar "https://cdn.modrinth.com/data/8oi3bsk5/versions/IY93YaEe/Terralith_1.21.1_v2.6.2_Neoforge.jar"
dl lithostitched-1.8.1-neoforge-21.1.jar "https://cdn.modrinth.com/data/XaDC71GB/versions/cZu4cHY3/lithostitched-1.8.1-neoforge-21.1.jar"
dl spark-1.10.124-neoforge.jar "https://cdn.modrinth.com/data/l6YH9Als/versions/v5qtqRQi/spark-1.10.124-neoforge.jar"
dl Chunky-NeoForge-1.4.23.jar "https://cdn.modrinth.com/data/fALzjamp/versions/LuFhm4eU/Chunky-NeoForge-1.4.23.jar"
# ldtteam maven (MineColonies family is not on Modrinth for NeoForge)
dl minecolonies-1.1.1419-1.21.1-snapshot.jar "$LDT/minecolonies/1.1.1419-1.21.1-snapshot/minecolonies-1.1.1419-1.21.1-snapshot.jar"
dl structurize-1.0.839-1.21.1-snapshot.jar "$LDT/structurize/1.0.839-1.21.1-snapshot/structurize-1.0.839-1.21.1-snapshot.jar"
dl blockui-1.0.212-1.21.1-snapshot.jar "$LDT/blockui/1.0.212-1.21.1-snapshot/blockui-1.0.212-1.21.1-snapshot.jar"
dl domum-ornamentum-1.0.237-snapshot.jar "$LDT/domum-ornamentum/1.0.237-snapshot/domum-ornamentum-1.0.237-snapshot-main.jar"
dl multipiston-1.2.58-1.21.1.jar "$LDT/multipiston/1.2.58-1.21.1/multipiston-1.2.58-1.21.1.jar"

echo "eula=true" > eula.txt
if [ ! -f server.properties ]; then
cat > server.properties <<EOF
level-type=$LEVEL_TYPE
online-mode=true
enable-rcon=true
rcon.password=civspike
rcon.port=25575
view-distance=8
simulation-distance=6
spawn-protection=0
max-tick-time=-1
motd=Civ NeoForge 1.21.1 spike
EOF
fi
echo "Server ready in $TARGET"
