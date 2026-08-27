# Marlin (vendored)

FP16×INT4 weight-only GEMM kernel from [IST-DASLab/marlin](https://github.com/IST-DASLab/marlin)
(Apache-2.0). Used by Smile as an **Ampere/Ada failover** for HuggingFace GPTQ/AWQ
checkpoints. Hopper+/Blackwell use native FP8/NVFP4 (cuBLASLt) instead.

Files:
- `marlin_cuda_kernel.cu` — CUDA kernel
- `LICENSE` — Apache-2.0
- `README.smile.md` — upstream README snapshot

Smile wrapper: `../../smile_marlin.cpp` / `../../smile_marlin.h` (enabled with `-DUSE_MARLIN=ON`).
