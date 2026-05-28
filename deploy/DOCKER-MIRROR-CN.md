# Docker 构建：国内网络 / Docker Hub 超时

报错类似：

```text
failed to fetch anonymous token: Get "https://auth.docker.io/token?...": i/o timeout
```

说明本机 **拉不到 Docker Hub** 上的 `node` / `maven` / `eclipse-temurin` 基础镜像。

## 方案一（推荐）：一键国内构建脚本

不修改 Docker Desktop 全局配置，直接在 Dockerfile 里走 **DaoCloud 镜像代理**：

```bash
chmod +x scripts/docker-build-cn.sh
./scripts/docker-build-cn.sh
```

等价于设置：

- `docker.m.daocloud.io/library/node:20-alpine`
- `docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17`
- `docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy`
- npm：`registry.npmmirror.com`
- Maven：`https://maven.aliyun.com/repository/public`

或持久化配置：

```bash
cp .env.docker.example .env.docker
./scripts/docker-build.sh
```

## 方案二：配置 Docker 镜像加速（守护进程）

适合希望所有 `docker pull` 都走加速的场景。

### macOS Docker Desktop

1. 打开 **Docker Desktop → Settings → Docker Engine**
2. 在 JSON 中增加（阿里云需先在控制台获取**专属**加速地址）：

```json
{
  "registry-mirrors": [
    "https://你的ID.mirror.aliyuncs.com",
    "https://docker.m.daocloud.io"
  ]
}
```

3. **Apply & Restart**
4. 再执行 `./scripts/docker-build.sh`（使用默认 `node:20-alpine` 等官方镜像名）

阿里云加速器申请：<https://cr.console.aliyun.com/cn-hangzhou/instances/mirrors>

### Linux（含 ECS 上构建）

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://你的ID.mirror.aliyuncs.com",
    "https://docker.m.daocloud.io"
  ]
}
EOF
sudo systemctl daemon-reload
sudo systemctl restart docker
```

仓库内示例：`deploy/daemon.json.example`

## 方案三：在 ECS 上直接构建

ECS 与阿里云同区域时，配置方案二后：

```bash
git clone ... && cd 00-code-sapce-demo
./scripts/docker-build-cn.sh
cp .env.example .env && vi .env
docker compose up -d
```

无需把镜像 tar 从本机拷过去。

## 验证能否拉取基础镜像

```bash
docker pull docker.m.daocloud.io/library/node:20-alpine
```

成功后再 `./scripts/docker-build-cn.sh`。

## 若 DaoCloud 不稳定

在 `.env.docker` 中改用其它代理前缀（保持 `library/` 路径），例如：

```env
NODE_IMAGE=docker.1ms.run/library/node:20-alpine
MAVEN_IMAGE=docker.1ms.run/library/maven:3.9-eclipse-temurin-17
JRE_IMAGE=docker.1ms.run/library/eclipse-temurin:17-jre-jammy
```

然后 `./scripts/docker-build.sh`。
