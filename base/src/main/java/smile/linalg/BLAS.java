/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.linalg;

import java.lang.foreign.MemorySegment;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import static smile.linalg.blas.cblas_openblas_h.*;

/**
 * Basic Linear Algebra Subprograms. BLAS is a specification that prescribes
 * a set of low-level routines for performing common linear algebra operations
 * such as vector addition, scalar multiplication, dot products, linear
 * combinations, and matrix multiplication. They are the de facto standard
 * low-level routines for linear algebra libraries.
 *
 * @author Haifeng Li
 */
public interface BLAS {
    /**
     * Sums the absolute values of the elements of a vector.
     * When working backward ({@code incx < 0}), each routine starts at the end of the
     * vector and moves backward.
     *
     * @param n Number of vector elements to be summed.
     *
     * @param x Array of dimension {@code (n-1) * abs(incx)+ 1}.
     *          Vector that contains elements to be summed.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @return Sum of the absolute values of the elements of the vector x.
     *         If {@code n <= 0}, DASUM is set to 0.
     */
    static double asum(int n, double[] x, int incx) {
        var x_ = MemorySegment.ofArray(x);
        return cblas_dasum(n, x_, incx);
    }

    /**
     * Sums the absolute values of the elements of a vector.
     * When working backward ({@code incx < 0}), each routine starts at the end of the
     * vector and moves backward.
     *
     * @param n Number of vector elements to be summed.
     *
     * @param x Array of dimension {@code (n-1) * abs(incx)+ 1}.
     *          Vector that contains elements to be summed.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @return Sum of the absolute values of the elements of the vector x.
     *         If {@code n <= 0}, DASUM is set to 0.
     */
    static float asum(int n, float[] x, int incx) {
        var x_ = MemorySegment.ofArray(x);
        return cblas_sasum(n, x_, incx);
    }

    /**
     * Sums the absolute values of the elements of a vector.
     *
     * @param x Vector that contains elements to be summed.
     *
     * @return Sum of the absolute values of the elements of the vector x.
     */
    default double asum(double[] x) {
        return asum(x.length, x, 1);
    }

    /**
     * Sums the absolute values of the elements of a vector.
     *
     * @param x Vector that contains elements to be summed.
     *
     * @return Sum of the absolute values of the elements of the vector x.
     */
    default float asum(float[] x) {
        return asum(x.length, x, 1);
    }

    /**
     * Computes a constant alpha times a vector x plus a vector y.
     * The result overwrites the initial values of vector y.
     * incx and incy specify the increment between two consecutive
     * elements of respectively vector x and y. When working backward
     * ({@code incx < 0 or incy < 0}), each  routine  starts  at the end of the
     * vector and moves backward.
     * <p>
     * When  {@code n <= 0}, or {@code alpha = 0.}, this routine returns immediately
     * with no change in its arguments.
     *
     * @param n Number of elements in the vectors. If {@code n <= 0}, these routines
     *          return without any computation.
     *
     * @param alpha If {@code alpha = 0} this routine returns without any computation.
     *
     * @param x Input array of dimension {@code (n-1) * |incx| + 1}.  Contains the
     *          vector to be scaled before summation.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @param y Input and output array of dimension {@code (n-1) * |incy| + 1}.
     *          Before calling the routine, y contains the vector to be summed.
     *          After the routine ends, y contains the result of the summation.
     *
     * @param incy Increment between elements of y.
     *             If {@code incy = 0}, the results will be unpredictable.
     */
    static void axpy(int n, double alpha, double[] x, int incx, double[] y, int incy) {
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_daxpy(n, alpha, x_, incx, y_, incy);
    }

    /**
     * Computes a constant alpha times a vector x plus a vector y.
     * The result overwrites the initial values of vector y.
     * incx and incy specify the increment between two consecutive
     * elements of respectively vector x and y. When working backward
     * ({@code incx < 0 or incy < 0}), each  routine  starts  at the end of the
     * vector and moves backward.
     * <p>
     * When {@code n <= 0, or alpha = 0.}, this routine returns immediately
     * with no change in its arguments.
     *
     * @param n Number of elements in the vectors. If {@code n <= 0}, these routines
     *          return without any computation.
     *
     * @param alpha If {@code alpha = 0} this routine returns without any computation.
     *
     * @param x Input array of dimension {@code (n-1) * |incx| + 1.}  Contains the
     *          vector to be scaled before summation.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @param y Input and output array of dimension {@code (n-1) * |incy| + 1}.
     *          Before calling the routine, y contains the vector to be summed.
     *          After the routine ends, y contains the result of the summation.
     *
     * @param incy Increment between elements of y.
     *             If {@code incy = 0}, the results will be unpredictable.
     */
    static void axpy(int n, float alpha, float[] x, int incx, float[] y, int incy) {
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_saxpy(n, alpha, x_, incx, y_, incy);
    }

