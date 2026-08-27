# Marlin (vendored)

FP16×INT4 weight-only GEMM kernel from [IST-DASLab/marlin](https://github.com/IST-DASLab/marlin)
(Apache-2.0). Used by SMILE as an **Ampere/Ada failover** for HuggingFace GPTQ/AWQ
checkpoints. Hopper+/Blackwell use native FP8/NVFP4 (cuBLASLt) instead.

Files:
- `marlin_cuda_kernel.cu` — CUDA kernel
- `LICENSE` — Apache-2.0
- `README.smile.md` — upstream README snapshot

SMILE wrapper: `../../smile_marlin.cpp` / `../../smile_marlin.h` (enabled with `-DUSE_MARLIN=ON`).

SMILE patch in `marlin_cuda_kernel.cu`:
- use `if constexpr (group_blocks != -1)` for grouped-scale paths so NVCC does not
  emit division/modulo-by-zero warnings when instantiating column-wise kernels;
- check `cudaFuncSetAttribute` return for 96KB dynamic shared memory (A100/Ada).
