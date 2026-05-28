# Docker 部署架构（Nginx + API 分离）

## 拓扑

```text
                    公网 :8888
                        │
                        ▼
              ┌─────────────────┐
              │  mutilmodel-web │  nginx:1.27-alpine
              │  (静态 + 反代)   │  :80 ← 映射宿主机 8888
              └────────┬────────┘
                       │ /api/*  → http://app:8888/api/*
                       ▼
              ┌─────────────────┐
              │  mutilmodel-api │  Spring Boot JAR（无前端 static）
              │  仅内网 expose   │
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │ mutilmodel-redis│
              └─────────────────┘
```

- **对外只开放** `APP_PORT`（默认 **8888**）→ nginx 容器 80 端口。
- **app** 不映射宿主机端口，仅 Docker 网络内可达。
- 浏览器访问 `/` 为 React 静态页；`/api/*` 由 nginx 转发到后端。
- 若 API 返回 `Invalid CORS request`：后端 `app.cors.allowed-origin-patterns` 默认已含 `*`；可按需在 `.env` 设置 `APP_CORS_ALLOWED_ORIGIN_PATTERNS`。

## 镜像

| 镜像名 | Dockerfile | 内容 |
|--------|------------|------|
| `mutilmodel-api:<tag>` | `Dockerfile.backend` | Java 17 + `app.jar` |
| `mutilmodel-web:<tag>` | `frontend/Dockerfile` | Vite `dist` + nginx |

## 构建与启动

```bash
./scripts/docker-build-cn.sh
cp .env.example .env
./scripts/docker-up.sh
# http://localhost:8888/
```

导出到 ECS：

```bash
./scripts/docker-export.sh
# dist/mutilmodel-<tag>.tar.gz 内含 api + web 两个镜像
```

## 与旧方案差异

| 旧方案 | 新方案 |
|--------|--------|
| 单镜像 `mutilmodel-demo`，JAR 内嵌 static | `mutilmodel-api` 纯 API |
| app 容器直接映射 8888 | **nginx** 映射 8888，反代 API |
| 前端在 Maven/npm 打进 JAR | 前端只在 **web 镜像** |

本地 `mvn -Pfrontend` / `mvn spring-boot:run` 仍可把 static 打进 JAR 用于开发；**Docker 生产路径不再依赖 JAR 内 static**。
