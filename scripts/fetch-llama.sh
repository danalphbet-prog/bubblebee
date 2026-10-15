#!/usr/bin/env bash
set -euo pipefail

TAG="v0.6.0"
DEST="third_party/llama.cpp"

if [ -d "$DEST/.git" ] || [ -f "$DEST/CMakeLists.txt" ]; then
  echo "llama.cpp ja existe em $DEST"
  exit 0
fi

mkdir -p third_party
git clone --depth 1 --branch "$TAG" https://github.com/ggml-org/llama.cpp.git "$DEST"
echo "llama.cpp $TAG instalado em $DEST"
