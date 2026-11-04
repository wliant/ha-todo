#!/usr/bin/env bash
# Build the app and web images locally.
#
# Usage: scripts/build.sh [image-tag]

source "$(dirname "$0")/lib/common.sh"
require_command docker

IMAGE_TAG="${1:-${IMAGE_TAG:-latest}}"

log "building ha-todo-app:$IMAGE_TAG"
docker build -t "ha-todo-app:$IMAGE_TAG" "$REPO_ROOT/app"

log "building ha-todo-web:$IMAGE_TAG"
docker build -t "ha-todo-web:$IMAGE_TAG" "$REPO_ROOT/web"

log "built ha-todo-app:$IMAGE_TAG and ha-todo-web:$IMAGE_TAG"
