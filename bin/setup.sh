#!/bin/bash

# Install OpenBLAS and ARPACK
sudo apt update
sudo apt install -y libopenblas-dev libarpack2

# Install CUDA
sudo apt update
sudo apt install -y cuda-toolkit-12-6 libnccl2 libnccl-dev libcusparselt0 libcudnn9-cuda-12 libnvshmem3-cuda-12

