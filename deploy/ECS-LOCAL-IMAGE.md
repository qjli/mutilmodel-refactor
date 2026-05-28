# 本地镜像 → 上传 ECS → 命令启动（完整步骤）

适用场景：**不使用阿里云 ACR**，在 Mac/Windows 本地打好镜像，用 `scp` 传到 ECS，再 `docker load` + `docker compose` 启动。

---

## 零、前置条件

| 位置 | 要求 |
|------|------|
| **本地** | 已安装 Docker Desktop；能执行 `./scripts/docker-build-cn.sh` |
| **ECS** | 已安装 Docker；建议已安装 **Docker Compose V2**（`docker compose version` 有输出） |
| **网络** | ECS 安全组放行 **TCP 8888**（或你自定义的 `APP_PORT`） |
| **密钥** | 准备好 **DashScope API Key** |

ECS 登录示例（把 IP、用户名换成你的）：

```bash
ssh root@<ECS公网IP>
```

---

## 一、本地：构建应用镜像（必须为 linux/amd64）

阿里云 ECS 一般为 **x86_64（amd64）**。在 Mac M 系列上若不加平台参数，会打出 **arm64** 镜像，ECS 上会出现：

```text
platform (linux/arm64) does not match host platform (linux/amd64)
dependency failed to start: container mutilmodel-redis is unhealthy
```

构建脚本已默认 `BUILD_PLATFORM=linux/amd64`（首次在 Mac 上构建会稍慢，属跨平台编译）。

```bash
cd /Users/qjli/03LD/00-code-sapce-demo

chmod +x scripts/*.sh

# 国内网络 + amd64（上传 ECS 用这个）
./scripts/docker-build-cn.sh

# 固定版本号
IMAGE_TAG=v1.0.0 ./scripts/docker-build-cn.sh
```

确认镜像 **架构为 amd64**（应有 api、web 两个仓库名）：

```bash
docker images | grep mutilmodel
docker image inspect mutilmodel-api:latest --format 'API {{.Architecture}}'
docker image inspect mutilmodel-web:latest --format 'WEB {{.Architecture}}'
```

---

## 二、本地：导出镜像为 tar.gz

### 2.1 只导出应用镜像（推荐先试）

```bash
# 与构建时标签一致；默认导出 latest
./scripts/docker-export.sh

# 若构建时用了固定标签：
IMAGE_TAG=v1.0.0 ./scripts/docker-export.sh
```

生成文件示例：

```text
dist/mutilmodel-latest.tar.gz
# 内含 mutilmodel-api + mutilmodel-web
```

查看大小（通常几百 MB～1GB+）：

```bash
ls -lh dist/
```

### 2.2 （可选）同时打包 Redis 镜像

ECS 若也访问不了 Docker Hub，可在本地先拉 Redis 再一并导出：

```bash
docker pull --platform linux/amd64 docker.m.daocloud.io/library/redis:7-alpine
docker tag docker.m.daocloud.io/library/redis:7-alpine redis:7-alpine

IMAGE_TAG=v1.0.0 ./scripts/docker-export-bundle.sh
# 生成 dist/mutilmodel-bundle-v1.0.0.tar.gz（应用 + redis）
```

未使用 bundle 时，ECS 需能拉取 `redis:7-alpine`（见下文「Redis 拉取失败」）。

---

## 三、本地：上传到 ECS

在**本地**执行（替换 `<ECS_IP>`、路径）：

```bash
ECS_IP=<ECS公网IP>
ECS_USER=root
REMOTE_DIR=/opt/mutilmodel

# 在 ECS 上建目录
ssh ${ECS_USER}@${ECS_IP} "mkdir -p ${REMOTE_DIR}"

# 1) 镜像包（api + web）
scp dist/mutilmodel-latest.tar.gz \
  ${ECS_USER}@${ECS_IP}:${REMOTE_DIR}/

# 若用了固定标签：
# scp dist/mutilmodel-demo-v1.0.0.tar.gz ${ECS_USER}@${ECS_IP}:${REMOTE_DIR}/

# 2) 编排与配置模板（只需传一次，有更新再传）
scp docker-compose.yml .env.example \
  ${ECS_USER}@${ECS_IP}:${REMOTE_DIR}/
```

---

## 四、ECS：加载镜像

SSH 登录 ECS 后：

```bash
cd /opt/mutilmodel

# 加载应用镜像（文件名与 scp 的一致）
gunzip -c mutilmodel-latest.tar.gz | docker load

# 若导出的是 bundle：
# gunzip -c mutilmodel-bundle-v1.0.0.tar.gz | docker load

# 确认
docker images | grep -E 'mutilmodel|redis'
```

应看到 **mutilmodel-api** 与 **mutilmodel-web** 两条记录。

**重要**：`.env` 中 `API_IMAGE_NAME`、`NGINX_IMAGE_NAME`、`IMAGE_TAG` 与 `docker images` 一致。

---

## 五、ECS：准备 Redis

`docker-compose.yml` 依赖 **Redis** 容器（默认会话存储）。

### 方式 A：ECS 拉 Redis（`registry-1.docker.io` 超时必看）

报错 `Get "https://registry-1.docker.io/v2/": ... Timeout` 表示 **ECS 访问不了 Docker Hub**。

**推荐**：在 `.env` 中指定国内镜像后直接 `compose up`：

```env
REDIS_IMAGE=docker.m.daocloud.io/library/redis:7-alpine
```

