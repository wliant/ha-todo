#!/usr/bin/env bash
# Shared helpers. Source this; do not execute it.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOYMENT_DIR="$REPO_ROOT/deployment"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarn:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# Usage: require_env VAR_NAME...
require_env() {
    local missing=()
    local name
    for name in "$@"; do
        if [[ -z "${!name:-}" ]]; then
            missing+=("$name")
        fi
    done
    if (( ${#missing[@]} )); then
        die "missing required variable(s): ${missing[*]} (set them in ${ENV_FILE:-deployment/.env.<env>})"
    fi
}

require_command() {
    local name
    for name in "$@"; do
        command -v "$name" >/dev/null 2>&1 || die "required command not found: $name"
    done
}

# Usage: load_env <dev|prod>
load_env() {
    local env_name="$1"
    case "$env_name" in
        dev|prod) ;;
        *) die "unknown environment '$env_name' (expected: dev or prod)" ;;
    esac

    ENV_NAME="$env_name"
    ENV_FILE="$DEPLOYMENT_DIR/.env.$env_name"
    COMPOSE_FILE="docker-compose.$env_name.yml"

    [[ -f "$ENV_FILE" ]] || die "no $ENV_FILE - copy deployment/.env.example and fill it in"

    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
}

# All SSH goes through here. Deployment is scripted end to end; nobody should be
# logging into a server to run steps by hand.
ssh_run() {
    require_env SSH_USER DEPLOY_HOST
    ssh -p "${SSH_PORT:-22}" \
        -o BatchMode=yes \
        -o StrictHostKeyChecking=accept-new \
        "$SSH_USER@$DEPLOY_HOST" "$@"
}

scp_to_target() {
    require_env SSH_USER DEPLOY_HOST DEPLOY_DIR
    scp -P "${SSH_PORT:-22}" \
        -o BatchMode=yes \
        -o StrictHostKeyChecking=accept-new \
        "$@" "$SSH_USER@$DEPLOY_HOST:$DEPLOY_DIR/"
}

confirm_or_abort() {
    local prompt="$1" expected="$2" answer
    read -r -p "$prompt" answer
    [[ "$answer" == "$expected" ]] || die "aborted"
}
