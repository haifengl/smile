#!/bin/bash
# Runs the deep module's full GPU test suite (./gradlew :deep:test — every
# CPU-only test plus every GPU-only test, including the SMILE_VERIFY_CUDA_GRAPH
# Stage 1/2/4/5 tests) against a freshly-compiled CUDA libsmile_torch.so,
# then — only if they pass — builds the production smile-serve GPU image.
# Collapses the previous two manual host-side steps (./gradlew :serve:build;
# docker build -f Dockerfile.jvm-gpu) into one command, with a real-GPU test
# gate in between (docker build alone has no GPU access, so tests can't run
# there — see Dockerfile.gpu-test's header comment).
#
# Test results (JUnit XML + HTML reports) are copied to $MODEL_DIR (default
# /model — the same path production containers mount, e.g. for
# -XX:ErrorFile=/model/hs_err_%p.log) so they survive the --rm'd container
# and are inspectable afterward without re-running anything: this is the only
# reliable way to tell a genuinely PASSED test apart from one that was
# SKIPPED (e.g. an assumeTrue(...) guard silently short-circuiting — Gradle's
# own console summary does not distinguish these two outcomes as clearly as
# the XML's <testsuite skipped="N"> / per-test <skipped/> elements do).
#
# Run from the repository root:
#   bin/gpu-test-and-build.sh
#   MODEL_DIR=/path/to/host/model bin/gpu-test-and-build.sh   # custom mount source
#
# Requires the NVIDIA Container Toolkit (docker run --gpus works).
set -euo pipefail
cd "$(dirname "$0")/.."

TEST_TAG="smile-gpu-test"
SERVE_TAG="quarkus/smile-serve-gpu"
MODEL_DIR="${MODEL_DIR:-/model}"

echo "==> [1/3] Building GPU test image (${TEST_TAG})..."
docker build -f serve/src/main/docker/Dockerfile.gpu-test -t "${TEST_TAG}" .

echo "==> [2/3] Running GPU tests (docker run --gpus all)..."
mkdir -p "${MODEL_DIR}/gpu-test-results"
docker run --rm --gpus all \
    -v "${MODEL_DIR}:/model" \
    "${TEST_TAG}" \
    bash -c '
        set +e
        ./gradlew :deep:test --no-daemon --rerun
        code=$?
        echo "==> Copying test results to /model/gpu-test-results (exit code ${code})..."
        mkdir -p /model/gpu-test-results
        rm -rf /model/gpu-test-results/xml /model/gpu-test-results/html
        cp -r deep/build/test-results/test /model/gpu-test-results/xml
        cp -r deep/build/reports/tests/test /model/gpu-test-results/html
        exit "${code}"
    '

echo "==> GPU tests passed (results in ${MODEL_DIR}/gpu-test-results). Continuing to the production image build."

echo "==> [3/3] Building serve jars and the production GPU image..."
./gradlew :serve:build
docker build -f serve/src/main/docker/Dockerfile.jvm-gpu -t "${SERVE_TAG}" .

echo "==> Done. Built ${SERVE_TAG}."
