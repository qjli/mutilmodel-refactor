#!/usr/bin/env bash
# 构建后端 API 镜像 + 前端 Nginx 镜像（分离部署）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/.env.docker" ]]; then
  # shellcheck disable=SC1091
  set -a && source "$ROOT/.env.docker" && set +a
fi

if [[ "${USE_CN_MIRROR:-}" == "1" ]]; then
  export NODE_IMAGE="${NODE_IMAGE:-docker.m.daocloud.io/library/node:20-alpine}"
  export MAVEN_IMAGE="${MAVEN_IMAGE:-docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17}"
  export JRE_IMAGE="${JRE_IMAGE:-docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy}"
  export NGINX_IMAGE="${NGINX_IMAGE:-docker.m.daocloud.io/library/nginx:1.27-alpine}"
  export NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
  export MAVEN_MIRROR="${MAVEN_MIRROR:-https://maven.aliyun.com/repository/public}"
fi

NODE_IMAGE="${NODE_IMAGE:-node:20-alpine}"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-17}"
JRE_IMAGE="${JRE_IMAGE:-eclipse-temurin:17-jre-jammy}"
NGINX_IMAGE="${NGINX_IMAGE:-nginx:1.27-alpine}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmjs.org}"
MAVEN_MIRROR="${MAVEN_MIRROR:-}"
BUILD_PLATFORM="${BUILD_PLATFORM:-linux/amd64}"

API_IMAGE_NAME="${API_IMAGE_NAME:-${IMAGE_NAME:-mutilmodel-api}}"
NGINX_IMAGE_NAME="${NGINX_IMAGE_NAME:-mutilmodel-web}"

if [[ -z "${IMAGE_TAG:-}" ]]; then
  if git rev-parse --is-inside-work-tree &>/dev/null; then
    IMAGE_TAG="$(git rev-parse --short HEAD)"
  else
    IMAGE_TAG="$(date +%Y%m%d%H%M)"
  fi
fi

API_ARGS=(
  --build-arg "MAVEN_IMAGE=${MAVEN_IMAGE}"
  --build-arg "JRE_IMAGE=${JRE_IMAGE}"
  --build-arg "MAVEN_MIRROR=${MAVEN_MIRROR}"
)
WEB_ARGS=(
  --build-arg "NODE_IMAGE=${NODE_IMAGE}"
  --build-arg "NGINX_IMAGE=${NGINX_IMAGE}"
  --build-arg "NPM_REGISTRY=${NPM_REGISTRY}"
)

echo "==> [1/2] API  ${API_IMAGE_NAME}:${IMAGE_TAG}  platform=${BUILD_PLATFORM}"
if [[ -n "${NO_CACHE:-}" ]]; then
  docker build --platform "${BUILD_PLATFORM}" --no-cache "${API_ARGS[@]}" \
    -t "${API_IMAGE_NAME}:${IMAGE_TAG}" -t "${API_IMAGE_NAME}:latest" \
    -f Dockerfile.backend "$ROOT"
else
  docker build --platform "${BUILD_PLATFORM}" "${API_ARGS[@]}" \
    -t "${API_IMAGE_NAME}:${IMAGE_TAG}" -t "${API_IMAGE_NAME}:latest" \
    -f Dockerfile.backend "$ROOT"
fi

echo "==> [2/2] Web  ${NGINX_IMAGE_NAME}:${IMAGE_TAG}  platform=${BUILD_PLATFORM}"
if [[ -n "${NO_CACHE:-}" ]]; then
  docker build --platform "${BUILD_PLATFORM}" --no-cache "${WEB_ARGS[@]}" \
    -t "${NGINX_IMAGE_NAME}:${IMAGE_TAG}" -t "${NGINX_IMAGE_NAME}:latest" \
    -f frontend/Dockerfile "$ROOT/frontend"
else
  docker build --platform "${BUILD_PLATFORM}" "${WEB_ARGS[@]}" \
    -t "${NGINX_IMAGE_NAME}:${IMAGE_TAG}" -t "${NGINX_IMAGE_NAME}:latest" \
    -f frontend/Dockerfile "$ROOT/frontend"
fi

echo ""
docker image inspect "${API_IMAGE_NAME}:${IMAGE_TAG}" --format 'API  {{.Architecture}} {{.Os}}'
docker image inspect "${NGINX_IMAGE_NAME}:${IMAGE_TAG}" --format 'Web  {{.Architecture}} {{.Os}}'
echo ""
echo "Done."
echo "  ${API_IMAGE_NAME}:${IMAGE_TAG}"
echo "  ${NGINX_IMAGE_NAME}:${IMAGE_TAG}"
echo "  Test: cp .env.example .env && ./scripts/docker-up.sh"
echo "  Export: ./scripts/docker-export.sh"
