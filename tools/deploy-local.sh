#!/usr/bin/env bash
# Deploy the freshly built mod jar into the local test instance and remove old versions.
#
# Usage: bash tools/deploy-local.sh          (after gradlew build)
#
# The target instance is the team-play 1.21.10 profile; only voidmaw-*.jar files are
# touched - other mods in that folder are never modified.
set -e
cd "$(dirname "$0")/.."

MODS="/j/LauncherX/.minecraft/versions/团播-21.10-API0.19.3/mods"
VERSION=$(grep -oP '^mod_version=\K.*' gradle.properties)
JAR="build/libs/voidmaw-${VERSION}.jar"

[ -f "$JAR" ] || { echo "missing $JAR - run 'gradlew build' first"; exit 1; }
[ -d "$MODS" ] || { echo "mods folder not found: $MODS"; exit 1; }

# Remove every older voidmaw jar from the instance, then copy the new one.
rm -f "$MODS"/voidmaw-*.jar
cp "$JAR" "$MODS"/

echo "deployed voidmaw-${VERSION} -> $MODS"
ls "$MODS" | grep voidmaw || true
