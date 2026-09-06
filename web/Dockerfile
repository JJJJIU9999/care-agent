# CareAgent 周 5：构建 Vue 前端并用 Nginx 提供静态资源与 API 代理。
# 多阶段：Node 构建产物 -> Nginx 提供 SPA（含前端路由回退与 SSE 代理）。

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
