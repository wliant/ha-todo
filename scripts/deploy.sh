#!/usr/bin/env bash
# Build, push, and start ha-todo in a target environment.
#
# Everything happens from this machine over SSH. Do not log into the target to
# run any of these steps by hand - if something is missing, add it here.
#
# Usage: scripts/deploy.sh <dev|prod> [image-tag]

source "$(dirname "$0")/lib/common.sh"
require_command docker ssh scp

[[ $# -ge 1 ]] || die "usage: $(basename "$0") <dev|prod> [image-tag]"
load_env "$1"
IMAGE_TAG="${2:-${IMAGE_TAG:-latest}}"
export IMAGE_TAG

# ADMIN_PASSWORD and the object store are checked here rather than discovered
# at startup: without either the app refuses to boot, and the health poll below
# would only fail after the images were already built and pushed.
require_env REGISTRY IMAGE_TAG SSH_USER DEPLOY_HOST DEPLOY_DIR ADMIN_PASSWORD \
    MINIO_ENDPOINT MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_BUCKET

if [[ "$ENV_NAME" == "prod" ]]; then
    warn "about to deploy ha-todo-{app,web}:$IMAGE_TAG to PROD at $DEPLOY_HOST:$DEPLOY_DIR"
    confirm_or_abort "Type 'deploy prod' to continue: " "deploy prod"
fi

log "[1/5] building images"
"$(dirname "$0")/build.sh" "$IMAGE_TAG"

log "[2/5] pushing to $REGISTRY"
"$(dirname "$0")/push.sh" "$ENV_NAME"

log "[3/5] copying compose files to $DEPLOY_HOST:$DEPLOY_DIR"
ssh_run "mkdir -p '$DEPLOY_DIR'"
scp_to_target "$DEPLOYMENT_DIR/$COMPOSE_FILE" "$ENV_FILE"

log "[4/5] starting services on $DEPLOY_HOST"
# --env-file is what makes ${REGISTRY} / ${WEB_PORT} interpolate in the compose
# file. `env_file:` inside the file only populates the container environment, so
# without this the image name resolves to "/ha-todo-app:tag" and the pull fails.
# IMAGE_TAG is passed in the shell environment, which outranks the env file.
# --remove-orphans so a renamed service does not linger from an older release.
COMPOSE_CMD="docker compose --env-file '.env.$ENV_NAME' -f '$COMPOSE_FILE'"
ssh_run "cd '$DEPLOY_DIR' \
    && IMAGE_TAG='$IMAGE_TAG' $COMPOSE_CMD pull \
    && IMAGE_TAG='$IMAGE_TAG' $COMPOSE_CMD up -d --remove-orphans"

log "[5/5] waiting for health"
# Polled through the web service, the same same-origin path a browser uses -
# the app publishes no port of its own.
for attempt in $(seq 1 30); do
    if ssh_run "curl -fsS http://localhost:${WEB_PORT:-80}/actuator/health" 2>/dev/null \
            | grep -q '"status":"UP"'; then
        log "deploy complete: $ENV_NAME is UP on tag $IMAGE_TAG"
        exit 0
    fi
    sleep 5
done

ssh_run "cd '$DEPLOY_DIR' && $COMPOSE_CMD ps; $COMPOSE_CMD logs --tail=40 app" || true
die "service did not report healthy within 150s - check the logs above"
