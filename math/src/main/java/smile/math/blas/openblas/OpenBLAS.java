/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.blas.openblas;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import smile.math.blas.*;
import static org.bytedeco.openblas.global.openblas.*;

/**
 * OpenBLAS library wrapper.
 *
 * @author Haifeng Li
 */
public class OpenBLAS implements BLAS, LAPACK {
    @Override
    public double asum(int n, double[] x, int incx) {
        return cblas_dasum(n, x, incx);
    }

    @Override
    public float asum(int n, float[] x, int incx) {
        return cblas_sasum(n, x, incx);
    }

    @Override
    public void axpy(int n, double alpha, double[] x, int incx, double[] y, int incy) {
        cblas_daxpy(n, alpha, x, incx, y, incy);
    }

    @Override
    public void axpy(int n, float alpha, float[] x, int incx, float[] y, int incy) {
        cblas_saxpy(n, alpha, x, incx, y, incy);
    }

    @Override
    public double dot(int n, double[] x, int incx, double[] y, int incy) {
        return cblas_ddot(n, x, incx, y, incy);
    }

    @Override
    public float dot(int n, float[] x, int incx, float[] y, int incy) {
        return cblas_sdot(n, x, incx, y, incy);
    }

    @Override
    public double nrm2(int n, double[] x, int incx) {
        return cblas_dnrm2(n, x, incx);
    }

    @Override
    public float nrm2(int n, float[] x, int incx) {
        return cblas_snrm2(n, x, incx);
    }

    @Override
    public void scal(int n, double alpha, double[] x, int incx) {
        cblas_dscal(n, alpha, x, incx);
    }

    @Override
    public void scal(int n, float alpha, float[] x, int incx) {
        cblas_sscal(n, alpha, x, incx);
    }

    @Override
    public void swap(int n, double[] x, int incx, double[] y, int incy) {
        cblas_dswap(n, x, incx, y, incy);
    }

    @Override
    public void swap(int n, float[] x, int incx, float[] y, int incy) {
        cblas_sswap(n, x, incx, y, incy);
    }

    @Override
    public long iamax(int n, double[] x, int incx) {
        return cblas_idamax(n, x, incx);
    }

    @Override
    public long iamax(int n, float[] x, int incx) {
        return cblas_isamax(n, x, incx);
    }

