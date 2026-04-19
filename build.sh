#!/usr/bin/env bash
set -e

# 切到脚本所在目录（确保在 g2rain-gateway-webflux 根目录执行）
cd "$(dirname "$0")"

APP_IMAGE="g2rain/g2rain-gateway-webflux"

# 第一个参数可选：指定 tag；不传时默认 latest
TAG="${1:-latest}"

echo "Building Docker image: ${APP_IMAGE}:${TAG}"

# 编译并使用 Jib 构建 Docker 镜像（单模块项目，直接在当前目录执行）
mvn -DskipTests=true \
  clean compile jib:dockerBuild \
  -Djib.to.image=${APP_IMAGE}:${TAG}

