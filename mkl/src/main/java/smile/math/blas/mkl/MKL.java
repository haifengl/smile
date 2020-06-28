/*******************************************************************************
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
 ******************************************************************************/

package smile.math.blas.mkl;

import smile.math.blas.*;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.indexer.*;
import static org.bytedeco.mkl.global.mkl_rt.*;

/**
 * Intel MKL library wrapper.
 *
 * @author Haifeng Li
 */
public class MKL implements BLAS, LAPACK {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MKL.class);

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
        cblas_dgemv(layout.getValue(), trans.getValue(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gemv(Layout layout, Transpose trans, int m, int n, double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dgemv(layout.getValue(), trans.getValue(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gemv(Layout layout, Transpose trans, int m, int n, float alpha, float[] A, int lda, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_sgemv(layout.getValue(), trans.getValue(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gemv(Layout layout, Transpose trans, int m, int n, float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_sgemv(layout.getValue(), trans.getValue(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void symv(Layout layout, UPLO uplo, int n, double alpha, double[] A, int lda, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dsymv(layout.getValue(), uplo.getValue(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void symv(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dsymv(layout.getValue(), uplo.getValue(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void symv(Layout layout, UPLO uplo, int n, float alpha, float[] A, int lda, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_ssymv(layout.getValue(), uplo.getValue(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void symv(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_ssymv(layout.getValue(), uplo.getValue(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void spmv(Layout layout, UPLO uplo, int n, double alpha, double[] A, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dspmv(layout.getValue(), uplo.getValue(), n, alpha, A, x, incx, beta, y, incy);
    }

    @Override
    public void spmv(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer A, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dspmv(layout.getValue(), uplo.getValue(), n, alpha, A, x, incx, beta, y, incy);
    }

    @Override
    public void spmv(Layout layout, UPLO uplo, int n, float alpha, float[] A, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_sspmv(layout.getValue(), uplo.getValue(), n, alpha, A, x, incx, beta, y, incy);
    }

    @Override
    public void spmv(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer A, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_sspmv(layout.getValue(), uplo.getValue(), n, alpha, A, x, incx, beta, y, incy);
    }

    @Override
    public void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, double[] A, int lda, double[] x, int incx) {
        cblas_dtrmv(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, A, lda, x, incx);
    }

    @Override
    public void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, DoubleBuffer A, int lda, DoubleBuffer x, int incx) {
        cblas_dtrmv(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, A, lda, x, incx);
    }

    @Override
    public void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, float[] A, int lda, float[] x, int incx) {
        cblas_strmv(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, A, lda, x, incx);
    }

    @Override
    public void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, FloatBuffer A, int lda, FloatBuffer x, int incx) {
        cblas_strmv(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, A, lda, x, incx);
    }

    @Override
    public void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, double[] A, double[] x, int incx) {
        cblas_dtpmv(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, A, x, incx);
    }

    @Override
    public void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, DoubleBuffer A, DoubleBuffer x, int incx) {
        cblas_dtpmv(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, A, x, incx);
    }

    @Override
    public void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, float[] A, float[] x, int incx) {
        cblas_stpmv(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, A, x, incx);
    }

    @Override
    public void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, FloatBuffer A, FloatBuffer x, int incx) {
        cblas_stpmv(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, A, x, incx);
    }

    @Override
    public void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku, double alpha, double[] A, int lda, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dgbmv(layout.getValue(), trans.getValue(), m, n, kl, ku, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku, double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dgbmv(layout.getValue(), trans.getValue(), m, n, kl, ku, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku, float alpha, float[] A, int lda, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_sgbmv(layout.getValue(), trans.getValue(), m, n, kl, ku, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku, float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_sgbmv(layout.getValue(), trans.getValue(), m, n, kl, ku, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void sbmv(Layout layout, UPLO uplo, int n, int k, double alpha, double[] A, int lda, double[] x, int incx, double beta, double[] y, int incy) {
        cblas_dsbmv(layout.getValue(), uplo.getValue(), n, k, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void sbmv(Layout layout, UPLO uplo, int n, int k, double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        cblas_dsbmv(layout.getValue(), uplo.getValue(), n, k, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void sbmv(Layout layout, UPLO uplo, int n, int k, float alpha, float[] A, int lda, float[] x, int incx, float beta, float[] y, int incy) {
        cblas_ssbmv(layout.getValue(), uplo.getValue(), n, k, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void sbmv(Layout layout, UPLO uplo, int n, int k, float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        cblas_ssbmv(layout.getValue(), uplo.getValue(), n, k, alpha, A, lda, x, incx, beta, y, incy);
    }

    @Override
    public void ger(Layout layout, int m, int n, double alpha, double[] x, int incx, double[] y, int incy, double[] A, int lda) {
        cblas_dger(layout.getValue(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    @Override
    public void ger(Layout layout, int m, int n, double alpha, DoubleBuffer x, int incx, DoubleBuffer y, int incy, DoubleBuffer A, int lda) {
        cblas_dger(layout.getValue(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    @Override
    public void ger(Layout layout, int m, int n, float alpha, float[] x, int incx, float[] y, int incy, float[] A, int lda) {
        cblas_sger(layout.getValue(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    @Override
    public void ger(Layout layout, int m, int n, float alpha, FloatBuffer x, int incx, FloatBuffer y, int incy, FloatBuffer A, int lda) {
        cblas_sger(layout.getValue(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    @Override
    public void syr(Layout layout, UPLO uplo, int n, double alpha, double[] x, int incx, double[] A, int lda) {
        cblas_dsyr(layout.getValue(), uplo.getValue(), n, alpha, x, incx, A, lda);
    }

    @Override
    public void syr(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer x, int incx, DoubleBuffer A, int lda) {
        cblas_dsyr(layout.getValue(), uplo.getValue(), n, alpha, x, incx, A, lda);
    }

    @Override
    public void syr(Layout layout, UPLO uplo, int n, float alpha, float[] x, int incx, float[] A, int lda) {
        cblas_ssyr(layout.getValue(), uplo.getValue(), n, alpha, x, incx, A, lda);
    }

    @Override
    public void syr(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer x, int incx, FloatBuffer A, int lda) {
        cblas_ssyr(layout.getValue(), uplo.getValue(), n, alpha, x, incx, A, lda);
    }

    @Override
    public void spr(Layout layout, UPLO uplo, int n, double alpha, double[] x, int incx, double[] A) {
        cblas_dspr(layout.getValue(), uplo.getValue(), n, alpha, x, incx, A);
    }

    @Override
    public void spr(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer x, int incx, DoubleBuffer A) {
        cblas_dspr(layout.getValue(), uplo.getValue(), n, alpha, x, incx, A);
    }

    @Override
    public void spr(Layout layout, UPLO uplo, int n, float alpha, float[] x, int incx, float[] A) {
        cblas_sspr(layout.getValue(), uplo.getValue(), n, alpha, x, incx, A);
    }

    @Override
    public void spr(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer x, int incx, FloatBuffer A) {
        cblas_sspr(layout.getValue(), uplo.getValue(), n, alpha, x, incx, A);
    }

    @Override
    public void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k, double alpha, double[] A, int lda, double[] B, int ldb, double beta, double[] C, int ldc) {
        cblas_dgemm(layout.getValue(), transA.getValue(), transB.getValue(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k, double alpha, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, double beta, DoubleBuffer C, int ldc) {
        cblas_dgemm(layout.getValue(), transA.getValue(), transB.getValue(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k, float alpha, float[] A, int lda, float[] B, int ldb, float beta, float[] C, int ldc) {
        cblas_sgemm(layout.getValue(), transA.getValue(), transB.getValue(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k, float alpha, FloatBuffer A, int lda, FloatBuffer B, int ldb, float beta, FloatBuffer C, int ldc) {
        cblas_sgemm(layout.getValue(), transA.getValue(), transB.getValue(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void symm(Layout layout, Side side, UPLO uplo, int m, int n, double alpha, double[] A, int lda, double[] B, int ldb, double beta, double[] C, int ldc) {
        cblas_dsymm(layout.getValue(), side.getValue(), uplo.getValue(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void symm(Layout layout, Side side, UPLO uplo, int m, int n, double alpha, DoubleBuffer A, int lda, DoubleBuffer B, int ldb, double beta, DoubleBuffer C, int ldc) {
        cblas_dsymm(layout.getValue(), side.getValue(), uplo.getValue(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void symm(Layout layout, Side side, UPLO uplo, int m, int n, float alpha, float[] A, int lda, float[] B, int ldb, float beta, float[] C, int ldc) {
        cblas_ssymm(layout.getValue(), side.getValue(), uplo.getValue(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public void symm(Layout layout, Side side, UPLO uplo, int m, int n, float alpha, FloatBuffer A, int lda, FloatBuffer B, int ldb, float beta, FloatBuffer C, int ldc) {
        cblas_ssymm(layout.getValue(), side.getValue(), uplo.getValue(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    @Override
    public int gesv(Layout layout, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dgesv(layout.getValue(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gesv(Layout layout, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sgesv(layout.getValue(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int sysv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dsysv(layout.getValue(), uplo.getValue(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int sysv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_ssysv(layout.getValue(), uplo.getValue(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int spsv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dspsv(layout.getValue(), uplo.getValue(), n, nrhs, A, ipiv, B, ldb);
    }

    @Override
    public int spsv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sspsv(layout.getValue(), uplo.getValue(), n, nrhs, A, ipiv, B, ldb);
    }

    @Override
    public int posv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        return LAPACKE_dposv(layout.getValue(), uplo.getValue(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int posv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        return LAPACKE_sposv(layout.getValue(), uplo.getValue(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int ppsv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, double[] B, int ldb) {
        return LAPACKE_dppsv(layout.getValue(), uplo.getValue(), n, nrhs, A, B, ldb);
    }

    @Override
    public int ppsv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, float[] B, int ldb) {
        return LAPACKE_sppsv(layout.getValue(), uplo.getValue(), n, nrhs, A, B, ldb);
    }

    @Override
    public int gbsv(Layout layout, int n, int kl, int ku, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dgbsv(layout.getValue(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gbsv(Layout layout, int n, int kl, int ku, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sgbsv(layout.getValue(), n, kl, ku, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int gels(Layout layout, Transpose trans, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        return LAPACKE_dgels(layout.getValue(), trans.getValue(), m, n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int gels(Layout layout, Transpose trans, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        return LAPACKE_sgels(layout.getValue(), trans.getValue(), m, n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int gelsy(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, int[] jpvt, double rcond, int[] rank) {
        return LAPACKE_dgelsy(layout.getValue(), m, n, nrhs, A, lda, B, ldb, jpvt, rcond, rank);
    }

    @Override
    public int gelsy(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, int[] jpvt, float rcond, int[] rank) {
        return LAPACKE_sgelsy(layout.getValue(), m, n, nrhs, A, lda, B, ldb, jpvt, rcond, rank);
    }

    @Override
    public int gelss(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, double[] s, double rcond, int[] rank) {
        return LAPACKE_dgelss(layout.getValue(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelss(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, float[] s, float rcond, int[] rank) {
        return LAPACKE_sgelss(layout.getValue(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelsd(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, double[] s, double rcond, int[] rank) {
        return LAPACKE_dgelsd(layout.getValue(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gelsd(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, float[] s, float rcond, int[] rank) {
        return LAPACKE_sgelsd(layout.getValue(), m, n, nrhs, A, lda, B, ldb, s, rcond, rank);
    }

    @Override
    public int gglse(Layout layout, int m, int n, int p, double[] A, int lda, double[] B, int ldb, double[] c, double[] d, double[] x) {
        return LAPACKE_dgglse(layout.getValue(), m, n, p, A, lda, B, ldb, c, d, x);
    }

    @Override
    public int gglse(Layout layout, int m, int n, int p, float[] A, int lda, float[] B, int ldb, float[] c, float[] d, float[] x) {
        return LAPACKE_sgglse(layout.getValue(), m, n, p, A, lda, B, ldb, c, d, x);
    }

    @Override
    public int ggglm(Layout layout, int n, int m, int p, double[] A, int lda, double[] B, int ldb, double[] d, double[] x, double[] y) {
        return LAPACKE_dggglm(layout.getValue(), n, m, p, A, lda, B, ldb, d, x, y);
    }

    @Override
    public int ggglm(Layout layout, int n, int m, int p, float[] A, int lda, float[] B, int ldb, float[] d, float[] x, float[] y) {
        return LAPACKE_sggglm(layout.getValue(), n, m, p, A, lda, B, ldb, d, x, y);
    }

    @Override
    public int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, double[] A, int lda, double[] wr, double[] wi, double[] Vl, int ldvl, double[] Vr, int ldvr) {
        return LAPACKE_dgeev(layout.getValue(), jobvl.getValue(), jobvr.getValue(), n, A, lda, wr, wi, Vl, ldvl, Vr, ldvr);
    }

    @Override
    public int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, float[] A, int lda, float[] wr, float[] wi, float[] Vl, int ldvl, float[] Vr, int ldvr) {
        return LAPACKE_sgeev(layout.getValue(), jobvl.getValue(), jobvr.getValue(), n, A, lda, wr, wi, Vl, ldvl, Vr, ldvr);
    }

    @Override
    public int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, double[] A, int lda, double[] w) {
        return LAPACKE_dsyev(layout.getValue(), jobz.getValue(), uplo.getValue(), n, A, lda, w);
    }

    @Override
    public int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, float[] A, int lda, float[] w) {
        return LAPACKE_ssyev(layout.getValue(), jobz.getValue(), uplo.getValue(), n, A, lda, w);
    }

    @Override
    public int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, double[] A, int lda, double[] w) {
        return LAPACKE_dsyevd(layout.getValue(), jobz.getValue(), uplo.getValue(), n, A, lda, w);
    }

    @Override
    public int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, float[] A, int lda, float[] w) {
        return LAPACKE_ssyevd(layout.getValue(), jobz.getValue(), uplo.getValue(), n, A, lda, w);
    }

    @Override
    public int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, double[] A, int lda, double vl, double vu, int il, int iu, double abstol, int[] m, double[] w, double[] Z, int ldz, int[] isuppz) {
        return LAPACKE_dsyevr(layout.getValue(), jobz.getValue(), range.getValue(), uplo.getValue(), n, A, lda, vl, vu, il, iu, abstol, m, w, Z, ldz, isuppz);
    }

    @Override
    public int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, float[] A, int lda, float vl, float vu, int il, int iu, float abstol, int[] m, float[] w, float[] Z, int ldz, int[] isuppz) {
        return LAPACKE_ssyevr(layout.getValue(), jobz.getValue(), range.getValue(), uplo.getValue(), n, A, lda, vl, vu, il, iu, abstol, m, w, Z, ldz, isuppz);
    }

    @Override
    public int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, double[] A, int lda, double[] s, double[] U, int ldu, double[] VT, int ldvt, double[] superb) {
        return LAPACKE_dgesvd(layout.getValue(), jobu.getValue(), jobvt.getValue(), m, n, A, lda, s, U, ldu, VT, ldvt, superb);
    }

    @Override
    public int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, float[] A, int lda, float[] s, float[] U, int ldu, float[] VT, int ldvt, float[] superb) {
        return LAPACKE_sgesvd(layout.getValue(), jobu.getValue(), jobvt.getValue(), m, n, A, lda, s, U, ldu, VT, ldvt, superb);
    }

    @Override
    public int gesdd(Layout layout, SVDJob jobz, int m, int n, double[] A, int lda, double[] s, double[] U, int ldu, double[] VT, int ldvt) {
        return LAPACKE_dgesdd(layout.getValue(), jobz.getValue(), m, n, A, lda, s, U, ldu, VT, ldvt);
    }

    @Override
    public int gesdd(Layout layout, SVDJob jobz, int m, int n, float[] A, int lda, float[] s, float[] U, int ldu, float[] VT, int ldvt) {
        return LAPACKE_sgesdd(layout.getValue(), jobz.getValue(), m, n, A, lda, s, U, ldu, VT, ldvt);
    }

    @Override
    public int getrf(Layout layout, int m, int n, double[] A, int lda, int[] ipiv) {
        return LAPACKE_dgetrf(layout.getValue(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf(Layout layout, int m, int n, float[] A, int lda, int[] ipiv) {
        return LAPACKE_sgetrf(layout.getValue(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf2(Layout layout, int m, int n, double[] A, int lda, int[] ipiv) {
        return LAPACKE_dgetrf2(layout.getValue(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrf2(Layout layout, int m, int n, float[] A, int lda, int[] ipiv) {
        return LAPACKE_sgetrf2(layout.getValue(), m, n, A, lda, ipiv);
    }

    @Override
    public int getrs(Layout layout, Transpose trans, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        return LAPACKE_dgetrs(layout.getValue(), trans.getValue(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int getrs(Layout layout, Transpose trans, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        return LAPACKE_sgetrs(layout.getValue(), trans.getValue(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    @Override
    public int potrf(Layout layout, UPLO uplo, int n, double[] A, int lda) {
        return LAPACKE_dpotrf(layout.getValue(), uplo.getValue(), n, A, lda);
    }

    @Override
    public int potrf(Layout layout, UPLO uplo, int n, float[] A, int lda) {
        return LAPACKE_spotrf(layout.getValue(), uplo.getValue(), n, A, lda);
    }

    @Override
    public int potrf2(Layout layout, UPLO uplo, int n, double[] A, int lda) {
        return LAPACKE_dpotrf2(layout.getValue(), uplo.getValue(), n, A, lda);
    }

    @Override
    public int potrf2(Layout layout, UPLO uplo, int n, float[] A, int lda) {
        return LAPACKE_spotrf2(layout.getValue(), uplo.getValue(), n, A, lda);
    }

    @Override
    public int potrs(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        return LAPACKE_dpotrs(layout.getValue(), uplo.getValue(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int potrs(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        return LAPACKE_spotrs(layout.getValue(), uplo.getValue(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int geqrf(Layout layout, int m, int n, double[] A, int lda, double[] tau) {
        return LAPACKE_dgeqrf(layout.getValue(), m, n, A, lda, tau);
    }

    @Override
    public int geqrf(Layout layout, int m, int n, float[] A, int lda, float[] tau) {
        return LAPACKE_sgeqrf(layout.getValue(), m, n, A, lda, tau);
    }

    @Override
    public int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k, double[] A, int lda, double[] tau, double[] C, int ldc) {
        return LAPACKE_dormqr(layout.getValue(), side.getValue(), trans.getValue(), m, n, k, A, lda, tau, C, ldc);
    }

    @Override
    public int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k, float[] A, int lda, float[] tau, float[] C, int ldc) {
        return LAPACKE_sormqr(layout.getValue(), side.getValue(), trans.getValue(), m, n, k, A, lda, tau, C, ldc);
    }

    @Override
    public int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        return LAPACKE_dtrtrs(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, nrhs, A, lda, B, ldb);
    }

    @Override
    public int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        return LAPACKE_strtrs(layout.getValue(), uplo.getValue(), trans.getValue(), diag.getValue(), n, nrhs, A, lda, B, ldb);
    }
}