    /**
     * Computes a constant alpha times a vector x plus a vector y.
     * The result overwrites the initial values of vector y.
     * <p>
     * When {@code alpha = 0.}, this routine returns immediately
     * with no change in its arguments.
     *
     * @param alpha If {@code alpha = 0} this routine returns without any computation.
     *
     * @param x The vector to be scaled before summation.
     *
     * @param y Input and output array.
     *          Before calling the routine, y contains the vector to be summed.
     *          After the routine ends, y contains the result of the summation.
     */
    default void axpy(double alpha, double[] x, double[] y) {
        axpy(x.length, alpha, x, 1, y, 1);
    }

    /**
     * Computes a constant alpha times a vector x plus a vector y.
     * The result overwrites the initial values of vector y.
     * <p>
     * When {@code alpha = 0.}, this routine returns immediately
     * with no change in its arguments.
     *
     * @param alpha If {@code alpha = 0} this routine returns without any computation.
     *
     * @param x The vector to be scaled before summation.
     *
     * @param y Input and output array.
     *          Before calling the routine, y contains the vector to be summed.
     *          After the routine ends, y contains the result of the summation.
     */
    default void axpy(float alpha, float[] x, float[] y) {
        axpy(x.length, alpha, x, 1, y, 1);
    }

    /**
     * Computes the dot product of two vectors.
     * incx and incy specify the increment between two consecutive
     * elements of respectively vector x and y. When working backward
     * ({@code incx < 0 or incy < 0}), each  routine  starts  at the end of the
     * vector and moves backward.
     *
     * @param n Number of elements in the vectors.
     *
     * @param x Input array of dimension {@code (n-1) * |incx| + 1}.
     *          Array x contains the first vector operand.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @param y Input array of dimension {@code (n-1) * |incy| + 1}.
     *          Array y contains the second vector operand.
     *
     * @param incy Increment between elements of y.
     *             If {@code incy = 0}, the results will be unpredictable.
     *
     * @return dot product. If {@code n <= 0}, return 0.
     */
    static double dot(int n, double[] x, int incx, double[] y, int incy) {
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        return cblas_ddot(n, x_, incx, y_, incy);
    }

    /**
     * Computes the dot product of two vectors.
     * incx and incy specify the increment between two consecutive
     * elements of respectively vector x and y. When working backward
     * ({@code incx < 0 or incy < 0}), each  routine  starts  at the end of the
     * vector and moves backward.
     *
     * @param n Number of elements in the vectors.
     *
     * @param x Input array of dimension {@code (n-1) * |incx| + 1}.
     *          Array x contains the first vector operand.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @param y Input array of dimension {@code (n-1) * |incy| + 1}.
     *          Array y contains the second vector operand.
     *
     * @param incy Increment between elements of y.
     *             If {@code incy = 0}, the results will be unpredictable.
     *
     * @return dot product. If {@code n <= 0}, return 0.
     */
    static float dot(int n, float[] x, int incx, float[] y, int incy) {
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        return cblas_sdot(n, x_, incx, y_, incy);
    }

    /**
     * Computes the dot product of two vectors.
     *
     * @param x Array x contains the first vector operand.
     *
     * @param y Array y contains the second vector operand.
     *
     * @return dot product. If {@code n <= 0}, return 0.
     */
    default double dot(double[] x, double[] y) {
        return dot(x.length, x, 1, y, 1);
    }

    /**
     * Computes the dot product of two vectors.
     *
     * @param x Array x contains the first vector operand.
     *
     * @param y Array y contains the second vector operand.
     *
     * @return dot product. If {@code n <= 0}, return 0.
     */
    default float dot(float[] x, float[] y) {
        return dot(x.length, x, 1, y, 1);
    }

    /**
     * Computes the Euclidean (L2) norm of a vector.
     *
     * @param n Number of elements in the vectors.
     *
     * @param x Input array of dimension {@code (n-1) * |incx| + 1}.
     *          Array x contains the vector operand.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @return Euclidean norm. If {@code n <= 0}, return 0.
     */
    static double nrm2(int n, double[] x, int incx) {
        var x_ = MemorySegment.ofArray(x);
        return cblas_dnrm2(n, x_, incx);
    }

