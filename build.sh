#!/usr/bin/env bash
set -euo pipefail

# 切到脚本所在目录（确保在 g2rain-gateway-webflux 根目录执行）
cd "$(dirname "$0")"

APP_IMAGE="${APP_IMAGE:-ghcr.io/g2rain/g2rain-gateway-webflux}"

# 第一个参数可选；默认使用 Maven 项目版本。EXTRA_TAGS 可追加额外标签（逗号分隔）。
PROJECT_VERSION="$(mvn --batch-mode --no-transfer-progress -q -DforceStdout help:evaluate -Dexpression=project.version | tail -n 1)"
TAG="${1:-$PROJECT_VERSION}"

if [[ "$TAG" != "$PROJECT_VERSION" ]]; then
  echo "Image tag '$TAG' must equal Maven project version '$PROJECT_VERSION'." >&2
  exit 1
fi

JAR_FILE="target/g2rain-gateway-webflux-${PROJECT_VERSION}.jar"
VCS_REF="$(git rev-parse HEAD)"

TAG_ARGS=(--tag "${APP_IMAGE}:${TAG}")
if [[ -n "${EXTRA_TAGS:-}" ]]; then
  IFS=',' read -ra extra_tags <<<"$EXTRA_TAGS"
  for extra_tag in "${extra_tags[@]}"; do
    TAG_ARGS+=(--tag "${APP_IMAGE}:${extra_tag}")
  done
fi

echo "Building Docker image: ${APP_IMAGE}:${TAG}"
mvn --batch-mode --no-transfer-progress -DskipTests=true clean package
docker build \
  --build-arg "JAR_FILE=${JAR_FILE}" \
  --build-arg "BUILD_VERSION=${PROJECT_VERSION}" \
  --build-arg "VCS_REF=${VCS_REF}" \
  "${TAG_ARGS[@]}" \
  .
