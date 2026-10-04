#!/usr/bin/env bash
# Mirror a GitHub release of this mod to Gitee (repo + attach the built jar).
#
# Usage:
#   gradlew build                 # jar must exist in build/libs first
#   bash tools/release-gitee.sh v1.0.0-beta.1
#
# Requirements: the GitHub release for <tag> must already exist (CI creates it on
# tag push); the Gitee token is read from .gitee-token in the repo root (gitignored).
#
# Why local? Gitee blocks cloud-datacenter IPs, so this cannot run on GitHub
# Actions; from a home connection the API works fine.
set -e
TAG="$1"
: "${TAG:?usage: bash tools/release-gitee.sh <tag>}"
cd "$(dirname "$0")/.."

[ -f .gitee-token ] || { echo "missing .gitee-token in repo root"; exit 1; }
TOKEN=$(tr -d ' \r\n' < .gitee-token)

GH_REPO=Wmsqaq/voidmaw-mod
API="https://gitee.com/api/v5/repos/novapupil/voidmaw-mod"

# The jar version must match the tag: v1.0.0-beta.1 -> voidmaw-1.0.0-beta.1.jar
JAR="build/libs/voidmaw-${TAG#v}.jar"
[ -f "$JAR" ] || { echo "missing $JAR — build the matching version first (gradlew.bat build)"; exit 1; }

NOTES=$(curl -sf "https://api.github.com/repos/$GH_REPO/releases/tags/$TAG" | grep -oE '"body": *"[^"]*"' | sed 's/^"body": *"//; s/"$//')
[ -z "$NOTES" ] && NOTES="Automated build for $TAG. See the GitHub release for details."
printf '%s' "$NOTES" > .gitee-notes.tmp

# Body goes through a file so non-ASCII bytes survive Windows argv codepage conversion.
RESP=$(curl -s -X POST "$API/releases" \
  -d "access_token=$TOKEN" \
  -d "tag_name=$TAG" \
  -d "target_commitish=main" \
  --data-urlencode "name=Void Maw $TAG" \
  --data-urlencode "body@.gitee-notes.tmp")
RID=$(echo "$RESP" | grep -oE '"id": *[0-9]+' | head -1 | grep -oE '[0-9]+')
if [ -z "$RID" ]; then
  echo "create failed or release exists; looking up by tag"
  RID=$(curl -s "$API/releases?access_token=$TOKEN" | grep -B4 "\"tag_name\":\"$TAG\"" | grep -oE '"id": *[0-9]+' | head -1 | grep -oE '[0-9]+')
fi
[ -z "$RID" ] && { echo "no Gitee release id found for $TAG"; exit 1; }
rm -f .gitee-notes.tmp

# The attach endpoint occasionally fails right after release creation; retry a few times.
for attempt in 1 2 3 4; do
  ATTACH_URL=$(curl -s -X POST "$API/releases/$RID/attach_files" \
    -F "access_token=$TOKEN" \
    -F "file=@$JAR" | grep -oE '"browser_download_url":"[^"]*"' | head -1)
  if [ -n "$ATTACH_URL" ]; then
    echo "$ATTACH_URL"
    echo "Mirrored $TAG ($JAR) to Gitee."
    exit 0
  fi
  echo "attach attempt $attempt failed, retrying..."
  sleep 3
done
echo "ERROR: could not attach $JAR to Gitee release $RID"
exit 1
