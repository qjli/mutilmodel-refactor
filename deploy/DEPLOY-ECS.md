# 阿里云 ECS Docker 部署指南

本项目采用 **单镜像**（Spring Boot 内嵌前端静态资源）+ **Redis** 编排。本地用脚本构建镜像，ECS 上 `docker compose` 启动。

## 一、架构

| 容器 | 说明 | 端口 |
|------|------|------|
| `mutilmodel-app` | Java 17，API + 前端静态页 | 宿主机 `8888` → 容器 `8888` |
| `mutilmodel-redis` | 会话与材料覆盖（默认 `store=redis`） | 仅内网 |

浏览器访问：`http://<ECS公网IP>:8888/`

## 二、本地：重复构建镜像

> **Docker Hub 超时**（`auth.docker.io` / `i/o timeout`）请改用国内构建：  
> **[deploy/DOCKER-MIRROR-CN.md](DOCKER-MIRROR-CN.md)**，或执行 `./scripts/docker-build-cn.sh`

```bash
cd /path/to/00-code-sapce-demo

# 国内网络推荐
chmod +x scripts/*.sh
./scripts/docker-build-cn.sh

# 或已配置 Docker 镜像加速时
./scripts/docker-build.sh

# 自定义名称/标签
IMAGE_NAME=mutilmodel-demo IMAGE_TAG=v1.0.0 ./scripts/docker-build.sh

# 无缓存重建
NO_CACHE=1 ./scripts/docker-build.sh
```

本地试跑（需 Docker Compose v2）：

```bash
cp .env.example .env
# 编辑 .env，填写 DASHSCOPE_API_KEY
./scripts/docker-up.sh          # 启动
./scripts/docker-up.sh logs     # 看日志
./scripts/docker-up.sh down     # 停止
```

## 三、把镜像弄到 ECS

### 方式 A：导出 tar（无需镜像仓库）

**本地：**

```bash
IMAGE_TAG=v1.0.0 ./scripts/docker-build.sh
IMAGE_TAG=v1.0.0 ./scripts/docker-export.sh
# 生成 dist/mutilmodel-demo-v1.0.0.tar.gz
scp dist/mutilmodel-demo-v1.0.0.tar.gz root@<ECS_IP>:/opt/mutilmodel/
```

**ECS：**

```bash
mkdir -p /opt/mutilmodel && cd /opt/mutilmodel
gunzip -c mutilmodel-demo-v1.0.0.tar.gz | docker load
docker images | grep mutilmodel
```

### 方式 B：阿里云 ACR（推荐生产）

**本地：**

```bash
docker login registry.cn-hangzhou.aliyuncs.com
export ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
export ACR_NAMESPACE=你的命名空间
IMAGE_TAG=v1.0.0 ./scripts/docker-build.sh
IMAGE_TAG=v1.0.0 ./scripts/docker-push.sh
```

**ECS：**

```bash
docker login registry.cn-hangzhou.aliyuncs.com
docker pull registry.cn-hangzhou.aliyuncs.com/你的命名空间/mutilmodel-demo:v1.0.0
docker tag registry.cn-hangzhou.aliyuncs.com/你的命名空间/mutilmodel-demo:v1.0.0 mutilmodel-demo:v1.0.0
```

## 四、ECS 上首次部署

### 1. 安全组

放行入站：**TCP 8888**（或你自定义的 `APP_PORT`）。Redis **不要**对公网开放。

### 2. 上传编排文件

将仓库中以下文件放到 ECS（例如 `/opt/mutilmodel/`）：

- `docker-compose.yml`
- `.env`（由 `.env.example` 复制并填写）

```bash
cd /opt/mutilmodel
cp .env.example .env
vi .env   # 必填 DASHSCOPE_API_KEY；IMAGE_TAG 与 load/pull 的 tag 一致
```

`.env` 示例：

```env
IMAGE_NAME=mutilmodel-demo
IMAGE_TAG=v1.0.0
APP_PORT=8888
DASHSCOPE_API_KEY=sk-...
REDIS_PASSWORD=请改为强密码
```

### 3. 启动

```bash
cd /opt/mutilmodel
docker compose up -d
docker compose ps
curl -s http://127.0.0.1:8888/api/health
```

### 4. 日常运维

```bash
# 查看日志
docker compose logs -f app

# 更新版本（先 load/pull 新镜像，改 .env 中 IMAGE_TAG）
docker compose pull   # 若使用 ACR 且 compose 中 image 为完整仓库地址时可 pull
docker compose up -d

# 停止
docker compose down

# 仅重启应用
docker compose restart app
```

## 五、无 Redis 的极简模式（不推荐生产）

若 ECS 暂不部署 Redis，可在 `docker-compose.yml` 中注释 `redis` 服务，并给 `app` 增加：

```yaml
environment:
  AGENTSCOPE_SESSION_STORE: file
  AGENTSCOPE_SESSION_ROOT: /data/sessions
volumes:
  - app-sessions:/data/sessions
```

同时去掉对 `redis` 的 `depends_on`。默认配置以 Redis 为准。

## 六、环境变量说明

| 变量 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` | 必填，DashScope API Key |
| `REDIS_PASSWORD` | Redis 密码，需与 `spring.data.redis.password` 一致 |
| `APP_PORT` | 宿主机映射端口 |
| `IMAGE_NAME` / `IMAGE_TAG` | compose 使用的镜像 |
| `JAVA_TOOL_OPTIONS` | 默认关闭视觉 thinking，避免部分模型 400 |

## 七、常见问题

1. **健康检查 DEGRADED / redis DOWN**  
   检查 `REDIS_PASSWORD` 是否与 redis 容器 `requirepass` 一致。

2. **页面能开、对话失败**  
   检查 `DASHSCOPE_API_KEY` 是否有效、ECS 能否访问外网（调用 DashScope）。

3. **构建很慢**  
   首次会拉取 Node/Maven 基础镜像；之后 Docker 层缓存会加快 `docker-build.sh`。

4. **仅更新后端逻辑**  
   重新 `./scripts/docker-build.sh` 并重复「导出或推送 + ECS compose up -d」即可，前端已打进同一镜像。
