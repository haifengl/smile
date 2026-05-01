/*
 * smile_torch_test.c — Smoke test for the smile_torch C API.
 *
 * Verifies that the library loads and basic tensor operations work correctly
 * without requiring an attached GPU.  Run via CTest after building with
 * -DBUILD_TESTING=ON.
 */
#include "smile_torch.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <math.h>

#define CHECK(cond, msg) \
    do { if (!(cond)) { \
        fprintf(stderr, "FAIL [%s:%d] %s: %s\n", __FILE__, __LINE__, msg, \
                smile_last_error()); \
        return 1; \
    } } while(0)

int main(void) {
    char ver[64];
    CHECK(smile_torch_version(ver, sizeof(ver)) == 0, "version");
    printf("libtorch version: %s\n", ver);

    /* ---- TensorOptions ---- */
    ST_TensorOptions opts = smile_tensor_options_create();
    CHECK(opts != NULL, "tensor_options_create");
    opts = smile_tensor_options_dtype(opts, ST_DTYPE_FLOAT);
    CHECK(opts != NULL, "tensor_options_dtype");

    /* ---- Tensor factory ---- */
    int64_t shape2[] = {3, 4};
    ST_Tensor z = smile_tensor_zeros(shape2, 2, opts);
    CHECK(z != NULL, "zeros");
    CHECK(smile_tensor_dim(z) == 2, "dim==2");
    CHECK(smile_tensor_size(z, 0) == 3, "size(0)==3");
    CHECK(smile_tensor_size(z, 1) == 4, "size(1)==4");
    CHECK(smile_tensor_dtype(z) == ST_DTYPE_FLOAT, "dtype float");

    /* ---- Arithmetic ---- */
    ST_Scalar s1 = smile_scalar_from_float(2.0);
    CHECK(s1 != NULL, "scalar_from_float");
    ST_Tensor a = smile_tensor_add_s(z, s1);
    CHECK(a != NULL, "add_s");
    CHECK(fabs(smile_tensor_item_float(
                   smile_tensor_sum(smile_tensor_zeros(shape2,2,opts)))) < 1e-6,
          "sum of zeros");

    /* ---- Linear layer ---- */
    ST_Linear lin = smile_linear_create(4, 8, 1);
    CHECK(lin != NULL, "linear_create");
    int64_t in_shape[] = {2, 4};
    ST_Tensor in = smile_tensor_randn(in_shape, 2, opts);
    CHECK(in != NULL, "randn input");
    ST_Tensor out = smile_linear_forward(lin, in);
    CHECK(out != NULL, "linear_forward");
    CHECK(smile_tensor_size(out, 0) == 2, "linear out batch");
    CHECK(smile_tensor_size(out, 1) == 8, "linear out features");

    /* ---- NoGradGuard ---- */
    ST_NoGradGuard ng = smile_no_grad_guard_create();
    CHECK(ng != NULL, "no_grad_guard");
    smile_no_grad_guard_free(ng);

    /* ---- Cleanup ---- */
    smile_tensor_free(out);
    smile_tensor_free(in);
    smile_linear_free(lin);
    smile_tensor_free(a);
    smile_scalar_free(s1);
    smile_tensor_free(z);
    smile_tensor_options_free(opts);

    printf("All smoke tests passed.\n");
    return 0;
}