    /**
     * Computes the Euclidean (L2) norm of a vector.
     *
     * @param n Number of elements in the vectors.
     *
     * @param x Input array of dimension {@code (n-1) * |incx| + 1}.
     *          Array x contains the vector operand.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @return Euclidean norm. If {@code n <= 0}, return 0.
     */
    static float nrm2(int n, float[] x, int incx) {
        var x_ = MemorySegment.ofArray(x);
        return cblas_snrm2(n, x_, incx);
    }

    /**
     * Computes the Euclidean (L2) norm of a vector.
     *
     * @param x Array x contains the vector operand.
     *
     * @return Euclidean norm.
     */
    default double nrm2(double[] x) {
        return nrm2(x.length, x, 1);
    }

    /**
     * Computes the Euclidean (L2) norm of a vector.
     *
     * @param x Array x contains the vector operand.
     *
     * @return Euclidean norm.
     */
    default float nrm2(float[] x) {
       return nrm2(x.length, x, 1);
    }

    /**
     * Scales a vector with a scalar.
     *
     * @param n Number of elements in the vectors.
     *
     * @param alpha The scaling factor.
     *
     * @param x Input and output array of dimension {@code (n-1) * |incx| + 1}.
     *          Vector to be scaled.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     */
    static void scal(int n, double alpha, double[] x, int incx) {
        var x_ = MemorySegment.ofArray(x);
        cblas_dscal(n, alpha, x_, incx);
    }

    /**
     * Scales a vector with a scalar.
     *
     * @param n Number of elements in the vectors.
     *
     * @param alpha The scaling factor.
     *
     * @param x Input and output array of dimension {@code (n-1) * |incx| + 1}.
     *          Vector to be scaled.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     */
    static void scal(int n, float alpha, float[] x, int incx) {
        var x_ = MemorySegment.ofArray(x);
        cblas_sscal(n, alpha, x_, incx);
    }

    /**
     * Scales a vector with a scalar.
     *
     * @param alpha The scaling factor.
     *
     * @param x Input and output vector to be scaled.
     */
    default void scal(double alpha, double[] x) {
        scal(x.length, alpha, x, 1);
    }

    /**
     * Scales a vector with a scalar.
     *
     * @param alpha The scaling factor.
     *
     * @param x Input and output vector to be scaled.
     */
    default void scal(float alpha, float[] x) {
        scal(x.length, alpha, x, 1);
    }

    /**
     * Swaps two vectors.
     * incx and incy specify the increment between two consecutive
     * elements of respectively vector x and y. When working backward
     * ({@code incx < 0 or incy < 0}), each  routine  starts  at the end of the
     * vector and moves backward.
     *
     * @param n Number of elements in the vectors.
     *
     * @param x Input and output array of dimension {@code (n-1) * |incx| + 1}.
     *          Vector to be swapped.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @param y Input and output array of dimension {@code (n-1) * |incy| + 1}.
     *          Vector to be swapped.
     *
     * @param incy Increment between elements of y.
     *             If {@code incy = 0}, the results will be unpredictable.
     */
    static void swap(int n, double[] x, int incx, double[] y, int incy) {
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_dswap(n, x_, incx, y_, incy);
    }

    /**
     * Swaps two vectors.
     * incx and incy specify the increment between two consecutive
     * elements of respectively vector x and y. When working backward
     * ({@code incx < 0 or incy < 0}), each  routine  starts  at the end of the
     * vector and moves backward.
     *
     * @param n Number of elements in the vectors.
     *
     * @param x Input and output array of dimension {@code (n-1) * |incx| + 1}.
     *          Vector to be swapped.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @param y Input and output array of dimension {@code (n-1) * |incy| + 1}.
     *          Vector to be swapped.
     *
     * @param incy Increment between elements of y.
     *             If {@code incy = 0}, the results will be unpredictable.
     */
    static void swap(int n, float[] x, int incx, float[] y, int incy) {
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_sswap(n, x_, incx, y_, incy);
    }

    /**
     * Swaps two vectors.
     *
     * @param x Input and output vector to be swapped.
     *
     * @param y Input and output vector to be swapped.
     */
    default void swap(double[] x, double[] y) {
        swap(x.length, x, 1, y, 1);
    }

    /**
     * Swaps two vectors.
     *
     * @param x Input and output vector to be swapped.
     *
     * @param y Input and output vector to be swapped.
     */
    default void swap(float[] x, float[] y) {
        swap(x.length, x, 1, y, 1);
    }

