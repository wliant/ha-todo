#!/usr/bin/env bash
# Deploys the Home Assistant custom integration (hass/) and restarts Core.
#
# Usage: ./scripts/deploy-hass.sh <dev|prod> [--no-restart] [--dry-run]
#
#   --dry-run  check the target and report what would happen. Ships nothing,
#              restarts nothing, writes nothing. Exists so the prod path can be
#              proven - right host, right flavour, right directory - without a
#              restart that takes the house down.
#
# Two target flavours, because the two instances are nothing alike:
#
#   compose  a plain Docker Compose Home Assistant with a bind-mounted config
#            dir, alongside a hass-dev checkout that has its own deploy.sh.
#   haos     Home Assistant OS. SSH lands in the `core-ssh` add-on, which has
#            /config but no rsync, no python3 and no docker. Restarting goes
#            through the Supervisor (`ha core restart`).
#
# Read from deployment/.env.<env>:
#
#   HASS_FLAVOUR  compose | haos          (default: compose)
#   HASS_HOST     the Home Assistant host (default: DEPLOY_HOST)
#   HASS_USER     ssh user                (default: SSH_USER)
#   HASS_SSH_PORT ssh port                (default: SSH_PORT, then 22)
#   HASS_DIR      compose: the hass checkout   (default ~HASS_USER/myapps/hass-dev)
#                 haos:    the config dir      (default /config)
#   HASS_PORT     compose only: the port Core answers on (default 4123)
#
# HASS_HOST exists because Home Assistant does not necessarily run on the host
# the application is deployed to. On dev it happens to; on prod it does not,
# and defaulting to DEPLOY_HOST without saying so would quietly ship the
# integration to the wrong machine.
#
# There is no image and no registry push: this is Python that Home Assistant
# imports from a directory, so there is nothing to build and nothing to pull.
# That is why it is its own script rather than a step in scripts/deploy.sh.
#
# All SSH goes through ssh_run. If a step is missing, add it here rather than
# logging in to do it by hand.
set -euo pipefail

# shellcheck source=lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

ENV_ARG="${1:-}"
[[ -n "$ENV_ARG" ]] || die "usage: $0 <dev|prod> [--no-restart]"
shift || true

RESTART=1
DRY_RUN=0
for arg in "$@"; do
    case "$arg" in
        --no-restart) RESTART=0 ;;
        --dry-run) DRY_RUN=1 ;;
        *) die "unknown option: $arg" ;;
    esac
done

load_env "$ENV_ARG"
require_command ssh tar

FLAVOUR="${HASS_FLAVOUR:-compose}"
COMPONENT="$REPO_ROOT/hass/custom_components/ha_todo"
[[ -f "$COMPONENT/manifest.json" ]] || die "no manifest at $COMPONENT"

# A Home Assistant of its own, which may not be the application's host.
SSH_USER="${HASS_USER:-${SSH_USER:-}}"
DEPLOY_HOST="${HASS_HOST:-${DEPLOY_HOST:-}}"
SSH_PORT="${HASS_SSH_PORT:-${SSH_PORT:-22}}"
require_env SSH_USER DEPLOY_HOST

if [[ "$ENV_NAME" == "prod" && $DRY_RUN -eq 0 ]]; then
    # Restarting Core takes every automation, alarm and camera in the house
    # down for the better part of a minute.
    confirm_or_abort "type 'deploy hass prod' to continue: " "deploy hass prod"
fi

log "target: $FLAVOUR at $SSH_USER@$DEPLOY_HOST (env $ENV_NAME)"

# One tarball, streamed. rsync is not available on a HAOS core-ssh add-on, and
# using the same mechanism for both flavours means one thing to get right.
ship() {
    local dest="$1"
    if (( DRY_RUN )); then
        log "[dry run] would ship $(find "$COMPONENT" -name '*.py' | wc -l | tr -d ' ') modules to $DEPLOY_HOST:$dest/ha_todo"
        log "[dry run] would stage at $dest/.ha_todo_staging, then swap"
        return 0
    fi
    log "shipping the integration to $DEPLOY_HOST:$dest"
    ssh_run "rm -rf '$dest/.ha_todo_staging' && mkdir -p '$dest/.ha_todo_staging'"
    tar czf - -C "$REPO_ROOT/hass/custom_components" \
        --exclude '__pycache__' --exclude '*.pyc' ha_todo \
        | ssh_run "tar xzf - -C '$dest/.ha_todo_staging'"
    # Swapped rather than extracted in place: a half-written integration is one
    # Home Assistant may try to import, and tar alone would also leave behind
    # files that this version no longer ships.
    ssh_run "rm -rf '$dest/ha_todo' \
        && mv '$dest/.ha_todo_staging/ha_todo' '$dest/ha_todo' \
        && rmdir '$dest/.ha_todo_staging'"
}

