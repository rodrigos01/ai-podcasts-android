#!/bin/bash
# SessionStart hook (Claude Code on the web only) - everything here needs the
# actual repo checkout, so it can't live in the cloud environment's setup
# script (that runs before the repo is cloned). This handles per-repo work:
# making sure this project's exact compileSdk platform is installed, and
# mirroring this repo's secrets (google-services.json, and anything else
# staged for it) from the shared GCS bucket.
#
# The Android SDK itself (cmdline-tools, licenses, a default platform set,
# PATH) is provisioned once per environment by the setup script - see the
# Claude Cloud environment's "Setup script" field.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

REPO_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

# --- Make sure this repo's exact compileSdk platform + a matching build-tools are installed ---
# The setup script only installs a generic default platform set (it runs
# before this repo is cloned, so it can't know what this project needs).
if [ -x "$SDKMANAGER" ] && [ -f "$REPO_DIR/app/build.gradle.kts" ]; then
  COMPILE_SDK=$(grep -oP '(?<=compileSdk = )\d+' "$REPO_DIR/app/build.gradle.kts" || true)
  if [ -n "${COMPILE_SDK:-}" ]; then
    PLATFORM="platforms;android-${COMPILE_SDK}"
    if ! "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --list_installed 2>/dev/null | grep -qF "$PLATFORM"; then
      SDK_LIST=$("$SDKMANAGER" --sdk_root="$ANDROID_HOME" --list 2>/dev/null)
      if echo "$SDK_LIST" | grep -qF "$PLATFORM"; then
        echo "Installing $PLATFORM (this repo's compileSdk, not in the environment's default set)"
        yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --install "$PLATFORM" >/dev/null 2>&1 || true
      else
        echo "WARNING: $PLATFORM isn't published in the SDK repository yet - builds needing compileSdk=$COMPILE_SDK will fail until it is (or this project lowers compileSdk)."
      fi
    fi
  fi
fi

# --- Mirror this repo's secrets from the shared GCS bucket onto the checkout ---
# The actual "list + download objects under <owner>/<repo>/" logic lives in
# scripts/sync-secrets-from-bucket.sh, shared with CI. This hook's only job
# is minting an access token: the Cloud environment has no OIDC identity of
# its own to offer, so it exchanges the shared static service-account key
# via a hand-rolled JWT-bearer flow (openssl/jq/curl, nothing else on the
# VM). CI does this the proper way - see .github/workflows/release-prod.yml,
# which authenticates via Workload Identity Federation instead and has no
# static key to manage at all.
mint_access_token() {
  [ -n "${GCP_SA_KEY_B64:-}" ] || return 1

  local sa_key client_email private_key
  sa_key=$(echo "$GCP_SA_KEY_B64" | base64 -d)
  client_email=$(echo "$sa_key" | jq -r .client_email)
  private_key=$(echo "$sa_key" | jq -r .private_key)

  local header claims signing_input signature jwt now exp
  now=$(date +%s); exp=$((now + 3600))
  header=$(printf '{"alg":"RS256","typ":"JWT"}' | base64 -w0 | tr '+/' '-_' | tr -d '=')
  claims=$(jq -nc --arg iss "$client_email" --arg aud "https://oauth2.googleapis.com/token" \
    --argjson iat "$now" --argjson exp "$exp" \
    '{iss:$iss, scope:"https://www.googleapis.com/auth/devstorage.read_only", aud:$aud, iat:$iat, exp:$exp}' \
    | base64 -w0 | tr '+/' '-_' | tr -d '=')
  signing_input="${header}.${claims}"
  signature=$(printf '%s' "$signing_input" | openssl dgst -sha256 -sign <(printf '%s' "$private_key") \
    | base64 -w0 | tr '+/' '-_' | tr -d '=')
  jwt="${signing_input}.${signature}"

  curl -fsS -X POST "https://oauth2.googleapis.com/token" \
    -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" -d "assertion=$jwt" \
    | jq -r .access_token
}

echo "==> Syncing secrets from gs://${GCS_SECRETS_BUCKET:-<unset>}/"
if [ -n "${GCS_SECRETS_BUCKET:-}" ] && GCS_ACCESS_TOKEN=$(mint_access_token) \
   && [ -n "$GCS_ACCESS_TOKEN" ] && [ "$GCS_ACCESS_TOKEN" != "null" ]; then
  GCS_SECRETS_BUCKET="$GCS_SECRETS_BUCKET" GCS_ACCESS_TOKEN="$GCS_ACCESS_TOKEN" DEST_DIR="$REPO_DIR" \
    "$REPO_DIR/scripts/sync-secrets-from-bucket.sh" \
    || echo "    nothing synced (no git remote, or nothing staged for this repo yet)"
else
  echo "    nothing synced (GCP_SA_KEY_B64/GCS_SECRETS_BUCKET unset, or token exchange failed)"
fi

GS_JSON="$REPO_DIR/app/google-services.json"
if [ ! -f "$GS_JSON" ] && [ -f "$REPO_DIR/app/build.gradle.kts" ]; then
  echo "    app/google-services.json still missing - writing a placeholder so the build still compiles"
  APP_ID=$(grep -oP '(?<=applicationId = ")[^"]+' "$REPO_DIR/app/build.gradle.kts")
  cat > "$GS_JSON" <<EOF
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "placeholder-project",
    "storage_bucket": "placeholder-project.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": { "package_name": "$APP_ID" }
      },
      "oauth_client": [],
      "api_key": [{ "current_key": "placeholder" }],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
EOF
fi
