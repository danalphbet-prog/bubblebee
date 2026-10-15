#!/usr/bin/env bash
set -euo pipefail

TAG="v0.6.0"
DEST="third_party/llama.cpp"

if [ -f "$DEST/CMakeLists.txt" ] && [ -f "$DEST/examples/llama.android/lib/build.gradle.kts" ]; then
  echo "llama.cpp ja existe em $DEST"
  exit 0
fi

echo "Preparando llama.cpp $TAG..."
rm -rf "$DEST"
mkdir -p third_party

git clone \
  --depth 1 \
  --branch "$TAG" \
  --single-branch \
  https://github.com/ggml-org/llama.cpp.git \
  "$DEST"

test -f "$DEST/CMakeLists.txt"
test -f "$DEST/examples/llama.android/lib/build.gradle.kts"

echo "llama.cpp $TAG instalado em $DEST"
