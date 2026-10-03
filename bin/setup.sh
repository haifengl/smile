#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Install core math libraries required for both local and CI environments
echo "Installing OpenBLAS and ARPACK..."
sudo apt update
sudo apt install -y libopenblas-dev libarpack2

# Check if running inside a GitHub Actions runner
if [ "${GITHUB_ACTIONS}" = "true" ]; then
    echo "--------------------------------------------------------"
    echo "GitHub Actions environment detected!"
    echo "Skipping heavy CUDA installation (not supported on standard CI runners)."
    echo "--------------------------------------------------------"
else
    # Local machine installation (Installs heavy GPU/CUDA libraries)
    echo "Local environment detected. Proceeding with CUDA installation..."
    sudo apt install -y \
        cuda-toolkit-13-2 \
        libnccl2 \
        libnccl-dev \
        libcusparselt0 \
        libcudnn9-cuda-13 \
        libnvshmem3-cuda-13
fi

