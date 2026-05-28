#!/usr/bin/env bash
# 国内网络一键构建：DaoCloud 代理 + npmmirror / 阿里云 Maven
# 默认 BUILD_PLATFORM=linux/amd64，供阿里云 ECS（x86）使用
set -euo pipefail
export USE_CN_MIRROR=1
export BUILD_PLATFORM="${BUILD_PLATFORM:-linux/amd64}"
export NODE_IMAGE="${NODE_IMAGE:-docker.m.daocloud.io/library/node:20-alpine}"
export MAVEN_IMAGE="${MAVEN_IMAGE:-docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17}"
export JRE_IMAGE="${JRE_IMAGE:-docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy}"
export NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
export MAVEN_MIRROR="${MAVEN_MIRROR:-https://maven.aliyun.com/repository/public}"
exec "$(dirname "$0")/docker-build.sh" "$@"
