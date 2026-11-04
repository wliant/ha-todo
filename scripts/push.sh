#!/usr/bin/env bash
# Tag the local images for the registry and push them.
#
# Usage: scripts/push.sh <dev|prod>

source "$(dirname "$0")/lib/common.sh"
require_command docker

[[ $# -eq 1 ]] || die "usage: $(basename "$0") <dev|prod>"
load_env "$1"
require_env REGISTRY IMAGE_TAG

for image in app web; do
    local_ref="ha-todo-$image:$IMAGE_TAG"
    remote_ref="$REGISTRY/ha-todo-$image:$IMAGE_TAG"

    docker image inspect "$local_ref" >/dev/null 2>&1 \
        || die "$local_ref not found locally - run scripts/build.sh $IMAGE_TAG first"

    log "pushing $remote_ref"
    docker tag "$local_ref" "$remote_ref"
    docker push "$remote_ref"
done

log "pushed both images to $REGISTRY"
