#!/bin/bash
#
# AutoTweaker
# Copyright (C) 2026  WhiteElephant-abc
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
IMAGE_NAME="autotweaker-test:latest"

if command -v flatpak-spawn &>/dev/null && [ -n "${FLATPAK_ID:-}" ]; then
    DOCKER="flatpak-spawn --host docker"
elif command -v docker &>/dev/null; then
    DOCKER="docker"
else
    echo "错误: 找不到 docker 命令" >&2
    exit 1
fi

echo "==> Building test image: $IMAGE_NAME"
DOCKERFILE_HASH=$(md5sum "$PROJECT_DIR/Dockerfile.test" | awk '{print $1}')
CACHE_TAG="autotweaker-test:${DOCKERFILE_HASH}"

if $DOCKER image inspect "$CACHE_TAG" &>/dev/null; then
    echo "  -> Image $CACHE_TAG already exists, skipping build"
    $DOCKER tag "$CACHE_TAG" "$IMAGE_NAME"
else
    PROXY_ARGS=()
    [[ -n "${HTTP_PROXY:-}" ]] && PROXY_ARGS+=(--build-arg "http_proxy=$HTTP_PROXY")
    [[ -n "${HTTPS_PROXY:-}" ]] && PROXY_ARGS+=(--build-arg "https_proxy=$HTTPS_PROXY")

    $DOCKER build --network host \
        -t "$IMAGE_NAME" \
        --build-arg "UID=$(id -u)" \
        --build-arg "GID=$(id -g)" \
        "${PROXY_ARGS[@]}" \
        -f "$PROJECT_DIR/Dockerfile.test" "$PROJECT_DIR"
    $DOCKER tag "$IMAGE_NAME" "$CACHE_TAG"
fi

echo "==> Running tests in container..."

if [ ! -f "$HOME/.gradle-docker/.bootstrapped" ]; then
    mkdir -p "$HOME/.gradle-docker/wrapper" "$HOME/.gradle-docker/native" "$HOME/.gradle-docker/caches" "$HOME/.gradle-docker/m2"
    cp -rn "$HOME/.gradle/wrapper/dists" "$HOME/.gradle-docker/wrapper/" 2>/dev/null || true
    cp -rn "$HOME/.gradle/native/." "$HOME/.gradle-docker/native/" 2>/dev/null || true
    cp -rn "$HOME/.gradle/caches/modules-2" "$HOME/.gradle-docker/caches/" 2>/dev/null || true
    cp -rn "$HOME/.m2/repository" "$HOME/.gradle-docker/m2/" 2>/dev/null || true
    touch "$HOME/.gradle-docker/.bootstrapped"
fi

$DOCKER run --rm \
    --network host \
    -e DOCKER_TEST=true \
    -e http_proxy="${HTTP_PROXY:-}" \
    -e https_proxy="${HTTPS_PROXY:-}" \
    -v "$PROJECT_DIR:/workspace" \
    -v "$HOME/.gradle-docker:/gradle-cache" \
    -v "$HOME/.gradle-docker/m2:/home/user/.m2/repository" \
    -w /workspace \
    "$IMAGE_NAME" \
    ./gradlew --no-daemon --console=plain test -x :tool-decl:generateArgs "$@" 2>&1 | sed -u 's/^/[docker] /'