```bash
cd /opt/mutilmodel
docker compose up -d
```

**或**先手动拉取（compose 仍用默认名 `redis:7-alpine`）：

```bash
docker pull --platform linux/amd64 docker.m.daocloud.io/library/redis:7-alpine
docker tag docker.m.daocloud.io/library/redis:7-alpine redis:7-alpine
docker compose up -d
```

### 方式 B：已在本地用 bundle 导出

`docker load` 后应已有 `redis:7-alpine`，无需再 pull。

---

## 六、ECS：配置环境变量

```bash
cd /opt/mutilmodel

cp .env.example .env
vi .env   # 或 nano .env
```

**必须修改**的项：

```env
API_IMAGE_NAME=mutilmodel-api
NGINX_IMAGE_NAME=mutilmodel-web
IMAGE_TAG=latest

APP_PORT=8888

DASHSCOPE_API_KEY=sk-你的真实Key

REDIS_PASSWORD=请改为强密码
```

保存后检查（勿把 `.env` 提交到 Git）：

```bash
grep -v '^#' .env | grep -v '^$'
```

---

## 七、ECS：启动服务

```bash
cd /opt/mutilmodel

# 后台启动（app + redis）
docker compose up -d

# 查看状态
docker compose ps

# 看应用日志
docker compose logs -f app
# Ctrl+C 退出日志跟踪
```

健康检查（在 ECS 本机）：

```bash
curl -s http://127.0.0.1:8888/api/health | python3 -m json.tool
# 或无 python：curl -s http://127.0.0.1:8888/api/health
```

浏览器访问（公网，**仅 nginx 入口**）：

```text
http://<ECS公网IP>:8888/
```

页面与 `/api/*` 均经 **mutilmodel-web（nginx）**；后端 **mutilmodel-api** 不暴露宿主机端口。

---

## 八、常用运维命令（均在 /opt/mutilmodel 下）

```bash
cd /opt/mutilmodel

# 停止
docker compose down

# 重启应用（不改镜像）
docker compose restart app

# 重新拉起的配置（改过 .env 后）
docker compose up -d

# 只看 Redis 日志
docker compose logs -f redis

# 进入应用容器（排错）
docker exec -it mutilmodel-app sh

# 查看占用端口
ss -lntp | grep 8888
```

---

## 九、更新版本（改代码后重复一遍）

**本地：**

```bash
IMAGE_TAG=v1.0.1 ./scripts/docker-build-cn.sh
IMAGE_TAG=v1.0.1 ./scripts/docker-export.sh
scp dist/mutilmodel-demo-v1.0.1.tar.gz root@<ECS_IP>:/opt/mutilmodel/
```

**ECS：**

```bash
cd /opt/mutilmodel
gunzip -c mutilmodel-demo-v1.0.1.tar.gz | docker load
vi .env    # IMAGE_TAG=v1.0.1
docker compose up -d
docker compose ps
```

旧镜像可定期清理：

```bash
docker image prune -f
```

---

## 十、故障排查

| 现象 | 处理 |
|------|------|
| `docker compose` 不存在 | 安装 Compose 插件，或用 `docker-compose`（V1）替代命令 |
| `请在 .env 中设置 DASHSCOPE_API_KEY` | `.env` 未创建或未 export；确认与 `docker-compose.yml` 同目录 |
| `pull access denied` / 找不着镜像 | 未 `docker load` 或 `.env` 的 `IMAGE_NAME`/`IMAGE_TAG` 与 `docker images` 不一致 |
| 健康检查 `redis: DOWN` | `REDIS_PASSWORD` 与 redis 容器 `requirepass` 不一致；`docker compose logs redis` |
| 页面能开、对话失败 | `DASHSCOPE_API_KEY` 无效；ECS 出网是否可达 DashScope |
| 外网访问不了 8888 | 检查安全组、本机防火墙 `firewalld`/`ufw` |
| Redis 拉取超时 | 用第五节国内 `docker pull`，或使用 `docker-export-bundle.sh` |
| **arm64 / amd64 平台不匹配** | 本地用 `./scripts/docker-build-cn.sh`（默认 amd64）重新打镜像并上传；ECS 上 `docker compose down` 后删掉旧 arm64 镜像再 load |

---

## 十一、命令速查（复制用）

**本地一条龙（标签 `v1.0.0` 示例）：**

```bash
cd /Users/qjli/03LD/00-code-sapce-demo
IMAGE_TAG=v1.0.0 ./scripts/docker-build-cn.sh
IMAGE_TAG=v1.0.0 ./scripts/docker-export.sh
scp dist/mutilmodel-demo-v1.0.0.tar.gz docker-compose.yml .env.example \
  root@<ECS_IP>:/opt/mutilmodel/
```

**ECS 一条龙：**

```bash
cd /opt/mutilmodel
gunzip -c mutilmodel-demo-v1.0.0.tar.gz | docker load
docker pull docker.m.daocloud.io/library/redis:7-alpine
docker tag docker.m.daocloud.io/library/redis:7-alpine redis:7-alpine
cp -n .env.example .env && vi .env
docker compose up -d
curl -s http://127.0.0.1:8888/api/health
```

---

相关文档：[DOCKER-MIRROR-CN.md](DOCKER-MIRROR-CN.md)（本地构建超时）、[DEPLOY-ECS.md](DEPLOY-ECS.md)（含 ACR 方式）。
