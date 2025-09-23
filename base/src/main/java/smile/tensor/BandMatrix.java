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
package smile.tensor;

import java.lang.foreign.MemorySegment;
import smile.math.MathEx;
import smile.linalg.*;
import static smile.linalg.Layout.*;
import static smile.linalg.blas.cblas_openblas_h.*;

/**
 * A band matrix is a sparse matrix, whose non-zero entries are confined to
 * a diagonal band, comprising the main diagonal and zero or more diagonals
 * on either side.
 * <p>
 * In numerical analysis, matrices from finite element or finite difference
 * problems are often banded. Such matrices can be viewed as descriptions
 * of the coupling between the problem variables; the bandedness corresponds
 * to the fact that variables are not coupled over arbitrarily large distances.
 * Such matrices can be further divided - for instance, banded matrices exist
 * where every element in the band is nonzero. These often arise when
 * discretizing one-dimensional problems. Problems in higher dimensions also
 * lead to banded matrices, in which case the band itself also tends to be sparse.
 * For instance, a partial differential equation on a square domain (using
 * central differences) will yield a matrix with a half-bandwidth equal to the
 * square root of the matrix dimension, but inside the band only 5 diagonals are
 * nonzero. Unfortunately, applying Gaussian elimination (or equivalently an
 * LU decomposition) to such a matrix results in the band being filled in by
 * many non-zero elements. As sparse matrices lend themselves to more efficient
 * computation than dense matrices, there has been much research focused on
 * finding ways to minimize the bandwidth (or directly minimize the fill in)
 * by applying permutations to the matrix, or other such equivalence or
 * similarity transformations.
 * <p>
 * From a computational point of view, working with band matrices is always
 * preferential to working with similarly dimensioned dense square matrices.
 * A band matrix can be likened in complexity to a rectangular matrix whose
 * row dimension is equal to the bandwidth of the band matrix. Thus, the work
 * involved in performing operations such as multiplication falls significantly,
 * often leading to huge savings in terms of calculation time and complexity.
 * <p>
 * Given an n-by-n band matrix with m<sub>1</sub> rows below the diagonal
 * and m<sub>2</sub> rows above. The matrix is compactly stored in an array
 * A[0,n-1][0,m<sub>1</sub>+m<sub>2</sub>]. The diagonal elements are in
 * A[0,n-1][m<sub>1</sub>]. The subdiagonal elements are in A[j,n-1][0,m<sub>1</sub>-1]
 * with {@code j > 0} appropriate to the number of elements on each subdiagonal.
 * The superdiagonal elements are in A[0,j][m<sub>1</sub>+1,m<sub>2</sub>+m<sub>2</sub>]
 * with {@code j < n-1} appropriate to the number of elements on each superdiagonal.
 *
 * @author Haifeng Li
 */
