#!/usr/bin/env bash
# 导出 API + Web + Redis（ECS 完全离线 Docker Hub）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

API_IMAGE_NAME="${API_IMAGE_NAME:-${IMAGE_NAME:-mutilmodel-api}}"
NGINX_IMAGE_NAME="${NGINX_IMAGE_NAME:-mutilmodel-web}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
BUILD_PLATFORM="${BUILD_PLATFORM:-linux/amd64}"
OUT_DIR="${OUT_DIR:-$ROOT/dist}"
mkdir -p "$OUT_DIR"

FILE="${OUT_DIR}/mutilmodel-bundle-${IMAGE_TAG}.tar.gz"

for img in "${API_IMAGE_NAME}:${IMAGE_TAG}" "${NGINX_IMAGE_NAME}:${IMAGE_TAG}"; do
  if ! docker image inspect "$img" &>/dev/null; then
    echo "缺少 $img，请先 ./scripts/docker-build-cn.sh"
    exit 1
  fi
done

if ! docker image inspect "${REDIS_IMAGE}" &>/dev/null; then
  echo "==> pull Redis ${REDIS_IMAGE} platform=${BUILD_PLATFORM}"
  docker pull --platform "${BUILD_PLATFORM}" "${REDIS_IMAGE}"
fi

echo "==> Saving API + Web + Redis -> ${FILE}"
docker save "${API_IMAGE_NAME}:${IMAGE_TAG}" "${NGINX_IMAGE_NAME}:${IMAGE_TAG}" "${REDIS_IMAGE}" \
  | gzip > "$FILE"
ls -lh "$FILE"
