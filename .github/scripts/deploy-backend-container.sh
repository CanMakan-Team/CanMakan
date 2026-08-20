#!/usr/bin/env bash
# Blue/green container swap on the EC2 host. Intended to be run via appleboy/ssh-action
# with GitHub Environment secrets forwarded as env vars (see deploy.yml).
set -euo pipefail

if [ -z "${BACKEND_IMAGE:-}" ] && [ -z "${ML_IMAGE:-}" ]; then
  echo "ERROR: BACKEND_IMAGE or ML_IMAGE must be set"
  exit 1
fi
if [ -z "${GHCR_TOKEN:-}" ]; then
  echo "ERROR: GHCR_TOKEN is not set"
  exit 1
fi

DOCKER_NETWORK="${DOCKER_NETWORK:-canmakan}"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

sudo apt-get update > /dev/null
sudo apt-get install -y lsof curl ca-certificates > /dev/null

if ! command -v docker >/dev/null 2>&1; then
  echo "Installing Docker Engine..."
  sudo apt-get install -y docker.io > /dev/null
  sudo systemctl enable --now docker
fi

mkdir -p /home/ubuntu/app
umask 077
ENV_FILE=/home/ubuntu/app/canmakan.env
: > "$ENV_FILE"
append_env() {
  local key="$1"
  local val=""
  eval "val=\"\${${key}-}\""
  printf '%s=%s\n' "$key" "$val" >> "$ENV_FILE"
}

append_env SPRING_PROFILES_ACTIVE
append_env CANMAKAN_CORS_ALLOWED_ORIGINS
append_env CANMAKAN_CORS_ALLOWED_HEADERS
append_env CANMAKAN_CORS_ALLOWED_METHODS
append_env CANMAKAN_CORS_ALLOWED_ORIGIN_PATTERNS
append_env CANMAKAN_CORS_ALLOW_CREDENTIALS
append_env CANMAKAN_CORS_MAX_AGE_SECONDS
append_env CANMAKAN_INVITES_PUBLIC_BASE_URL
append_env FIREBASE_APP_DISTRIBUTION_URL
append_env CANMAKAN_INVITES_EXPIRY_DAYS
append_env CANMAKAN_EMAIL_RESEND_ENABLED
append_env CANMAKAN_EMAIL_RESEND_API_KEY
append_env CANMAKAN_EMAIL_RESEND_FROM
append_env CANMAKAN_EMAIL_RESEND_API_URL
append_env MYSQL_HOST
append_env MYSQL_PORT
append_env MYSQL_DB
append_env MYSQL_USERNAME
append_env MYSQL_PASSWORD
append_env JWT_ISSUER
append_env JWT_ACCESS_TTL
append_env JWT_SIGNING_SECRET
append_env REFRESH_TOKEN_TTL
append_env REFRESH_COOKIE_NAME
append_env REFRESH_COOKIE_SECURE
append_env REFRESH_COOKIE_SAME_SITE
append_env OPEN_FOOD_FACTS_BASE_URL
append_env EAN_SEARCH_BASE_URL
append_env EAN_SEARCH_API_KEY
append_env PRODUCT_API_CONNECT_TIMEOUT_MS
append_env PRODUCT_API_RESPONSE_TIMEOUT_MS
append_env PRODUCT_API_RETRY_MAX_ATTEMPTS
append_env PRODUCT_API_RETRY_BACKOFF_MS
append_env OPENAI_BASE_URL
append_env OPENAI_API_KEY
append_env OPENAI_MODEL
append_env CANMAKAN_AI_ENABLED
append_env CANMAKAN_AI_TIMEOUT_MS
append_env CANMAKAN_AI_RETRY_MAX_ATTEMPTS
append_env CANMAKAN_AI_RETRY_BACKOFF_MS
append_env CANMAKAN_AI_AGENT_MAX_TOOL_ITERATIONS
append_env CANMAKAN_AI_AUDIT_ENABLED
append_env CANMAKAN_AI_AUDIT_STORE_PROMPT
append_env CANMAKAN_AI_AUDIT_STORE_RESPONSE
append_env TAVILY_API_KEY
append_env TAVILY_URL
append_env CANMAKAN_RECOMMENDATION_ML_ENABLED
append_env CANMAKAN_RECOMMENDATION_ML_ARTIFACT_PATH
append_env CANMAKAN_RECOMMENDATION_LLM_ENABLED
chmod 600 "$ENV_FILE"

