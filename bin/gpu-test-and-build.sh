#!/bin/bash
# Runs the deep module's GPU-only tests (SMILE_VERIFY_CUDA_GRAPH Stage 1/2)
# against a freshly-compiled CUDA libsmile_torch.so, then — only if they
# pass — builds the production smile-serve GPU image. Collapses the
# previous two manual host-side steps (./gradlew :serve:build; docker build
# -f Dockerfile.jvm-gpu) into one command, with a real-GPU test gate in
# between (docker build alone has no GPU access, so tests can't run there —
# see Dockerfile.gpu-test's header comment).
#
# Run from the repository root:
#   bin/gpu-test-and-build.sh
#
# Requires the NVIDIA Container Toolkit (docker run --gpus works).
set -euo pipefail
cd "$(dirname "$0")/.."

TEST_TAG="smile-gpu-test"
SERVE_TAG="quarkus/smile-serve-gpu"

echo "==> [1/3] Building GPU test image (${TEST_TAG})..."
docker build -f serve/src/main/docker/Dockerfile.gpu-test -t "${TEST_TAG}" .

echo "==> [2/3] Running GPU tests (docker run --gpus all)..."
docker run --rm --gpus all "${TEST_TAG}"

echo "==> GPU tests passed. Continuing to the production image build."

echo "==> [3/3] Building serve jars and the production GPU image..."
./gradlew :serve:build
docker build -f serve/src/main/docker/Dockerfile.jvm-gpu -t "${SERVE_TAG}" .

echo "==> Done. Built ${SERVE_TAG}."
