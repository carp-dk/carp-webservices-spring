#!/usr/bin/env bash
# Enables Cloudflare Turnstile (Phase Two magic-links.jar) on an existing realm's login and
# reset-password flows. New realms get the same setup from import/Carp-realm.json.
#
# Safe to re-run: it creates what is missing and updates the Turnstile configs, so it also rotates keys.
# The built-in "browser" and "reset credentials" flows are left untouched; to undo, bind them again.
#
# Required settings (environment variable, or the .local.env key it falls back to):
#   KEYCLOAK_URL             KC_URL, e.g. http://localhost:8090
#   KEYCLOAK_ADMIN_USER      ADMIN_USER, admin user in the master realm
#   KEYCLOAK_ADMIN_PASSWORD  ADMIN_PASSWORD
#   TURNSTILE_SITE_KEY       TURNSTILE_SITE_KEY
#   TURNSTILE_SECRET         TURNSTILE_SECRET
# Optional:
#   REALM_NAME               defaults to Carp
#   ENV_FILE                 defaults to .local.env in the repository root
#
# Environment variables win over the env file. The file is read as KEY=value lines, like
# docker compose does, and is never executed.
#
# Requires curl and jq. The password, token and secret are passed on stdin or through files,
# never as command-line arguments.

set -euo pipefail

ENV_FILE="${ENV_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.local.env}"

# Prints the last value of $1 in $ENV_FILE, without surrounding quotes.
env_file_value() {
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/"
}

if [[ -f "$ENV_FILE" ]]; then
  echo "Reading settings that are not set in the environment from $ENV_FILE"
  KEYCLOAK_URL="${KEYCLOAK_URL:-$(env_file_value KC_URL)}"
  KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-$(env_file_value ADMIN_USER)}"
  KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-$(env_file_value ADMIN_PASSWORD)}"
  TURNSTILE_SITE_KEY="${TURNSTILE_SITE_KEY:-$(env_file_value TURNSTILE_SITE_KEY)}"
  TURNSTILE_SECRET="${TURNSTILE_SECRET:-$(env_file_value TURNSTILE_SECRET)}"
fi

: "${KEYCLOAK_URL:?}" "${KEYCLOAK_ADMIN_USER:?}" "${KEYCLOAK_ADMIN_PASSWORD:?}"
: "${TURNSTILE_SITE_KEY:?}" "${TURNSTILE_SECRET:?}"
REALM_NAME="${REALM_NAME:-Carp}"
export KEYCLOAK_ADMIN_USER KEYCLOAK_ADMIN_PASSWORD TURNSTILE_SITE_KEY TURNSTILE_SECRET

LOGIN_FLOW="browser turnstile"
RESET_FLOW="reset credentials turnstile"
LOGIN_PROVIDER="ext-auth-turnstile-username-password"
RESET_PROVIDER="ext-auth-cloudflare-turnstile"
CF_HOST="https://challenges.cloudflare.com"

if [[ "$TURNSTILE_SITE_KEY" == [123]x0000000000000000000* ]]; then
  echo "Warning: TURNSTILE_SITE_KEY is a Cloudflare test key. Use it for local development only." >&2
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
AUTH_HEADER="$TMP_DIR/auth-header"

jq -jn '"username=\(env.KEYCLOAK_ADMIN_USER | @uri)&password=\(env.KEYCLOAK_ADMIN_PASSWORD | @uri)&grant_type=password&client_id=admin-cli"' |
  curl -fsS -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" --data-binary @- |
  jq -r '"Authorization: Bearer \(.access_token)"' > "$AUTH_HEADER"

ADMIN="$KEYCLOAK_URL/admin/realms/$REALM_NAME"

# api METHOD PATH [curl args...]. A JSON body, if any, is read from stdin.
api() {
  local method=$1 path=$2
  shift 2
  curl -fsS -X "$method" "$ADMIN$path" -H @"$AUTH_HEADER" -H "Content-Type: application/json" "$@"
}

uri() { jq -rn --arg s "$1" '$s | @uri'; }

executions() { api GET "/authentication/flows/$(uri "$1")/executions"; }

# Prints the id of the first execution with the given provider, or nothing.
execution_id() {
  executions "$1" | jq -r --arg provider "$2" 'first(.[] | select(.providerId == $provider) | .id) // empty'
}

copy_flow() {
  local source=$1 target=$2
  if api GET "/authentication/flows" | jq -e --arg alias "$target" 'any(.[]; .alias == $alias)' > /dev/null; then
    echo "Flow \"$target\" already exists"
  else
    jq -n --arg name "$target" '{newName: $name}' |
      api POST "/authentication/flows/$(uri "$source")/copy" --data-binary @- > /dev/null
    echo "Created flow \"$target\" from \"$source\""
  fi
}