    /**
     * Searches a vector for the first occurrence of the maximum absolute
     * value.
     *
     * @param n Number of elements in the vectors.
     *
     * @param x Input array of dimension {@code (n-1) * |incx| + 1}.
     *          Vector to be searched.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @return The first index of the maximum absolute value of vector x.
     *         If {@code n <= 0}, return 0.
     */
    static long iamax(int n, double[] x, int incx) {
        var x_ = MemorySegment.ofArray(x);
        return cblas_idamax(n, x_, incx);
    }

    /**
     * Searches a vector for the first occurrence of the maximum absolute
     * value.
     *
     * @param n Number of elements in the vectors.
     *
     * @param x Input array of dimension {@code (n-1) * |incx| + 1}.
     *          Vector to be searched.
     *
     * @param incx Increment between elements of x.
     *             If {@code incx = 0}, the results will be unpredictable.
     *
     * @return The first index of the maximum absolute value of vector x.
     *         If {@code n <= 0}, return 0.
     */
    static long iamax(int n, float[] x, int incx) {
        var x_ = MemorySegment.ofArray(x);
        return cblas_isamax(n, x_, incx);
    }

    /**
     * Searches a vector for the first occurrence of the maximum absolute
     * value.
     *
     * @param x Vector to be searched.
     *
     * @return The first index of the maximum absolute value of vector x.
     */
    default long iamax(double[] x) {
        return iamax(x.length, x, 1);
    }

    /**
     * Searches a vector for the first occurrence of the maximum absolute
     * value.
     *
     * @param x Vector to be searched.
     *
     * @return The first index of the maximum absolute value of vector x.
     */
    default long iamax(float[] x) {
        return iamax(x.length, x, 1);
    }