    @Override
    public void gemv(Layout layout, Transpose trans, int m, int n, double alpha, double[] A, int lda, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dgemv(layout.blas(), trans.blas(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gemv(Layout layout, Transpose trans, int m, int n, double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dgemv(layout.blas(), trans.blas(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gemv(Layout layout, Transpose trans, int m, int n, float alpha, float[] A, int lda, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_sgemv(layout.blas(), trans.blas(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gemv(Layout layout, Transpose trans, int m, int n, float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_sgemv(layout.blas(), trans.blas(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void symv(Layout layout, UPLO uplo, int n, double alpha, double[] A, int lda, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dsymv(layout.blas(), uplo.blas(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void symv(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dsymv(layout.blas(), uplo.blas(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void symv(Layout layout, UPLO uplo, int n, float alpha, float[] A, int lda, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_ssymv(layout.blas(), uplo.blas(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void symv(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_ssymv(layout.blas(), uplo.blas(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void spmv(Layout layout, UPLO uplo, int n, double alpha, double[] A, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dspmv(layout.blas(), uplo.blas(), n, alpha, A, x, incx, beta, y, incy);
    }

    @Override
    public void spmv(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer A, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dspmv(layout.blas(), uplo.blas(), n, alpha, A, x, incx, beta, y, incy);
    }

    @Override
    public void spmv(Layout layout, UPLO uplo, int n, float alpha, float[] A, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_sspmv(layout.blas(), uplo.blas(), n, alpha, A, x, incx, beta, y, incy);
    }

    @Override
    public void spmv(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer A, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_sspmv(layout.blas(), uplo.blas(), n, alpha, A, x, incx, beta, y, incy);
    }

    @Override
    public void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, double[] A, int lda, double[] x, int incx) {
        cblas_dtrmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, lda, x, incx);
    }

    @Override
    public void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, DoubleBuffer A, int lda, DoubleBuffer x, int incx) {
        cblas_dtrmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, lda, x, incx);
    }

    @Override
    public void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, float[] A, int lda, float[] x, int incx) {
        cblas_strmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, lda, x, incx);
    }

    @Override
    public void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, FloatBuffer A, int lda, FloatBuffer x, int incx) {
        cblas_strmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, lda, x, incx);
    }

    @Override
    public void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, double[] A, double[] x, int incx) {
        cblas_dtpmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, x, incx);
    }

    @Override
    public void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, DoubleBuffer A, DoubleBuffer x, int incx) {
        cblas_dtpmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, x, incx);
    }

    @Override
    public void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, float[] A, float[] x, int incx) {
        cblas_stpmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, x, incx);
    }

    @Override
    public void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, FloatBuffer A, FloatBuffer x, int incx) {
        cblas_stpmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, x, incx);
    }

    @Override
    public void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku, double alpha, double[] A, int lda, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dgbmv(layout.blas(), trans.blas(), m, n, kl, ku, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku, double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dgbmv(layout.blas(), trans.blas(), m, n, kl, ku, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku, float alpha, float[] A, int lda, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_sgbmv(layout.blas(), trans.blas(), m, n, kl, ku, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku, float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_sgbmv(layout.blas(), trans.blas(), m, n, kl, ku, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void sbmv(Layout layout, UPLO uplo, int n, int k, double alpha, double[] A, int lda, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dsbmv(layout.blas(), uplo.blas(), n, k, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void sbmv(Layout layout, UPLO uplo, int n, int k, double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dsbmv(layout.blas(), uplo.blas(), n, k, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void sbmv(Layout layout, UPLO uplo, int n, int k, float alpha, float[] A, int lda, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_ssbmv(layout.blas(), uplo.blas(), n, k, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void sbmv(Layout layout, UPLO uplo, int n, int k, float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_ssbmv(layout.blas(), uplo.blas(), n, k, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void ger(Layout layout, int m, int n, double alpha, double[] x, int incx, double[] y, int incy, double[] A, int lda) {
        cblas_dger(layout.blas(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    @Override
    public void ger(Layout layout, int m, int n, double alpha, DoubleBuffer x, int incx, DoubleBuffer y, int incy, DoubleBuffer A, int lda) {
        cblas_dger(layout.blas(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    @Override
    public void ger(Layout layout, int m, int n, float alpha, float[] x, int incx, float[] y, int incy, float[] A, int lda) {
        cblas_sger(layout.blas(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    @Override
    public void ger(Layout layout, int m, int n, float alpha, FloatBuffer x, int incx, FloatBuffer y, int incy, FloatBuffer A, int lda) {
        cblas_sger(layout.blas(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    @Override
    public void syr(Layout layout, UPLO uplo, int n, double alpha, double[] x, int incx, double[] A, int lda) {
        cblas_dsyr(layout.blas(), uplo.blas(), n, alpha, x, incx, A, lda);
    }

    @Override
    public void syr(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer x, int incx, DoubleBuffer A, int lda) {
        cblas_dsyr(layout.blas(), uplo.blas(), n, alpha, x, incx, A, lda);
    }

    @Override
    public void syr(Layout layout, UPLO uplo, int n, float alpha, float[] x, int incx, float[] A, int lda) {
        cblas_ssyr(layout.blas(), uplo.blas(), n, alpha, x, incx, A, lda);
    }

    @Override
    public void syr(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer x, int incx, FloatBuffer A, int lda) {
        cblas_ssyr(layout.blas(), uplo.blas(), n, alpha, x, incx, A, lda);
    }

    @Override
    public void spr(Layout layout, UPLO uplo, int n, double alpha, double[] x, int incx, double[] A) {
        cblas_dspr(layout.blas(), uplo.blas(), n, alpha, x, incx, A);
    }

    @Override
    public void spr(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer x, int incx, DoubleBuffer A) {
        cblas_dspr(layout.blas(), uplo.blas(), n, alpha, x, incx, A);
    }

    @Override
    public void spr(Layout layout, UPLO uplo, int n, float alpha, float[] x, int incx, float[] A) {
        cblas_sspr(layout.blas(), uplo.blas(), n, alpha, x, incx, A);
    }

    @Override
    public void spr(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer x, int incx, FloatBuffer A) {
        cblas_sspr(layout.blas(), uplo.blas(), n, alpha, x, incx, A);
    }

    @Override
    public void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k, double alpha, double[] A, int lda, double[] B, int ldb, double beta, double[] C, int ldc) {
        cblas_dgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k, double alpha, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, double beta, DoubleBuffer C, int ldc) {
        cblas_dgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k, float alpha, float[] A, int lda, float[] B, int ldb, float beta, float[] C, int ldc) {
        cblas_sgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k, float alpha, FloatBuffer A, int lda, FloatBuffer B, int ldb, float beta, FloatBuffer C, int ldc) {
        cblas_sgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void symm(Layout layout, Side side, UPLO uplo, int m, int n, double alpha, double[] A, int lda, double[] B, int ldb, double beta, double[] C, int ldc) {
        cblas_dsymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void symm(Layout layout, Side side, UPLO uplo, int m, int n, double alpha, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, double beta, DoubleBuffer C, int ldc) {
        cblas_dsymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void symm(Layout layout, Side side, UPLO uplo, int m, int n, float alpha, float[] A, int lda, float[] B, int ldb, float beta, float[] C, int ldc) {
        cblas_ssymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void symm(Layout layout, Side side, UPLO uplo, int m, int n, float alpha, FloatBuffer A, int lda, FloatBuffer B, int ldb, float beta, FloatBuffer C, int ldc) {
        cblas_ssymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public int gesv(Layout layout, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dgesv(layout.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gesv(Layout layout, int n, int nrhs, DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        return LAPACKE_dgesv(layout.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gesv(Layout layout, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sgesv(layout.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gesv(Layout layout, int n, int nrhs, FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        return LAPACKE_sgesv(layout.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int sysv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dsysv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int sysv(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        return LAPACKE_dsysv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int sysv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_ssysv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int sysv(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        return LAPACKE_ssysv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int spsv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dspsv(layout.lapack(), uplo.lapack(), n, nrhs, A, ipiv, B, ldb);
    }

    @Override
    public int spsv(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        return LAPACKE_dspsv(layout.lapack(), uplo.lapack(), n, nrhs, A, ipiv, B, ldb);
    }

    @Override
    public int spsv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sspsv(layout.lapack(), uplo.lapack(), n, nrhs, A, ipiv, B, ldb);
    }

    @Override
    public int spsv(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, IntBuffer ipiv, FloatBuffer B, int ldb) {
        return LAPACKE_sspsv(layout.lapack(), uplo.lapack(), n, nrhs, A, ipiv, B, ldb);
    }

    @Override
    public int posv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        return LAPACKE_dposv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int posv(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb) {
        return LAPACKE_dposv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int posv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        return LAPACKE_sposv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int posv(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb) {
        return LAPACKE_sposv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int ppsv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, double[] B, int ldb) {
        return LAPACKE_dppsv(layout.lapack(), uplo.lapack(), n, nrhs, A, B, ldb);
    }

    @Override
    public int ppsv(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, DoubleBuffer B, int ldb) {
        return LAPACKE_dppsv(layout.lapack(), uplo.lapack(), n, nrhs, A, B, ldb);
    }

    @Override
    public int ppsv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, float[] B, int ldb) {
        return LAPACKE_sppsv(layout.lapack(), uplo.lapack(), n, nrhs, A, B, ldb);
    }

    @Override
    public int ppsv(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, FloatBuffer B, int ldb) {
        return LAPACKE_sppsv(layout.lapack(), uplo.lapack(), n, nrhs, A, B, ldb);
    }

    @Override
    public int gbsv(Layout layout, int n, int kl, int ku, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dgbsv(layout.lapack(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gbsv(Layout layout, int n, int kl, int ku, int nrhs, DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        return LAPACKE_dgbsv(layout.lapack(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gbsv(Layout layout, int n, int kl, int ku, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sgbsv(layout.lapack(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gbsv(Layout layout, int n, int kl, int ku, int nrhs, FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        return LAPACKE_sgbsv(layout.lapack(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gels(Layout layout, Transpose trans, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        return LAPACKE_dgels(layout.lapack(), trans.lapack(), m, n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int gels(Layout layout, Transpose trans, int m, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb) {
        return LAPACKE_dgels(layout.lapack(), trans.lapack(), m, n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int gels(Layout layout, Transpose trans, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        return LAPACKE_sgels(layout.lapack(), trans.lapack(), m, n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int gels(Layout layout, Transpose trans, int m, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb) {
        return LAPACKE_sgels(layout.lapack(), trans.lapack(), m, n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int gelsy(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, int[] jpvt, double rcond, int[] rank) {
        return LAPACKE_dgelsy(layout.lapack(), m, n, nrhs, A, lda, B, ldb, jpvt, rcond, rank);
    }

    @Override
    public int gelsy(Layout layout, int m, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, IntBuffer jpvt, double rcond, IntBuffer rank) {
        return LAPACKE_dgelsy(layout.lapack(), m, n, nrhs, A, lda, B, ldb, jpvt, rcond, rank);
    }

    @Override
    public int gelsy(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, int[] jpvt, float rcond, int[] rank) {
        return LAPACKE_sgelsy(layout.lapack(), m, n, nrhs, A, lda, B, ldb, jpvt, rcond, rank);
    }

    @Override
    public int gelsy(Layout layout, int m, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb, IntBuffer jpvt, float rcond, IntBuffer rank) {
        return LAPACKE_sgelsy(layout.lapack(), m, n, nrhs, A, lda, B, ldb, jpvt, rcond, rank);
    }

    @Override
    public int gelss(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, double[] s, double rcond, int[] rank) {
        return LAPACKE_dgelss(layout.lapack(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelss(Layout layout, int m, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, DoubleBuffer s, double rcond, IntBuffer rank) {
        return LAPACKE_dgelss(layout.lapack(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelss(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, float[] s, float rcond, int[] rank) {
        return LAPACKE_sgelss(layout.lapack(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelss(Layout layout, int m, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb, FloatBuffer s, float rcond, IntBuffer rank) {
        return LAPACKE_sgelss(layout.lapack(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelsd(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, double[] s, double rcond, int[] rank) {
        return LAPACKE_dgelsd(layout.lapack(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelsd(Layout layout, int m, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, DoubleBuffer s, double rcond, IntBuffer rank) {
        return LAPACKE_dgelsd(layout.lapack(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelsd(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, float[] s, float rcond, int[] rank) {
        return LAPACKE_sgelsd(layout.lapack(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelsd(Layout layout, int m, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb, FloatBuffer s, float rcond, IntBuffer rank) {
        return LAPACKE_sgelsd(layout.lapack(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gglse(Layout layout, int m, int n, int p, double[] A, int lda, double[] B, int ldb, double[] c, double[] d, double[] x) {
        return LAPACKE_dgglse(layout.lapack(), m, n, p, A, lda, B, ldb, c, d, x);
    }

    @Override
    public int gglse(Layout layout, int m, int n, int p, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, DoubleBuffer c, DoubleBuffer d, DoubleBuffer x) {
        return LAPACKE_dgglse(layout.lapack(), m, n, p, A, lda, B, ldb, c, d, x);
    }

    @Override
    public int gglse(Layout layout, int m, int n, int p, float[] A, int lda, float[] B, int ldb, float[] c, float[] d, float[] x) {
        return LAPACKE_sgglse(layout.lapack(), m, n, p, A, lda, B, ldb, c, d, x);
    }

    @Override
    public int gglse(Layout layout, int m, int n, int p, FloatBuffer A, int lda, FloatBuffer B, int ldb, FloatBuffer c, FloatBuffer d, FloatBuffer x) {
        return LAPACKE_sgglse(layout.lapack(), m, n, p, A, lda, B, ldb, c, d, x);
    }

    @Override
    public int ggglm(Layout layout, int n, int m, int p, double[] A, int lda, double[] B, int ldb, double[] d, double[] x, double[] y) {
        return LAPACKE_dggglm(layout.lapack(), n, m, p, A, lda, B, ldb, d, x, y);
    }

    @Override
    public int ggglm(Layout layout, int n, int m, int p, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, DoubleBuffer d, DoubleBuffer x, DoubleBuffer y) {
        return LAPACKE_dggglm(layout.lapack(), n, m, p, A, lda, B, ldb, d, x, y);
    }

    @Override
    public int ggglm(Layout layout, int n, int m, int p, float[] A, int lda, float[] B, int ldb, float[] d, float[] x, float[] y) {
        return LAPACKE_sggglm(layout.lapack(), n, m, p, A, lda, B, ldb, d, x, y);
    }

    @Override
    public int ggglm(Layout layout, int n, int m, int p, FloatBuffer A, int lda, FloatBuffer B, int ldb, FloatBuffer d, FloatBuffer x, FloatBuffer y) {
        return LAPACKE_sggglm(layout.lapack(), n, m, p, A, lda, B, ldb, d, x, y);
    }

    @Override
    public int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, double[] A, int lda, double[] wr, double[] wi, double[] Vl, int ldvl, double[] Vr, int ldvr) {
        return LAPACKE_dgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A, lda, wr, wi, Vl, ldvl, Vr, ldvr);
    }

    @Override
    public int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, DoubleBuffer A, int lda, DoubleBuffer wr, DoubleBuffer wi, DoubleBuffer Vl, int ldvl, DoubleBuffer Vr, int ldvr) {
        return LAPACKE_dgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A, lda, wr, wi, Vl, ldvl, Vr, ldvr);
    }

    @Override
    public int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, float[] A, int lda, float[] wr, float[] wi, float[] Vl, int ldvl, float[] Vr, int ldvr) {
        return LAPACKE_sgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A, lda, wr, wi, Vl, ldvl, Vr, ldvr);
    }

    @Override
    public int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, FloatBuffer A, int lda, FloatBuffer wr, FloatBuffer wi, FloatBuffer Vl, int ldvl, FloatBuffer Vr, int ldvr) {
        return LAPACKE_sgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A, lda, wr, wi, Vl, ldvl, Vr, ldvr);
    }

    @Override
    public int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, double[] A, int lda, double[] w) {
        return LAPACKE_dsyev(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    @Override
    public int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, DoubleBuffer A, int lda, DoubleBuffer w) {
        return LAPACKE_dsyev(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    @Override
    public int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, float[] A, int lda, float[] w) {
        return LAPACKE_ssyev(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    @Override
    public int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, FloatBuffer A, int lda, FloatBuffer w) {
        return LAPACKE_ssyev(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    @Override
    public int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, double[] A, int lda, double[] w) {
        return LAPACKE_dsyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    @Override
    public int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, DoubleBuffer A, int lda, DoubleBuffer w) {
        return LAPACKE_dsyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    @Override
    public int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, float[] A, int lda, float[] w) {
        return LAPACKE_ssyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    @Override
    public int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, FloatBuffer A, int lda, FloatBuffer w) {
        return LAPACKE_ssyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    @Override
    public int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, double[] A, int lda, double vl, double vu, int il, int iu, double abstol, int[] m, double[] w, double[] Z, int ldz, int[] isuppz) {
        return LAPACKE_dsyevr(layout.lapack(), jobz.lapack(), range.lapack(), uplo.lapack(), n, A, lda, vl, vu, il, iu, abstol, m, w, Z, ldz, isuppz);
    }

    @Override
    public int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, DoubleBuffer A, int lda, double vl, double vu, int il, int iu, double abstol, IntBuffer m, DoubleBuffer w, DoubleBuffer Z, int ldz, IntBuffer isuppz) {
        return LAPACKE_dsyevr(layout.lapack(), jobz.lapack(), range.lapack(), uplo.lapack(), n, A, lda, vl, vu, il, iu, abstol, m, w, Z, ldz, isuppz);
    }

    @Override
    public int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, float[] A, int lda, float vl, float vu, int il, int iu, float abstol, int[] m, float[] w, float[] Z, int ldz, int[] isuppz) {
        return LAPACKE_ssyevr(layout.lapack(), jobz.lapack(), range.lapack(), uplo.lapack(), n, A, lda, vl, vu, il, iu, abstol, m, w, Z, ldz, isuppz);
    }

    @Override
    public int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, FloatBuffer A, int lda, float vl, float vu, int il, int iu, float abstol, IntBuffer m, FloatBuffer w, FloatBuffer Z, int ldz, IntBuffer isuppz) {
        return LAPACKE_ssyevr(layout.lapack(), jobz.lapack(), range.lapack(), uplo.lapack(), n, A, lda, vl, vu, il, iu, abstol, m, w, Z, ldz, isuppz);
    }

    @Override
    public int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, double[] A, int lda, double[] s, double[] U, int ldu, double[] VT, int ldvt, double[] superb) {
        return LAPACKE_dgesvd(layout.lapack(), jobu.lapack(), jobvt.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt, superb);
    }

    @Override
    public int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, DoubleBuffer A, int lda, DoubleBuffer s, DoubleBuffer U, int ldu, DoubleBuffer VT, int ldvt, DoubleBuffer superb) {
        return LAPACKE_dgesvd(layout.lapack(), jobu.lapack(), jobvt.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt, superb);
    }

    @Override
    public int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, float[] A, int lda, float[] s, float[] U, int ldu, float[] VT, int ldvt, float[] superb) {
        return LAPACKE_sgesvd(layout.lapack(), jobu.lapack(), jobvt.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt, superb);
    }

    @Override
    public int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, FloatBuffer A, int lda, FloatBuffer s, FloatBuffer U, int ldu, FloatBuffer VT, int ldvt, FloatBuffer superb) {
        return LAPACKE_sgesvd(layout.lapack(), jobu.lapack(), jobvt.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt, superb);
    }

    @Override
    public int gesdd(Layout layout, SVDJob jobz, int m, int n, double[] A, int lda, double[] s, double[] U, int ldu, double[] VT, int ldvt) {
        return LAPACKE_dgesdd(layout.lapack(), jobz.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt);
    }

    @Override
    public int gesdd(Layout layout, SVDJob jobz, int m, int n, DoubleBuffer A, int lda, DoubleBuffer s, DoubleBuffer U, int ldu, DoubleBuffer VT, int ldvt) {
        return LAPACKE_dgesdd(layout.lapack(), jobz.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt);
    }

    @Override
    public int gesdd(Layout layout, SVDJob jobz, int m, int n, float[] A, int lda, float[] s, float[] U, int ldu, float[] VT, int ldvt) {
        return LAPACKE_sgesdd(layout.lapack(), jobz.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt);
    }

    @Override
    public int gesdd(Layout layout, SVDJob jobz, int m, int n, FloatBuffer A, int lda, FloatBuffer s, FloatBuffer U, int ldu, FloatBuffer VT, int ldvt) {
        return LAPACKE_sgesdd(layout.lapack(), jobz.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt);
    }

    @Override
    public int getrf(Layout layout, int m, int n, DoubleBuffer A, int lda, IntBuffer ipiv) {
        return LAPACKE_dgetrf(layout.lapack(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf(Layout layout, int m, int n, double[] A, int lda, int[] ipiv) {
        return LAPACKE_dgetrf(layout.lapack(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf(Layout layout, int m, int n, float[] A, int lda, int[] ipiv) {
        return LAPACKE_sgetrf(layout.lapack(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf(Layout layout, int m, int n, FloatBuffer A, int lda, IntBuffer ipiv) {
        return LAPACKE_sgetrf(layout.lapack(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf2(Layout layout, int m, int n, double[] A, int lda, int[] ipiv) {
        return LAPACKE_dgetrf2(layout.lapack(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf2(Layout layout, int m, int n, DoubleBuffer A, int lda, IntBuffer ipiv) {
        return LAPACKE_dgetrf2(layout.lapack(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf2(Layout layout, int m, int n, float[] A, int lda, int[] ipiv) {
        return LAPACKE_sgetrf2(layout.lapack(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf2(Layout layout, int m, int n, FloatBuffer A, int lda, IntBuffer ipiv) {
        return LAPACKE_sgetrf2(layout.lapack(), m, n, A, lda, ipiv);
    }

    @Override
    public int gbtrf(Layout layout, int m, int n, int kl, int ku, double[] AB, int ldab, int[] ipiv) {
        return LAPACKE_dgbtrf(layout.lapack(), m, n, kl, ku, AB, ldab, ipiv);
    }

    @Override
    public int gbtrf(Layout layout, int m, int n, int kl, int ku, DoubleBuffer AB, int ldab, IntBuffer ipiv) {
        return LAPACKE_dgbtrf(layout.lapack(), m, n, kl, ku, AB, ldab, ipiv);
    }

    @Override
    public int gbtrf(Layout layout, int m, int n, int kl, int ku, float[] AB, int ldab, int[] ipiv) {
        return LAPACKE_sgbtrf(layout.lapack(), m, n, kl, ku, AB, ldab, ipiv);
    }

    @Override
    public int gbtrf(Layout layout, int m, int n, int kl, int ku, FloatBuffer AB, int ldab, IntBuffer ipiv) {
        return LAPACKE_sgbtrf(layout.lapack(), m, n, kl, ku, AB, ldab, ipiv);
    }

    @Override
    public int sptrf(Layout layout, UPLO uplo, int n, double[] AP, int[] ipiv) {
        return LAPACKE_dsptrf(layout.lapack(), uplo.lapack(), n, AP, ipiv);
    }

    @Override
    public int sptrf(Layout layout, UPLO uplo, int n, DoubleBuffer AP, IntBuffer ipiv) {
        return LAPACKE_dsptrf(layout.lapack(), uplo.lapack(), n, AP, ipiv);
    }

    @Override
    public int sptrf(Layout layout, UPLO uplo, int n, float[] AP, int[] ipiv) {
        return LAPACKE_ssptrf(layout.lapack(), uplo.lapack(), n, AP, ipiv);
    }

    @Override
    public int sptrf(Layout layout, UPLO uplo, int n, FloatBuffer AP, IntBuffer ipiv) {
        return LAPACKE_ssptrf(layout.lapack(), uplo.lapack(), n, AP, ipiv);
    }

    @Override
    public int getrs(Layout layout, Transpose trans, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dgetrs(layout.lapack(), trans.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int getrs(Layout layout, Transpose trans, int n, int nrhs, DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        return LAPACKE_dgetrs(layout.lapack(), trans.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int getrs(Layout layout, Transpose trans, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sgetrs(layout.lapack(), trans.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int getrs(Layout layout, Transpose trans, int n, int nrhs, FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        return LAPACKE_sgetrs(layout.lapack(), trans.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gbtrs(Layout layout, Transpose trans, int n, int kl, int ku, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dgbtrs(layout.lapack(), trans.lapack(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gbtrs(Layout layout, Transpose trans, int n, int kl, int ku, int nrhs, DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        return LAPACKE_dgbtrs(layout.lapack(), trans.lapack(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gbtrs(Layout layout, Transpose trans, int n, int kl, int ku, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sgbtrs(layout.lapack(), trans.lapack(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gbtrs(Layout layout, Transpose trans, int n, int kl, int ku, int nrhs, FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        return LAPACKE_sgbtrs(layout.lapack(), trans.lapack(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int sptrs(Layout layout, UPLO uplo, int n, int nrhs, double[] AP, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dsptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP, ipiv, B, ldb);
    }

    @Override
    public int sptrs(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer AP, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        return LAPACKE_dsptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP, ipiv, B, ldb);
    }

    @Override
    public int sptrs(Layout layout, UPLO uplo, int n, int nrhs, float[] AP, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_ssptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP, ipiv, B, ldb);
    }

    @Override
    public int sptrs(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer AP, IntBuffer ipiv, FloatBuffer B, int ldb) {
        return LAPACKE_ssptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP, ipiv, B, ldb);
    }

    @Override
    public int potrf(Layout layout, UPLO uplo, int n, double[] A, int lda) {
        return LAPACKE_dpotrf(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    @Override
    public int potrf(Layout layout, UPLO uplo, int n, DoubleBuffer A, int lda) {
        return LAPACKE_dpotrf(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    @Override
    public int potrf(Layout layout, UPLO uplo, int n, float[] A, int lda) {
        return LAPACKE_spotrf(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    @Override
    public int potrf(Layout layout, UPLO uplo, int n, FloatBuffer A, int lda) {
        return LAPACKE_spotrf(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    @Override
    public int potrf2(Layout layout, UPLO uplo, int n, double[] A, int lda) {
        return LAPACKE_dpotrf2(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    @Override
    public int potrf2(Layout layout, UPLO uplo, int n, DoubleBuffer A, int lda) {
        return LAPACKE_dpotrf2(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    @Override
    public int potrf2(Layout layout, UPLO uplo, int n, float[] A, int lda) {
        return LAPACKE_spotrf2(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    @Override
    public int potrf2(Layout layout, UPLO uplo, int n, FloatBuffer A, int lda) {
        return LAPACKE_spotrf2(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    @Override
    public int pbtrf(Layout layout, UPLO uplo, int n, int kd, double[] AB, int ldab) {
        return LAPACKE_dpbtrf(layout.lapack(), uplo.lapack(), n, kd, AB, ldab);
    }

    @Override
    public int pbtrf(Layout layout, UPLO uplo, int n, int kd, DoubleBuffer AB, int ldab) {
        return LAPACKE_dpbtrf(layout.lapack(), uplo.lapack(), n, kd, AB, ldab);
    }

    @Override
    public int pbtrf(Layout layout, UPLO uplo, int n, int kd, float[] AB, int ldab) {
        return LAPACKE_spbtrf(layout.lapack(), uplo.lapack(), n, kd, AB, ldab);
    }

    @Override
    public int pbtrf(Layout layout, UPLO uplo, int n, int kd, FloatBuffer AB, int ldab) {
        return LAPACKE_spbtrf(layout.lapack(), uplo.lapack(), n, kd, AB, ldab);
    }

    @Override
    public int pptrf(Layout layout, UPLO uplo, int n, double[] AP) {
        return LAPACKE_dpptrf(layout.lapack(), uplo.lapack(), n, AP);
    }

    @Override
    public int pptrf(Layout layout, UPLO uplo, int n, DoubleBuffer AP) {
        return LAPACKE_dpptrf(layout.lapack(), uplo.lapack(), n, AP);
    }

    @Override
    public int pptrf(Layout layout, UPLO uplo, int n, float[] AP) {
        return LAPACKE_spptrf(layout.lapack(), uplo.lapack(), n, AP);
    }

    @Override
    public int pptrf(Layout layout, UPLO uplo, int n, FloatBuffer AP) {
        return LAPACKE_spptrf(layout.lapack(), uplo.lapack(), n, AP);
    }

    @Override
    public int potrs(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        return LAPACKE_dpotrs(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int potrs(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb) {
        return LAPACKE_dpotrs(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int potrs(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        return LAPACKE_spotrs(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int potrs(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb) {
        return LAPACKE_spotrs(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int pbtrs(Layout layout, UPLO uplo, int n, int kd, int nrhs, double[] AB, int ldab, double[] B, int ldb) {
        return LAPACKE_dpbtrs(layout.lapack(), uplo.lapack(), n, kd, nrhs, AB, ldab, B, ldb);
    }

    @Override
    public int pbtrs(Layout layout, UPLO uplo, int n, int kd, int nrhs, DoubleBuffer AB, int ldab, DoubleBuffer B, int ldb) {
        return LAPACKE_dpbtrs(layout.lapack(), uplo.lapack(), n, kd, nrhs, AB, ldab, B, ldb);
    }

    @Override
    public int pbtrs(Layout layout, UPLO uplo, int n, int kd, int nrhs, float[] AB, int ldab, float[] B, int ldb) {
        return LAPACKE_spbtrs(layout.lapack(), uplo.lapack(), n, kd, nrhs, AB, ldab, B, ldb);
    }

    @Override
    public int pbtrs(Layout layout, UPLO uplo, int n, int kd, int nrhs, FloatBuffer AB, int ldab, FloatBuffer B, int ldb) {
        return LAPACKE_spbtrs(layout.lapack(), uplo.lapack(), n, kd, nrhs, AB, ldab, B, ldb);
    }

    @Override
    public int pptrs(Layout layout, UPLO uplo, int n, int nrhs, double[] AP, double[] B, int ldb) {
        return LAPACKE_dpptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP, B, ldb);
    }

    @Override
    public int pptrs(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer AP, DoubleBuffer B, int ldb) {
        return LAPACKE_dpptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP, B, ldb);
    }

    @Override
    public int pptrs(Layout layout, UPLO uplo, int n, int nrhs, float[] AP, float[] B, int ldb) {
        return LAPACKE_spptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP, B, ldb);
    }

    @Override
    public int pptrs(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer AP, FloatBuffer B, int ldb) {
        return LAPACKE_spptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP, B, ldb);
    }

    @Override
    public int geqrf(Layout layout, int m, int n, double[] A, int lda, double[] tau) {
        return LAPACKE_dgeqrf(layout.lapack(), m, n, A, lda, tau);
    }

    @Override
    public int geqrf(Layout layout, int m, int n, DoubleBuffer A, int lda, DoubleBuffer tau) {
        return LAPACKE_dgeqrf(layout.lapack(), m, n, A, lda, tau);
    }

    @Override
    public int geqrf(Layout layout, int m, int n, float[] A, int lda, float[] tau) {
        return LAPACKE_sgeqrf(layout.lapack(), m, n, A, lda, tau);
    }

    @Override
    public int geqrf(Layout layout, int m, int n, FloatBuffer A, int lda, FloatBuffer tau) {
        return LAPACKE_sgeqrf(layout.lapack(), m, n, A, lda, tau);
    }

    @Override
    public int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k, double[] A, int lda, double[] tau, double[] C, int ldc) {
        return LAPACKE_dormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A, lda, tau, C, ldc);
    }
    @Override
    public int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k, DoubleBuffer A, int lda, DoubleBuffer tau, DoubleBuffer C, int ldc) {
        return LAPACKE_dormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A, lda, tau, C, ldc);
    }

    @Override
    public int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k, float[] A, int lda, float[] tau, float[] C, int ldc) {
        return LAPACKE_sormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A, lda, tau, C, ldc);
    }


    @Override
    public int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k, FloatBuffer A, int lda, FloatBuffer tau, FloatBuffer C, int ldc) {
        return LAPACKE_sormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A, lda, tau, C, ldc);
    }

    @Override
    public int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        return LAPACKE_dtrtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb) {
        return LAPACKE_dtrtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        return LAPACKE_strtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb) {
        return LAPACKE_strtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A, lda, B, ldb);
    }
}
