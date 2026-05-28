#!/usr/bin/env bash
# 导出 API + Web 两个镜像到单个 tar.gz
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

API_IMAGE_NAME="${API_IMAGE_NAME:-${IMAGE_NAME:-mutilmodel-api}}"
NGINX_IMAGE_NAME="${NGINX_IMAGE_NAME:-mutilmodel-web}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
OUT_DIR="${OUT_DIR:-$ROOT/dist}"
mkdir -p "$OUT_DIR"

FILE="${OUT_DIR}/mutilmodel-${IMAGE_TAG}.tar.gz"

echo "==> Saving ${API_IMAGE_NAME}:${IMAGE_TAG} + ${NGINX_IMAGE_NAME}:${IMAGE_TAG}"
docker save "${API_IMAGE_NAME}:${IMAGE_TAG}" "${NGINX_IMAGE_NAME}:${IMAGE_TAG}" | gzip > "$FILE"
ls -lh "$FILE"
echo ""
echo "ECS: gunzip -c $(basename "$FILE") | docker load"
