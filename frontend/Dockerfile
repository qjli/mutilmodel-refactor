# 前端静态资源 + Nginx（对外入口，反代 /api → app:8888）
ARG NODE_IMAGE=node:20-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine
ARG NPM_REGISTRY=https://registry.npmjs.org

FROM ${NODE_IMAGE} AS frontend-build
ARG NPM_REGISTRY
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm config set registry "${NPM_REGISTRY}" && npm ci
COPY . .
RUN npm run build:docker

FROM ${NGINX_IMAGE}
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /build/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1/api/health >/dev/null || exit 1
