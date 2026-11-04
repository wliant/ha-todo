#!/usr/bin/env bash
# Stop the local stack. Pass --volumes to also discard the local database.
#
# Usage: scripts/dev-down.sh [--volumes]

source "$(dirname "$0")/lib/common.sh"
require_command docker

cd "$DEPLOYMENT_DIR"

EXTRA=()
if [[ "${1:-}" == "--volumes" ]]; then
    warn "discarding local database volume"
    EXTRA=(--volumes)
fi

log "stopping app + web"
docker compose -f docker-compose.local.yml down --remove-orphans

log "stopping infrastructure"
# ${EXTRA[@]+...} guard: bash 3.2 (macOS) treats an empty array as unbound under `set -u`.
docker compose -f docker-compose.infra.yml down --remove-orphans ${EXTRA[@]+"${EXTRA[@]}"}

log "local stack stopped"