if ! sudo docker network inspect "${DOCKER_NETWORK}" >/dev/null 2>&1; then
  sudo docker network create "${DOCKER_NETWORK}"
fi

echo "${GHCR_TOKEN}" | sudo docker login ghcr.io -u "${GHCR_USER:-github-actions}" --password-stdin

deploy_ml() {
  local image="$1"
  echo "Deploying ML ranker ${image} as canmakan-ml"
  sudo docker pull "${image}"
  sudo docker rm -f canmakan-ml-candidate >/dev/null 2>&1 || true
  sudo docker run -d \
    --name canmakan-ml-candidate \
    --network "${DOCKER_NETWORK}" \
    --log-opt max-size=50m \
    --log-opt max-file=7 \
    -p "127.0.0.1:8092:8091" \
    "${image}"

  echo "Polling ML candidate health check on port 8092..."
  local timeout=60
  while [ "$timeout" -gt 0 ]; do
    if curl -fsS "http://127.0.0.1:8092/health" | grep '"status":"ok"' > /dev/null; then
      echo "ML candidate is UP."
      sudo docker rm -f canmakan-ml >/dev/null 2>&1 || true
      sudo docker rm -f canmakan-ml-candidate >/dev/null 2>&1 || true
      sudo docker run -d \
        --name canmakan-ml \
        --network "${DOCKER_NETWORK}" \
        --restart unless-stopped \
        --log-opt max-size=50m \
        --log-opt max-file=7 \
        -p "127.0.0.1:8091:8091" \
        "${image}"
      local settle=30
      while [ "$settle" -gt 0 ]; do
        if curl -fsS "http://127.0.0.1:8091/health" | grep '"status":"ok"' > /dev/null; then
          echo "ML ranker is UP."
          return 0
        fi
        sleep 2
        settle=$((settle - 2))
      done
      echo "ERROR: ML ranker failed after swap."
      sudo docker logs canmakan-ml || true
      return 1
    fi
    sleep 3
    timeout=$((timeout - 3))
  done
  echo "ERROR: ML ranker failed health check."
  sudo docker logs canmakan-ml-candidate || true
  sudo docker rm -f canmakan-ml-candidate || true
  return 1
}

if [ -n "${ML_IMAGE:-}" ]; then
  if ! deploy_ml "${ML_IMAGE}"; then
    if [ -n "${ML_IMAGE_FALLBACK:-}" ] && [ "${ML_IMAGE_FALLBACK}" != "${ML_IMAGE}" ]; then
      echo "SHA tag missing; trying branch tag ${ML_IMAGE_FALLBACK}"
      deploy_ml "${ML_IMAGE_FALLBACK}"
    else
      exit 1
    fi
  fi
elif [ -n "${ML_IMAGE_FALLBACK:-}" ]; then
  echo "No ML image for this SHA; pulling branch tag if it exists."
  deploy_ml "${ML_IMAGE_FALLBACK}" || echo "No published ML image yet; Spring will use the Java ranker fallback."
fi

if sudo docker ps --format '{{.Names}}' | grep -qx canmakan-ml; then
  printf 'CANMAKAN_RECOMMENDATION_ML_RANKER_URL=http://canmakan-ml:8091\n' >> "$ENV_FILE"
  echo "Pointing Spring at http://canmakan-ml:8091"
fi