public abstract class BandMatrix implements Matrix {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BandMatrix.class);

    /**
     * The memory segment that stores matrix values.
     */
    transient MemorySegment memory;
    /**
     * The number of rows.
     */
    final int m;
    /**
     * The number of columns.
     */
    final int n;
    /**
     * The number of subdiagonal rows.
     */
    final int kl;
    /**
     * The number of superdiagonal rows.
     */
    final int ku;
    /**
     * The leading dimension.
     */
    final int ld;
    /**
     * The upper or lower triangle of the symmetric band matrix.
     */
    UPLO uplo = null;

    /**
     * Constructor.
     * @param memory the memory segment of data.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param kl the number of subdiagonals.
     * @param ku the number of superdiagonals.
     */
    BandMatrix(MemorySegment memory, int m, int n, int kl, int ku) {
        if (m <= 0 || n <= 0) {
            throw new IllegalArgumentException(String.format("Invalid matrix size: %d x %d", m, n));
        }

        if (kl < 0 || ku < 0) {
            throw new IllegalArgumentException(String.format("Invalid subdiagonals or superdiagonals: kl = %d, ku = %d", kl, ku));
        }

        if (kl >= m) {
            throw new IllegalArgumentException(String.format("Invalid subdiagonals %d >= %d", kl, m));
        }

        if (ku >= n) {
            throw new IllegalArgumentException(String.format("Invalid superdiagonals %d >= %d", ku, n));
        }

        this.m = m;
        this.n = n;
        this.kl = kl;
        this.ku = ku;
        this.ld = kl + ku + 1;
    }

    /**
     * Returns a zero matrix.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param kl the number of subdiagonals.
     * @param ku the number of superdiagonals.
     */
    public static BandMatrix zeros(ScalarType scalarType, int m, int n, int kl, int ku) {
        int ld = kl + ku + 1;
        return switch (scalarType) {
            case Float64 -> {
                double[] AB = new double[ld * n];
                yield new BandMatrix64(m, n, kl, ku, AB);
            }
            case Float32 -> {
                float[] AB = new float[ld * n];
                yield new BandMatrix32(m, n, kl, ku, AB);
            }
            default -> throw new UnsupportedOperationException("Unsupported ScalarType: " + scalarType);
        };
    }

    /**
     * Returns a symmetric matrix from a two-dimensional array.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param kl the number of subdiagonals.
     * @param ku the number of superdiagonals.
     * @param ab the band matrix. A[i,j] is stored in {@code AB[ku+i-j, j]}
     *           for {@code max(0, j-ku) <= i <= min(m-1, j+kl)}.
     */
    public static BandMatrix of(int m, int n, int kl, int ku, double[][] ab) {
        int ld = kl + ku + 1;
        double[] AB = new double[ld * n];
        BandMatrix64 matrix = new BandMatrix64(m, n, kl, ku, AB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < ld; i++) {
                AB[j * ld + i] = ab[i][j];
            }
        }
        return matrix;
    }

    /**
     * Returns a symmetric matrix from a two-dimensional array.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param kl the number of subdiagonals.
     * @param ku the number of superdiagonals.
     * @param ab the band matrix. A[i,j] is stored in {@code AB[ku+i-j, j]}
     *           for {@code max(0, j-ku) <= i <= min(m-1, j+kl)}.
     */
    public static BandMatrix of(int m, int n, int kl, int ku, float[][] ab) {
        int ld = kl + ku + 1;
        float[] AB = new float[ld * n];
        BandMatrix32 matrix = new BandMatrix32(m, n, kl, ku, AB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < ld; i++) {
                AB[j * ld + i] = ab[i][j];
            }
        }
        return matrix;
    }

    @Override
    public int nrow() {
        return m;
    }

    @Override
    public int ncol() {
        return n;
    }

    /**
     * Returns the number of subdiagonals.
     * @return the number of subdiagonals.
     */
    public int kl() {
        return kl;
    }

    /**
     * Returns the number of superdiagonals.
     * @return the number of superdiagonals.
     */
    public int ku() {
        return ku;
    }

    /**
     * Returns the matrix layout.
     * @return the matrix layout.
     */
    public Layout layout() {
        return COL_MAJOR;
    }

    /**
     * Returns the leading dimension.
     * @return the leading dimension.
     */
    public int ld() {
        return ld;
    }

    /**
     * Return true if the matrix is symmetric (uplo != null).
     * @return true if the matrix is symmetric (uplo != null).
     */
    public boolean isSymmetric() {
        return m == n && kl == ku && uplo != null;
    }

    @Override
    public SymmMatrix transpose() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the format of symmetric band matrix.
     * @param uplo the format of symmetric band matrix.
     * @return this matrix.
     */
    public BandMatrix withUplo(UPLO uplo) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        if (kl != ku) {
            throw new IllegalArgumentException(String.format("kl != ku: %d != %d", kl, ku));
        }

        this.uplo = uplo;
        return this;
    }

    /**
     * Gets the format of packed matrix.
     * @return the format of packed matrix.
     */
    public UPLO uplo() {
        return uplo;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BandMatrix)) {
            return false;
        }

        return equals((BandMatrix) o, 1E-10);
    }

    /**
     * Returns true if two matrices equal in given precision.
     *
     * @param o the other matrix.
     * @param epsilon a number close to zero.
     * @return true if two matrices equal in given precision.
     */
    public boolean equals(BandMatrix o, double epsilon) {
        if (m != o.m || n != o.n) {
            return false;
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                if (!MathEx.isZero(get(i, j) - o.get(i, j), epsilon)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void mv(Transpose trans, double alpha, Vector x, double beta, Vector y) {
        if (uplo != null) {
            switch(scalarType()) {
                case Float64 -> cblas_dsbmv(layout().blas(), uplo.blas(), n, kl, alpha, memory, ld, x.memory, 1, beta, y.memory, 1);
                case Float32 -> cblas_ssbmv(layout().blas(), uplo.blas(), n, kl, (float) alpha, memory, ld, x.memory, 1, (float) beta, y.memory, 1);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }
        } else {
            switch(scalarType()) {
                case Float64 -> cblas_dgbmv(layout().blas(), trans.blas(), m, n, kl, ku, alpha, memory, ld, x.memory, 1, beta, y.memory, 1);
                case Float32 -> cblas_sgbmv(layout().blas(), trans.blas(), m, n, kl, ku, (float) alpha, memory, ld, x.memory, 1, (float) beta, y.memory, 1);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }
        }
    }

    @Override
    public Matrix mm(Matrix B) {
        throw new UnsupportedOperationException();
    }
}
