#!/usr/bin/env bash
# 推送到阿里云 ACR（需先 docker login registry）
# 示例:
#   export ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
#   export ACR_NAMESPACE=your-namespace
#   ./scripts/docker-push.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

IMAGE_NAME="${IMAGE_NAME:-mutilmodel-demo}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

: "${ACR_REGISTRY:?请设置 ACR_REGISTRY，如 registry.cn-hangzhou.aliyuncs.com}"
: "${ACR_NAMESPACE:?请设置 ACR_NAMESPACE}"

REMOTE="${ACR_REGISTRY}/${ACR_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"
REMOTE_LATEST="${ACR_REGISTRY}/${ACR_NAMESPACE}/${IMAGE_NAME}:latest"

docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "$REMOTE"
docker tag "${IMAGE_NAME}:latest" "$REMOTE_LATEST"

echo "==> Pushing $REMOTE"
docker push "$REMOTE"
docker push "$REMOTE_LATEST"

echo "Done. ECS 上: docker pull $REMOTE"