if [ -z "${BACKEND_IMAGE:-}" ]; then
  ACTIVE_HINT=$(cat /home/ubuntu/app/active_port.txt 2>/dev/null || echo "8080")
  RUNNING="canmakan-backend-${ACTIVE_HINT}"
  if sudo docker ps --format '{{.Names}}' | grep -qx "${RUNNING}"; then
    BACKEND_IMAGE="$(sudo docker inspect -f '{{.Config.Image}}' "${RUNNING}")"
    echo "Recycling running backend ${BACKEND_IMAGE} so it can reach canmakan-ml."
  else
    echo "ML deployed; no backend container to recycle. Ranker URL is applied on the next backend deploy."
    sudo docker image prune -f >/dev/null || true
    exit 0
  fi
fi

sudo docker pull "${BACKEND_IMAGE}"

ACTIVE_PORT=$(cat /home/ubuntu/app/active_port.txt 2>/dev/null || echo "8081")
if [ "$ACTIVE_PORT" = "8080" ]; then
  NEW_PORT="8081"
else
  NEW_PORT="8080"
fi
NEW_NAME="canmakan-backend-${NEW_PORT}"
OLD_NAME="canmakan-backend-${ACTIVE_PORT}"

echo "Deploying ${BACKEND_IMAGE} as ${NEW_NAME} (SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE})"

sudo docker rm -f "${NEW_NAME}" >/dev/null 2>&1 || true

# First-time migration: stop a host-process JAR still bound to the target port.
if ! sudo docker ps --format '{{.Names}}' | grep -qx "${NEW_NAME}"; then
  ORPHAN_PID=$(sudo lsof -t -i:"${NEW_PORT}" || true)
  if [ -n "${ORPHAN_PID}" ]; then
    echo "Stopping non-container process on port ${NEW_PORT}..."
    sudo kill -9 ${ORPHAN_PID} || true
    sleep 2
  fi
fi

sudo docker run -d \
  --name "${NEW_NAME}" \
  --network "${DOCKER_NETWORK}" \
  --restart unless-stopped \
  --log-opt max-size=50m \
  --log-opt max-file=7 \
  -p "127.0.0.1:${NEW_PORT}:8080" \
  -p "127.0.0.1:8082:8082" \
  --env-file "${ENV_FILE}" \
  -e SERVER_ADDRESS=0.0.0.0 \
  -e SERVER_PORT=8080 \
  -e MANAGEMENT_SERVER_PORT=8082 \
  -e MANAGEMENT_SERVER_ADDRESS=127.0.0.1 \
  -e MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health \
  -e MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS=never \
  "${BACKEND_IMAGE}"

echo "Polling health check on port ${NEW_PORT}..."
TIMEOUT=90
while [ "$TIMEOUT" -gt 0 ]; do
  if curl -fsS "http://127.0.0.1:${NEW_PORT}/actuator/health" | grep '"status":"UP"' > /dev/null; then
    echo "Instance is UP."
    break
  fi
  sleep 5
  TIMEOUT=$((TIMEOUT - 5))
done

if [ "$TIMEOUT" -le 0 ]; then
  echo "ERROR: Instance failed health check. Rolling back."
  sudo docker logs "${NEW_NAME}" || true
  sudo docker rm -f "${NEW_NAME}" || true
  exit 1
fi

echo "Swapping Nginx to port ${NEW_PORT}..."
echo "upstream backend_pool { server 127.0.0.1:${NEW_PORT}; }" | sudo tee /etc/nginx/conf.d/canmakan-upstream.conf
sudo systemctl reload nginx

if sudo docker ps -a --format '{{.Names}}' | grep -qx "${OLD_NAME}"; then
  sudo docker stop "${OLD_NAME}" || true
  sudo docker rm "${OLD_NAME}" || true
else
  OLD_PID=$(sudo lsof -t -i:"${ACTIVE_PORT}" || true)
  if [ -n "${OLD_PID}" ]; then
    sudo kill -15 ${OLD_PID} || true
  fi
fi

echo "${NEW_PORT}" > /home/ubuntu/app/active_port.txt
find /home/ubuntu/app -maxdepth 1 -name "canmakan-backend-*.jar" -delete || true
rm -rf /home/ubuntu/app/deploy-temp || true
sudo docker image prune -f >/dev/null || true