# Prints the alias of the (sub)flow that directly contains the first execution of $provider,
# using the depth-first order and nesting levels of the executions list.
parent_flow() {
  local top_flow=$1 provider=$2
  executions "$top_flow" | jq -r --arg top "$top_flow" --arg provider "$provider" '
    reduce .[] as $e ({parents: {"-1": $top}, result: null};
      (if $e.providerId == $provider then .result //= .parents[($e.level - 1 | tostring)] else . end)
      | (if $e.authenticationFlow then .parents[($e.level | tostring)] = $e.displayName else . end))
    | .result // empty'
}

# Makes a REQUIRED $provider step the first step of the (sub)flow that holds $anchor_provider,
# and removes $anchor_provider when $replace is "replace". Each part checks the current state,
# so a re-run finishes a run that was interrupted.
add_first_step() {
  local top_flow=$1 provider=$2 anchor_provider=$3 replace=${4:-}
  local subflow
  subflow="$(parent_flow "$top_flow" "$provider")"
  [[ -n "$subflow" ]] || subflow="$(parent_flow "$top_flow" "$anchor_provider")"
  [[ -n "$subflow" ]] || { echo "Error: no \"$anchor_provider\" step in \"$top_flow\"" >&2; exit 1; }

  if [[ -z "$(execution_id "$top_flow" "$provider")" ]]; then
    jq -n --arg provider "$provider" '{provider: $provider}' |
      api POST "/authentication/flows/$(uri "$subflow")/executions/execution" --data-binary @- > /dev/null
    echo "Added \"$provider\" to \"$subflow\""
  fi
  local id
  id="$(execution_id "$top_flow" "$provider")"

  executions "$top_flow" | jq --arg id "$id" '.[] | select(.id == $id) | .requirement = "REQUIRED"' |
    api PUT "/authentication/flows/$(uri "$top_flow")/executions" --data-binary @- > /dev/null

  local replaced_id
  replaced_id="$(execution_id "$top_flow" "$anchor_provider")"
  if [[ "$replace" == "replace" && -n "$replaced_id" ]]; then
    api DELETE "/authentication/executions/$replaced_id" > /dev/null
    echo "Removed \"$anchor_provider\" from \"$subflow\""
  fi

  # New steps are added last; raise this one until it is first in its (sub)flow.
  local attempts=0
  while [[ "$(executions "$top_flow" | jq -r --arg id "$id" '.[] | select(.id == $id) | .index')" != "0" ]]; do
    ((++attempts <= 20)) || { echo "Error: could not move \"$provider\" to the top of \"$subflow\"" >&2; exit 1; }
    api POST "/authentication/executions/$id/raise-priority" > /dev/null
  done
}

# Creates or updates the config of the $provider execution in $top_flow.
configure() {
  local top_flow=$1 provider=$2 alias=$3 action=$4 config_id execution
  execution="$(executions "$top_flow" | jq -c --arg provider "$provider" 'first(.[] | select(.providerId == $provider))')"
  config_id="$(jq -r '.authenticationConfig // empty' <<< "$execution")"

  local body
  body="$(jq -n --arg alias "$alias" --arg action "$action" --arg id "$config_id" '{
    alias: $alias,
    config: {
      cloudflare_site_key: env.TURNSTILE_SITE_KEY,
      cloudflare_secret: env.TURNSTILE_SECRET,
      cloudflare_action: $action,
      cloudflare_siteverify_url: "https://challenges.cloudflare.com/turnstile/v0/siteverify"
    }
  } + (if $id == "" then {} else {id: $id} end)')"

  if [[ -z "$config_id" ]]; then
    printf '%s' "$body" |
      api POST "/authentication/executions/$(jq -r '.id' <<< "$execution")/config" --data-binary @- > /dev/null
    echo "Created config \"$alias\""
  else
    printf '%s' "$body" | api PUT "/authentication/config/$config_id" --data-binary @- > /dev/null
    echo "Updated config \"$alias\""
  fi
}

# Login: the username/password form is replaced by Phase Two's Turnstile version.
copy_flow "browser" "$LOGIN_FLOW"
add_first_step "$LOGIN_FLOW" "$LOGIN_PROVIDER" "auth-username-password-form" replace
configure "$LOGIN_FLOW" "$LOGIN_PROVIDER" "turnstile login" "login"

# Reset password: a Turnstile challenge page before the user is chosen.
copy_flow "reset credentials" "$RESET_FLOW"
add_first_step "$RESET_FLOW" "$RESET_PROVIDER" "reset-credentials-choose-user"
configure "$RESET_FLOW" "$RESET_PROVIDER" "turnstile reset password" "reset-password"

# Bind the flows, use the CARP login theme, and let the Turnstile iframe through the realm's CSP.
api GET "" | jq --arg login "$LOGIN_FLOW" --arg reset "$RESET_FLOW" --arg host "$CF_HOST" '
  .browserSecurityHeaders.contentSecurityPolicy |= ((. // "") as $csp |
    if $csp | contains($host) then $csp
    elif $csp | test("frame-src ") then $csp | sub("frame-src (?<sources>[^;]*)"; "frame-src \(.sources) \($host)")
    else "frame-src '"'"'self'"'"' \($host); \($csp)"
    end)
  | {browserFlow: $login, resetCredentialsFlow: $reset, browserSecurityHeaders,
     # The CARP theme renders the Turnstile widget, so the Phase Two "cloudflare-turnstile" theme is not needed.
     loginTheme: "carp-custom-theme"}' |
  api PUT "" --data-binary @- > /dev/null
echo "Bound \"$LOGIN_FLOW\" and \"$RESET_FLOW\", set the carp-custom-theme login theme, and allowed $CF_HOST in the CSP frame-src"