case "$FLAVOUR" in
compose)
    HASS_DIR="${HASS_DIR:-/home/$SSH_USER/myapps/hass-dev}"
    HASS_PORT="${HASS_PORT:-4123}"
    ssh_run "test -d '$HASS_DIR/config/custom_components' || mkdir -p '$HASS_DIR/config/custom_components'"
    ship "$HASS_DIR/config/custom_components"

    if (( DRY_RUN )); then
        log "[dry run] would restart the container and poll port $HASS_PORT"
        exit 0
    fi

    if (( RESTART )); then
        log "restarting Home Assistant"
        ssh_run "cd '$HASS_DIR' && docker compose restart homeassistant >/dev/null"
        log "waiting for it to answer again"
        ssh_run "for i in \$(seq 1 90); do \
            code=\$(curl -s -o /dev/null -w '%{http_code}' 'http://localhost:$HASS_PORT/' || true); \
            case \"\$code\" in 200|30*) echo up; exit 0 ;; esac; sleep 2; \
          done; echo 'timed out' >&2; exit 1"
    fi

    log "checking Home Assistant could import it"
    # Core answering HTTP is not the same as it having finished setting up its
    # integrations, so give it a moment: grepping the instant the port opens
    # reports a clean bill before the failure has been written.
    sleep 10
    # A custom component that fails to import says so once, at startup, and is
    # otherwise simply absent from the integration list. There is no second
    # symptom, so this is the check.
    if ssh_run "cd '$HASS_DIR' && docker compose logs --tail 400 homeassistant 2>&1 \
            | grep -iE 'ha_todo.*(Traceback|Error|failed)' | tail -20" | grep -q .; then
        warn "errors mentioning ha_todo appeared in the log - see them above"
    else
        log "no import errors mentioning ha_todo"
    fi
    ;;

haos)
    HASS_DIR="${HASS_DIR:-/config}"
    ssh_run "test -d '$HASS_DIR/custom_components'" \
        || die "no $HASS_DIR/custom_components on $DEPLOY_HOST - is this the core-ssh add-on?"
    ship "$HASS_DIR/custom_components"

    # Supervisor-side validation, reported but deliberately **not** a gate.
    #
    # Two reasons, and both matter. It cannot see this integration at all: the
    # integration is set up from a config entry rather than from YAML, so
    # `ha core check` never imports it and a syntax error here would sail past.
    # And it runs in a container whose mounts differ from the running Core, so
    # a perfectly good `allowlist_external_dirs` can be reported as "not a
    # directory" - an instance can fail this check permanently while running
    # fine for years.
    #
    # Gating on it would therefore block every deploy over something unrelated,
    # while catching nothing about what is actually being deployed. The real
    # verification is the log check after the restart.
    log "validating the rest of the configuration (informational)"
    if ! ssh_run "ha core check"; then
        warn "'ha core check' reported problems - read them above."
        warn "They are pre-existing config issues, not this integration: the"
        warn "check never imports a config-entry integration. Continuing."
    fi

    if (( DRY_RUN )); then
        log "[dry run] would restart Core, poll it, then check the log for import errors"
        exit 0
    fi

    if (( ! RESTART )); then
        warn "not restarted - Home Assistant will not import the change until it is"
        exit 0
    fi

    log "restarting Home Assistant Core (this interrupts every automation)"
    # Bounded, and its exit status is deliberately ignored.
    #
    # `ha core restart` does not reliably return. It asks the Supervisor and
    # then waits, and the restart it just asked for tears down the connection
    # it is waiting on - observed hanging for over ten minutes on a Raspberry
    # Pi while Home Assistant had in fact restarted and been serving for nine
    # of them. Readiness is decided by the poll below, which asks the only
    # question that matters, so this call only has to be *sent*.
    ssh_run "timeout 120 ha core restart || true"

    log "waiting for Core to answer again"
    # From inside the core-ssh add-on, Core is reachable by name on the
    # supervisor network. Polled rather than trusted: `ha core restart` returns
    # when the Supervisor has done its part, which is not the same as Home
    # Assistant having finished starting.
    ssh_run "for i in \$(seq 1 120); do \
        code=\$(curl -s -o /dev/null -w '%{http_code}' 'http://homeassistant:8123/' || true); \
        case \"\$code\" in 200|30*) echo up; exit 0 ;; esac; sleep 2; \
      done; echo 'timed out waiting for Core' >&2; exit 1"

    log "checking Home Assistant could import it"
    # As above: the port opening precedes integration setup.
    sleep 10
    # Same reasoning as the compose flavour: a custom component that fails to
    # import says so once, at startup, and is otherwise simply absent from the
    # integration list. There is no second symptom.
    if ssh_run "ha core logs 2>&1 | grep -iE 'ha_todo.*(Traceback|Error|failed)' \
            | grep -viE 'not been tested' | tail -20" | grep -q .; then
        warn "errors mentioning ha_todo appeared in the log - see them above"
    else
        log "no import errors mentioning ha_todo"
    fi
    ;;

*)
    die "unknown HASS_FLAVOUR '$FLAVOUR' (expected: compose or haos)"
    ;;
esac

log "deployed. Add or reload it in Settings > Devices & Services."