    /**
     * Performs the matrix-vector operation.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least {@code max(1, m)}. The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *           when {@code trans = 'N' or 'n'} and
     *           at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gemv(Layout layout, Transpose trans, int m, int n,
                     double alpha, double[] A, int lda, double[] x, int incx,
                     double beta, double[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_dgemv(layout.blas(), trans.blas(), m, n, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *           when {@code trans = 'N' or 'n'} and
     *           at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gemv(Layout layout, Transpose trans, int m, int n,
                     double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx,
                     double beta, DoubleBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_dgemv(layout.blas(), trans.blas(), m, n, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *           when {@code trans = 'N' or 'n'} and
     *           at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gemv(Layout layout, Transpose trans, int m, int n,
                     double alpha, MemorySegment A, int lda, MemorySegment x, int incx,
                     double beta, MemorySegment y, int incy) {
        cblas_dgemv(layout.blas(), trans.blas(), m, n, alpha, A, lda, x, incx, beta, y, incy);
    }

    /**
     * Performs the matrix-vector operation.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1)*abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *           when {@code trans = 'N' or 'n'} and
     *           at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gemv(Layout layout, Transpose trans, int m, int n,
                     float alpha, float[] A, int lda, float[] x, int incx,
                     float beta, float[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_sgemv(layout.blas(), trans.blas(), m, n, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *           when {@code trans = 'N' or 'n'} and
     *           at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gemv(Layout layout, Transpose trans, int m, int n,
                     float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx,
                     float beta, FloatBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_sgemv(layout.blas(), trans.blas(), m, n, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void symv(Layout layout, UPLO uplo, int n, double alpha, double[] A, int lda,
                     double[] x, int incx, double beta, double[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_dsymv(layout.blas(), uplo.blas(), n, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void symv(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer A, int lda,
                     DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_dsymv(layout.blas(), uplo.blas(), n, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void symv(Layout layout, UPLO uplo, int n, double alpha, MemorySegment A, int lda,
                     MemorySegment x, int incx, double beta, MemorySegment y, int incy) {
        cblas_dsymv(layout.blas(), uplo.blas(), n, alpha, A, lda, x, incx, beta, y, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void symv(Layout layout, UPLO uplo, int n, float alpha, float[] A, int lda,
                     float[] x, int incx, float beta, float[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_ssymv(layout.blas(), uplo.blas(), n, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void symv(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer A, int lda,
                     FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_ssymv(layout.blas(), uplo.blas(), n, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric packed matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric packed matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void spmv(Layout layout, UPLO uplo, int n, double alpha, double[] A,
                     double[] x, int incx, double beta, double[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_dspmv(layout.blas(), uplo.blas(), n, alpha, A_, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric packed matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric packed matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void spmv(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer A,
                     DoubleBuffer x, int incx, double beta, DoubleBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_dspmv(layout.blas(), uplo.blas(), n, alpha, A_, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric packed matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric packed matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void spmv(Layout layout, UPLO uplo, int n, float alpha, float[] A,
                     float[] x, int incx, float beta, float[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_sspmv(layout.blas(), uplo.blas(), n, alpha, A_, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric packed matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric packed matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void spmv(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer A,
                     FloatBuffer x, int incx, float beta, FloatBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_sspmv(layout.blas(), uplo.blas(), n, alpha, A_, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a triangular matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     x := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, double[] A, int lda, double[] x, int incx) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        cblas_dtrmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A_, lda, x_, incx);
    }

    /**
     * Performs the matrix-vector operation using a triangular matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     x := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, DoubleBuffer A, int lda, DoubleBuffer x, int incx) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        cblas_dtrmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A_, lda, x_, incx);
    }

    /**
     * Performs the matrix-vector operation using a triangular matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     x := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, MemorySegment A, int lda, MemorySegment x, int incx) {
        cblas_dtrmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A, lda, x, incx);
    }

    /**
     * Performs the matrix-vector operation using a triangular matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     x := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, float[] A, int lda, float[] x, int incx) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        cblas_strmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A_, lda, x_, incx);
    }

    /**
     * Performs the matrix-vector operation using a triangular matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     x := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void trmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, FloatBuffer A, int lda, FloatBuffer x, int incx) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        cblas_strmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A_, lda, x_, incx);
    }

    /**
     * Performs the matrix-vector operation using a triangular packed matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     y := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric packed matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, double[] A, double[] x, int incx) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        cblas_dtpmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A_, x_, incx);
    }

    /**
     * Performs the matrix-vector operation using a triangular packed matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     y := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric packed matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, DoubleBuffer A, DoubleBuffer x, int incx) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        cblas_dtpmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A_, x_, incx);
    }

    /**
     * Performs the matrix-vector operation using a triangular packed matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     x := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric packed matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, float[] A, float[] x, int incx) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        cblas_stpmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A_, x_, incx);
    }

    /**
     * Performs the matrix-vector operation using a triangular packed matrix.
     * <pre>{@code
     *     x := A*x
     * }</pre>
     * or
     * <pre>{@code
     *     x := A'*x
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param diag unit diagonal or not.
     * @param n the number of rows/columns of the triangular matrix A.
     * @param A the symmetric packed matrix.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     */
    static void tpmv(Layout layout, UPLO uplo, Transpose trans, Diag diag,
                     int n, FloatBuffer A, FloatBuffer x, int incx) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        cblas_stpmv(layout.blas(), uplo.blas(), trans.blas(), diag.blas(), n, A_, x_, incx);
    }

    /**
     * Performs the matrix-vector operation using a band matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param kl the number of subdiagonal elements of band matrix.
     * @param ku the number of superdiagonal elements of band matrix.
     * @param alpha the scalar alpha.
     * @param A the band matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku,
                     double alpha, double[] A, int lda, double[] x, int incx,
                     double beta, double[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_dgbmv(layout.blas(), trans.blas(), m, n, kl, ku, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a band matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param kl the number of subdiagonal elements of band matrix.
     * @param ku the number of superdiagonal elements of band matrix.
     * @param alpha the scalar alpha.
     * @param A the band matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least (1 + (n - 1) * abs(incx))
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku,
                     double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx,
                     double beta, DoubleBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_dgbmv(layout.blas(), trans.blas(), m, n, kl, ku, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a band matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param kl the number of subdiagonal elements of band matrix.
     * @param ku the number of superdiagonal elements of band matrix.
     * @param alpha the scalar alpha.
     * @param A the band matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least (1 + (n - 1) * abs(incx))
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku,
                     float alpha, float[] A, int lda, float[] x, int incx,
                     float beta, float[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_sgbmv(layout.blas(), trans.blas(), m, n, kl, ku, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a band matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param kl the number of subdiagonal elements of band matrix.
     * @param ku the number of superdiagonal elements of band matrix.
     * @param alpha the scalar alpha.
     * @param A the band matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least (1 + (n - 1) * abs(incx))
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (m - 1) * abs(incx))} otherwise.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (m - 1) * abs(incy))}
     *          when {@code trans = 'N' or 'n'} and
     *          at least {@code (1 + (n - 1) * abs(incy))} otherwise.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void gbmv(Layout layout, Transpose trans, int m, int n, int kl, int ku,
                     float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx,
                     float beta, FloatBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_sgbmv(layout.blas(), trans.blas(), m, n, kl, ku, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric band matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric band matrix A.
     * @param k the number of subdiagonal/superdiagonal elements of the symmetric band matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric band matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))},
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))},
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void sbmv(Layout layout, UPLO uplo, int n, int k,
                     double alpha, double[] A, int lda, double[] x, int incx,
                     double beta, double[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_dsbmv(layout.blas(), uplo.blas(), n, k, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric band matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of rows/columns of the symmetric band matrix A.
     * @param k the number of subdiagonal/superdiagonal elements of the symmetric band matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric band matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))},
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))},
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void sbmv(Layout layout, UPLO uplo, int n, int k,
                     double alpha, DoubleBuffer A, int lda, DoubleBuffer x, int incx,
                     double beta, DoubleBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_dsbmv(layout.blas(), uplo.blas(), n, k, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric band matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the symmetric band matrix A.
     * @param k the number of subdiagonal/superdiagonal elements of the symmetric band matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric band matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void sbmv(Layout layout, UPLO uplo, int n, int k,
                     float alpha, float[] A, int lda, float[] x, int incx,
                     float beta, float[] y, int incy) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_ssbmv(layout.blas(), uplo.blas(), n, k, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the matrix-vector operation using a symmetric band matrix.
     * <pre>{@code
     *     y := alpha*A*x + beta*y
     * }</pre>
     * or
     * <pre>{@code
     *     y := alpha*A'*x + beta*y
     * }</pre>
     * where alpha and beta are scalars, x and y are vectors and A is an m by
     * n matrix.
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the symmetric band matrix A.
     * @param k the number of subdiagonal/superdiagonal elements of the symmetric band matrix A.
     * @param alpha the scalar alpha.
     * @param A the symmetric band matrix.
     * @param lda the leading dimension of A as declared in the caller.
     * @param x array of dimension at least {@code (1 + (n - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     */
    static void sbmv(Layout layout, UPLO uplo, int n, int k,
                     float alpha, FloatBuffer A, int lda, FloatBuffer x, int incx,
                     float beta, FloatBuffer y, int incy) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_ssbmv(layout.blas(), uplo.blas(), n, k, alpha, A_, lda, x_, incx, beta, y_, incy);
    }

    /**
     * Performs the rank-1 update operation.
     * <pre>{@code
     *     A := A + alpha*x*y'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void ger(Layout layout, int m, int n, double alpha, double[] x, int incx,
                    double[] y, int incy, double[] A, int lda) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_dger(layout.blas(), m, n, alpha, x_, incx, y_, incy, A_, lda);
    }

    /**
     * Performs the rank-1 update operation.
     * <pre>{@code
     *     A := A + alpha*x*y'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void ger(Layout layout, int m, int n, double alpha, DoubleBuffer x, int incx,
                    DoubleBuffer y, int incy, DoubleBuffer A, int lda) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_dger(layout.blas(), m, n, alpha, x_, incx, y_, incy, A_, lda);
    }

    /**
     * Performs the rank-1 update operation.
     * <pre>{@code
     *     A := A + alpha*x*y'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void ger(Layout layout, int m, int n, double alpha, MemorySegment x, int incx,
                    MemorySegment y, int incy, MemorySegment A, int lda) {
        cblas_dger(layout.blas(), m, n, alpha, x, incx, y, incy, A, lda);
    }

    /**
     * Performs the rank-1 update operation.
     * <pre>{@code
     *     A := A + alpha*x*y'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void ger(Layout layout, int m, int n, float alpha, float[] x, int incx,
                    float[] y, int incy, float[] A, int lda) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        cblas_sger(layout.blas(), m, n, alpha, x_, incx, y_, incy, A_, lda);
    }

    /**
     * Performs the rank-1 update operation.
     * <pre>{@code
     *     A := A + alpha*x*y'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param m the number of rows of the matrix A.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param y  array of dimension at least {@code (1 + (n - 1) * abs(incy))}.
     * @param incy the increment for the elements of y, which must not be zero.
     * @param A the leading m by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void ger(Layout layout, int m, int n, float alpha, FloatBuffer x, int incx,
                    FloatBuffer y, int incy, FloatBuffer A, int lda) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        cblas_sger(layout.blas(), m, n, alpha, x_, incx, y_, incy, A_, lda);
    }

    /**
     * Performs the rank-1 update operation to symmetric matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param A the leading n by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void syr(Layout layout, UPLO uplo, int n, double alpha, double[] x, int incx, double[] A, int lda) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        cblas_dsyr(layout.blas(), uplo.blas(), n, alpha, x_, incx, A_, lda);
    }

    /**
     * Performs the rank-1 update operation to symmetric matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param A the leading n by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void syr(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer x, int incx, DoubleBuffer A, int lda) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        cblas_dsyr(layout.blas(), uplo.blas(), n, alpha, x_, incx, A_, lda);
    }

    /**
     * Performs the rank-1 update operation to symmetric matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param A the leading n by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void syr(Layout layout, UPLO uplo, int n, double alpha, MemorySegment x, int incx, MemorySegment A, int lda) {
        cblas_dsyr(layout.blas(), uplo.blas(), n, alpha, x, incx, A, lda);
    }

    /**
     * Performs the rank-1 update operation to symmetric matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     *          the matrix of coefficients.
     * @param A the leading n by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void syr(Layout layout, UPLO uplo, int n, float alpha, float[] x, int incx, float[] A, int lda) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        cblas_ssyr(layout.blas(), uplo.blas(), n, alpha, x_, incx, A_, lda);
    }

    /**
     * Performs the rank-1 update operation to symmetric matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     *          the matrix of coefficients.
     * @param A the leading n by n part of the array A must contain
     *          the matrix of coefficients.
     * @param lda the leading dimension of A as declared in the caller.
     *            LDA must be at least max(1, m). The leading dimension
     *            parameter allows use of BLAS/LAPACK routines on a submatrix
     *            of a larger matrix.
     */
    static void syr(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer x, int incx, FloatBuffer A, int lda) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        cblas_ssyr(layout.blas(), uplo.blas(), n, alpha, x_, incx, A_, lda);
    }

    /**
     * Performs the rank-1 update operation to symmetric packed matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param A the symmetric packed matrix.
     */
    static void spr(Layout layout, UPLO uplo, int n, double alpha, double[] x, int incx, double[] A) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        cblas_dspr(layout.blas(), uplo.blas(), n, alpha, x_, incx, A_);
    }
    /**
     * Performs the rank-1 update operation to symmetric packed matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param A the symmetric packed matrix.
     */
    static void spr(Layout layout, UPLO uplo, int n, double alpha, DoubleBuffer x, int incx, DoubleBuffer A) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        cblas_dspr(layout.blas(), uplo.blas(), n, alpha, x_, incx, A_);
    }

    /**
     * Performs the rank-1 update operation to symmetric packed matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param A the symmetric packed matrix.
     */
    static void spr(Layout layout, UPLO uplo, int n, float alpha, float[] x, int incx, float[] A) {
        var A_ = MemorySegment.ofArray(A);
        var x_ = MemorySegment.ofArray(x);
        cblas_sspr(layout.blas(), uplo.blas(), n, alpha, x_, incx, A_);
    }


    /**
     * Performs the rank-1 update operation to symmetric packed matrix.
     * <pre>{@code
     *     A := A + alpha*x*x'
     * }</pre>
     *
     * @param layout matrix layout.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param n the number of columns of the matrix A.
     * @param alpha the scalar alpha.
     * @param x array of dimension at least {@code (1 + (m - 1) * abs(incx))}.
     * @param incx the increment for the elements of x, which must not be zero.
     * @param A the symmetric packed matrix.
     */
    static void spr(Layout layout, UPLO uplo, int n, float alpha, FloatBuffer x, int incx, FloatBuffer A) {
        var A_ = MemorySegment.ofBuffer(A);
        var x_ = MemorySegment.ofBuffer(x);
        cblas_sspr(layout.blas(), uplo.blas(), n, alpha, x_, incx, A_);
    }

    /**
     * Performs the matrix-matrix operation.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param k the number of columns of the matrix op(A).
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k,
                     double alpha, double[] A, int lda, double[] B, int ldb,
                     double beta, double[] C, int ldc) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var C_ = MemorySegment.ofArray(C);
        cblas_dgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A_, lda, B_, ldb, beta, C_, ldc);
    }

    /**
     * Performs the matrix-matrix operation.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param k the number of columns of the matrix op(A).
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k,
                     double alpha, DoubleBuffer A, int lda, DoubleBuffer B, int ldb,
                     double beta, DoubleBuffer C, int ldc) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var C_ = MemorySegment.ofBuffer(C);
        cblas_dgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A_, lda, B_, ldb, beta, C_, ldc);
    }

    /**
     * Performs the matrix-matrix operation.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param k the number of columns of the matrix op(A).
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k,
                     double alpha, MemorySegment A, int lda, MemorySegment B, int ldb,
                     double beta, MemorySegment C, int ldc) {
        cblas_dgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    /**
     * Performs the matrix-matrix operation.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param k the number of columns of the matrix op(A).
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k,
                     float alpha, float[] A, int lda, float[] B, int ldb,
                     float beta, float[] C, int ldc) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var C_ = MemorySegment.ofArray(C);
        cblas_sgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A_, lda, B_, ldb, beta, C_, ldc);
    }

    /**
     * Performs the matrix-matrix operation.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param k the number of columns of the matrix op(A).
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void gemm(Layout layout, Transpose transA, Transpose transB, int m, int n, int k,
                     float alpha, FloatBuffer A, int lda, FloatBuffer B, int ldb,
                     float beta, FloatBuffer C, int ldc) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var C_ = MemorySegment.ofBuffer(C);
        cblas_sgemm(layout.blas(), transA.blas(), transB.blas(), m, n, k, alpha, A_, lda, B_, ldb, beta, C_, ldc);
    }

    /**
     * Performs the matrix-matrix operation where the matrix A is symmetric.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     * or
     * <pre>{@code
     *     C := alpha*B*A + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param side  {@code C := alpha*A*B + beta*C} if side is left or
     *              {@code C := alpha*B*A + beta*C} if side is right.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void symm(Layout layout, Side side, UPLO uplo, int m, int n,
                     double alpha, double[] A, int lda, double[] B, int ldb,
                     double beta, double[] C, int ldc) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var C_ = MemorySegment.ofArray(C);
        cblas_dsymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A_, lda, B_, ldb, beta, C_, ldc);
    }

    /**
     * Performs the matrix-matrix operation where the matrix A is symmetric.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     * or
     * <pre>{@code
     *     C := alpha*B*A + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param side  {@code C := alpha*A*B + beta*C} if side is left or
     *              {@code C := alpha*B*A + beta*C} if side is right.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void symm(Layout layout, Side side, UPLO uplo, int m, int n,
                     double alpha, DoubleBuffer A, int lda, DoubleBuffer B, int ldb,
                     double beta, DoubleBuffer C, int ldc) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var C_ = MemorySegment.ofBuffer(C);
        cblas_dsymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A_, lda, B_, ldb, beta, C_, ldc);
    }

    /**
     * Performs the matrix-matrix operation where the matrix A is symmetric.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     * or
     * <pre>{@code
     *     C := alpha*B*A + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param side  {@code C := alpha*A*B + beta*C} if side is left or
     *              {@code C := alpha*B*A + beta*C} if side is right.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void symm(Layout layout, Side side, UPLO uplo, int m, int n,
                     double alpha, MemorySegment A, int lda, MemorySegment B, int ldb,
                     double beta, MemorySegment C, int ldc) {
        cblas_dsymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A, lda, B, ldb, beta, C, ldc);
    }

    /**
     * Performs the matrix-matrix operation where one input matrix is symmetric.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param side  {@code C := alpha*A*B + beta*C} if side is left or
     *              {@code C := alpha*B*A + beta*C} if side is right.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void symm(Layout layout, Side side, UPLO uplo, int m, int n,
                     float alpha, float[] A, int lda, float[] B, int ldb,
                     float beta, float[] C, int ldc) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var C_ = MemorySegment.ofArray(C);
        cblas_ssymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A_, lda, B_, ldb, beta, C_, ldc);
    }

    /**
     * Performs the matrix-matrix operation where one input matrix is symmetric.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     *
     * @param layout matrix layout.
     * @param side  {@code C := alpha*A*B + beta*C} if side is left or
     *              {@code C := alpha*B*A + beta*C} if side is right.
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     * @param m the number of rows of the matrix C.
     * @param n the number of columns of the matrix C.
     * @param alpha the scalar alpha.
     * @param A the matrix A.
     * @param lda the leading dimension of A as declared in the caller.
     * @param B the matrix B.
     * @param ldb the leading dimension of B as declared in the caller.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param C the matrix C.
     * @param ldc the leading dimension of C as declared in the caller.
     */
    static void symm(Layout layout, Side side, UPLO uplo, int m, int n,
                     float alpha, FloatBuffer A, int lda, FloatBuffer B, int ldb,
                     float beta, FloatBuffer C, int ldc) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var C_ = MemorySegment.ofBuffer(C);
        cblas_ssymm(layout.blas(), side.blas(), uplo.blas(), m, n, alpha, A_, lda, B_, ldb, beta, C_, ldc);
    }
}
