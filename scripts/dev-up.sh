#!/usr/bin/env bash
# Start the local stack: infrastructure first, then the application.
#
# Usage: scripts/dev-up.sh

source "$(dirname "$0")/lib/common.sh"
require_command docker

cd "$DEPLOYMENT_DIR"

log "starting local infrastructure"
docker compose -f docker-compose.infra.yml up -d --wait

log "building and starting app + web"
docker compose -f docker-compose.local.yml up -d --build --wait

WEB_PORT="${WEB_PORT:-5173}"
APP_PORT="${APP_PORT:-8080}"
log "web: http://localhost:$WEB_PORT"
log "api: http://localhost:$APP_PORT/actuator/health"
log "demo sign-in: ana@example.com / password123"
