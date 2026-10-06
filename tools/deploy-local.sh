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

# Finish and verify the copy before removing the previous installed version.
TARGET="$MODS/$(basename "$JAR")"
cp "$JAR" "$TARGET.tmp"
cmp -s "$JAR" "$TARGET.tmp"
mv -f "$TARGET.tmp" "$TARGET"
for old in "$MODS"/voidmaw-*.jar; do
    [ "$old" = "$TARGET" ] || rm -f "$old"
done

echo "deployed voidmaw-${VERSION} -> $MODS"
ls "$MODS" | grep voidmaw || true
