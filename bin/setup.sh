#!/bin/bash

# Install OpenBLAS and ARPACK
sudo apt update
sudo apt install -y libopenblas-dev libarpack2

# Install CUDA
sudo apt update
sudo apt install -y cuda-toolkit-13-2 libnccl2 libnccl-dev libcusparselt0 libcudnn9-cuda-13 libnvshmem3-cuda-13

