#!/usr/bin/env bash
# 使用当前已构建镜像启动 compose（需 .env）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "缺少 .env，请先: cp .env.example .env 并填写 DASHSCOPE_API_KEY"
  exit 1
fi

# shellcheck disable=SC1091
set -a && source .env && set +a

ACTION="${1:-up}"
case "$ACTION" in
  up)
    docker compose up -d
    echo ""
    echo "服务已启动（nginx 入口）: http://localhost:${APP_PORT:-8888}/"
    echo "健康检查: curl -s http://localhost:${APP_PORT:-8888}/api/health | head"
    ;;
  down)
    docker compose down
    ;;
  logs)
    docker compose logs -f "${2:-app}"
    ;;
  ps)
    docker compose ps
    ;;
  restart)
    docker compose restart
    ;;
  *)
    echo "用法: $0 [up|down|logs|ps|restart]"
    exit 1
    ;;
esac
