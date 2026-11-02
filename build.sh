#!/usr/bin/env bash
set -e

# 切到脚本所在目录（确保在 g2rain-gateway-webflux 根目录执行）
cd "$(dirname "$0")"

APP_IMAGE="${APP_IMAGE:-ghcr.io/g2rain/g2rain-gateway-webflux}"

# 必须指定本地镜像 tag；发布流程从不生成 latest。
TAG="${1:?Usage: ./build.sh <tag>}"

VERSION="${TAG#v}"
JAR_FILE="target/g2rain-gateway-webflux-${VERSION}.jar"
VCS_REF="$(git rev-parse HEAD)"

echo "Building Docker image: ${APP_IMAGE}:${TAG}"
mvn -DskipTests=true clean package
docker build \
  --build-arg "JAR_FILE=${JAR_FILE}" \
  --build-arg "BUILD_VERSION=${VERSION}" \
  --build-arg "VCS_REF=${VCS_REF}" \
  --tag "${APP_IMAGE}:${TAG}" \
  .

