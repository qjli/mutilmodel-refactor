#!/usr/bin/env bash
# 在 ECS（或任何拉不动 Docker Hub 的机器）上预拉 Redis amd64 镜像
set -euo pipefail

PLATFORM="${BUILD_PLATFORM:-linux/amd64}"
SRC="${REDIS_IMAGE_SRC:-docker.m.daocloud.io/library/redis:7-alpine}"
TAG="${REDIS_IMAGE_TAG:-redis:7-alpine}"

echo "==> pull --platform ${PLATFORM} ${SRC}"
docker pull --platform "${PLATFORM}" "${SRC}"
docker tag "${SRC}" "${TAG}"
docker image inspect "${TAG}" --format 'OK {{.RepoTags}} Architecture={{.Architecture}}'
