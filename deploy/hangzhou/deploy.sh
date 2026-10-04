#!/usr/bin/env bash
# Builds Ravengard from this checkout and deploys it to the Hangzhou host, where it runs behind
# the network's Velocity. Only files that differ from the host are uploaded; the host's
# configuration/ and localization/ trees end up identical to the repository's (with the
# public resource pack URL from deploy/hangzhou/config.docker.yml).
#
# usage: deploy/hangzhou/deploy.sh [--force|--full]   (--force restarts even with players online; --full allows a full upload)
set -euo pipefail

HOST=${RAVENGARD_HOST:-root@t15.sjcmc.cn}
PORT=${RAVENGARD_SSH_PORT:-36053}
KEY=${RAVENGARD_SSH_KEY:-$HOME/.ssh/sjcmc_hz_ed25519}
REMOTE=/mnt/data/minecraft/ravengard/server
FORCE=${1:-}

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

remote() {
    local attempt
    for attempt in $(seq 1 15); do
        if ssh -i "$KEY" -o BatchMode=yes -o ConnectTimeout=20 -p "$PORT" "$HOST" "$@"; then return 0; fi
        local code=$?
        [ $code -ne 255 ] && return $code
        sleep 15
    done
    return 255
}

remote_capture() {
    local out=$1 attempt
    shift
    for attempt in $(seq 1 15); do
        if ssh -i "$KEY" -o BatchMode=yes -o ConnectTimeout=20 -p "$PORT" "$HOST" "$@" > "$out.part"; then
            mv "$out.part" "$out"
            return 0
        fi
        sleep 15
    done
    return 1
}

upload() {
    local attempt
    for attempt in $(seq 1 15); do
        scp -q -i "$KEY" -P "$PORT" -o BatchMode=yes "$1" "$HOST:$2" 2>/dev/null && return 0
        sleep 10
    done
    return 1
}

echo "== building"
(cd "$ROOT" && ./gradlew.bat :loader:shadowJar :velocity.extension:shadowJar :packer:shadowJar --offline --no-daemon -q)

echo "== staging"
mkdir -p "$STAGE/jars" "$STAGE/localization"
cp "$ROOT/loader/build/libs/HypixelCore.jar" "$ROOT/velocity.extension/build/libs/SkyBlockProxy.jar"     "$ROOT/packer/build/libs/net.swofty.packer.HypixelPackServer.jar" "$STAGE/jars/"
for jar in ServiceOrchestrator.jar ServiceParty.jar; do
    cp "$ROOT/DockerFiles/jars/$jar" "$STAGE/jars/"
done
cp -r "$ROOT/configuration" "$STAGE/configuration"
rm -f "$STAGE/configuration/forwarding.secret"
cp "$ROOT/deploy/hangzhou/config.docker.yml" "$STAGE/configuration/config.docker.yml"
cp -r "$ROOT/localization/zh_CN" "$STAGE/localization/zh_CN"
cp -r "$ROOT/deploy/hangzhou/docker" "$STAGE/docker"
cp "$ROOT/deploy/hangzhou/docker-compose.yml" "$STAGE/docker-compose.yml"
(cd "$STAGE/jars" && sha256sum *.jar > SHA256SUMS)

echo "== comparing with the host"
export LC_ALL=C
(cd "$STAGE" && find configuration localization docker jars docker-compose.yml -type f -print0 \
    | xargs -0 md5sum | sed 's/ \*/  /' | sort -k2) > "$STAGE.local"
remote_capture "$STAGE.remote" "cd $REMOTE && { find configuration localization docker jars docker-compose.yml -type f -print0 2>/dev/null || true; } \
    | xargs -0 -r md5sum" || { echo "cannot list the host's files" >&2; exit 1; }
sort -k2 -o "$STAGE.remote" "$STAGE.remote"
changed=$(join -1 2 -2 2 -a 1 -e - -o 0,1.1,2.1 "$STAGE.local" "$STAGE.remote" | awk '$2!=$3 {print $1}')
removed=$(join -1 2 -2 2 -v 2 "$STAGE.local" "$STAGE.remote" | awk '{print $1}' \
    | grep -E '^(configuration|localization)/' || true)
rm -f "$STAGE.local" "$STAGE.remote"
changed_count=$(echo "$changed" | grep -c . || true)
echo "$changed_count changed, $(echo "$removed" | grep -c . || true) removed"
if [ "$changed_count" -gt 500 ] && [ "$FORCE" != "--full" ]; then
    echo "refusing to upload $changed_count files; the host listing is probably incomplete (re-run, or pass --full)" >&2
    exit 1
fi

if [ -n "$changed" ]; then
    echo "== uploading changes"
    (cd "$STAGE" && echo "$changed" | tar czf "$STAGE.tgz" -T -)
    mkdir -p "$STAGE.parts"
    split -b 4M -d -a 3 "$STAGE.tgz" "$STAGE.parts/part."
    remote "rm -rf $REMOTE/../upload && mkdir -p $REMOTE/../upload/parts"
    for part in "$STAGE.parts"/part.*; do
        upload "$part" "$REMOTE/../upload/parts/$(basename "$part")" &
        while [ "$(jobs -r | wc -l)" -ge 4 ]; do sleep 1; done
    done
    wait
    expected=$(sha256sum "$STAGE.tgz" | cut -c1-64)
    actual=$(remote "cat $REMOTE/../upload/parts/part.* | tee $REMOTE/../upload/changes.tgz | sha256sum | cut -c1-64")
    rm -rf "$STAGE.tgz" "$STAGE.parts"
    [ "$expected" = "$actual" ] || { echo "upload checksum mismatch" >&2; exit 1; }
fi

players=$(remote "ss -Htn state established '( dport = :25571 )' | wc -l")
if [ "$players" -gt 0 ] && [ "$FORCE" != "--force" ]; then
    echo "$players player(s) are in Ravengard; changes are uploaded but not applied. Re-run with --force to restart anyway." >&2
    exit 2
fi

echo "== applying"
remote "set -e; cd $REMOTE
[ -f ../upload/changes.tgz ] && tar xzf ../upload/changes.tgz && rm -f ../upload/changes.tgz
$([ -n "$removed" ] && printf 'rm -f -- %q\n' $removed)
(cd jars && sha256sum -c --quiet SHA256SUMS)
docker compose build -q
docker compose up -d
for i in \$(seq 1 150); do docker compose logs --since 5m ravengard_dungeon 2>&1 | grep -aq 'Registered static dungeon interactables' && break; sleep 3; done
docker compose ps --format '{{.Name}} {{.Status}}'"
echo "== deployed"
