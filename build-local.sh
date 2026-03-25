#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# build-local.sh — Build the debug APK inside a container, copy it out.
#
# Mounts the host Gradle cache so dependencies are only downloaded once
# across all builds (~30s rebuilds vs ~3min cold builds).
#
# Usage:
#   ./build-local.sh           # build APK only
#   ./build-local.sh test      # run unit tests only
#   ./build-local.sh all       # lint + test + build APK
# ---------------------------------------------------------------------------
set -euo pipefail

IMAGE="carb-calculator-build"
APK_SRC="/app/app/build/outputs/apk/debug/app-debug.apk"
APK_DST="./app-debug.apk"
GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Use a named Docker volume for the Gradle cache so it persists across builds
# without permission conflicts (the volume is always owned by root inside the container).
GRADLE_CACHE_MOUNTS=(
  -v "carb-calculator-gradle-cache:/root/.gradle"
)

MODE="${1:-build}"

echo "==> Building image..."
docker build -f Dockerfile.build -t "$IMAGE" .

case "$MODE" in
  test)
    echo "==> Running unit tests..."
    docker run --rm \
      -e GRADLE_OPTS="$GRADLE_OPTS" \
      "${GRADLE_CACHE_MOUNTS[@]}" \
      "$IMAGE" \
      ./gradlew test --no-daemon
    ;;
  all)
    echo "==> Running lint + tests + assembleDebug..."
    CONTAINER_ID=$(docker run -d \
      -e GRADLE_OPTS="$GRADLE_OPTS" \
      "${GRADLE_CACHE_MOUNTS[@]}" \
      "$IMAGE" \
      sh -c "./gradlew lint test assembleDebug --no-daemon")

    docker logs -f "$CONTAINER_ID"
    EXIT_CODE=$(docker wait "$CONTAINER_ID")

    if [ "$EXIT_CODE" -ne 0 ]; then
      echo "Build failed (exit $EXIT_CODE)"
      docker rm "$CONTAINER_ID"
      exit 1
    fi

    echo "==> Copying APK out..."
    docker cp "$CONTAINER_ID:$APK_SRC" "$APK_DST"
    docker rm "$CONTAINER_ID"
    echo "==> APK written to $APK_DST"
    ;;
  build|*)
    echo "==> Assembling debug APK..."
    CONTAINER_ID=$(docker run -d \
      -e GRADLE_OPTS="$GRADLE_OPTS" \
      "${GRADLE_CACHE_MOUNTS[@]}" \
      "$IMAGE" \
      ./gradlew assembleDebug --no-daemon)

    docker logs -f "$CONTAINER_ID"
    EXIT_CODE=$(docker wait "$CONTAINER_ID")

    if [ "$EXIT_CODE" -ne 0 ]; then
      echo "Build failed (exit $EXIT_CODE)"
      docker rm "$CONTAINER_ID"
      exit 1
    fi

    echo "==> Copying APK out..."
    docker cp "$CONTAINER_ID:$APK_SRC" "$APK_DST"
    docker rm "$CONTAINER_ID"
    echo "==> APK written to $APK_DST"
    ;;
esac
