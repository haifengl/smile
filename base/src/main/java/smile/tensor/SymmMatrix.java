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

import java.io.Serializable;
import java.lang.foreign.MemorySegment;
import smile.math.MathEx;
import smile.linalg.*;
import static smile.linalg.Order.*;
import static smile.linalg.blas.cblas_h.*;
import static smile.tensor.ScalarType.*;

/**
 * The symmetric matrix in packed storage.
 *
 * @author Haifeng Li
 */
public abstract class SymmMatrix implements Matrix, Serializable {
    /**
     * The memory segment that stores matrix values.
     */
    transient MemorySegment memory;
    /**
     * The number of rows/columns.
     */
    final int n;
    /**
     * The upper or lower triangle of the symmetric matrix.
     */
    final UPLO uplo;

    /**
     * Default constructor for readObject.
     */
    SymmMatrix() {
        this.memory = null;
        this.n = 0;
        this.uplo = null;
    }

    /**
     * Constructor.
     * @param memory the memory segment of data.
     * @param uplo the symmetric matrix stores the upper or lower triangle.
     * @param n the dimension of matrix.
     */
    SymmMatrix(MemorySegment memory, UPLO uplo, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException(String.format("Invalid matrix size: %d x %d", n, n));
        }

        this.memory = memory;
        this.uplo = uplo;
        this.n = n;
    }

    /**
     * Returns a zero matrix.
     * @param uplo the symmetric matrix stores the upper or lower triangle.
     * @param n the dimension of matrix.
     * @return the packed symmetric matrix.
     */
    public static SymmMatrix zeros(ScalarType scalarType, UPLO uplo, int n) {
        if (uplo == null) {
            throw new IllegalArgumentException("UPLO is null");
        }

        return switch (scalarType) {
            case Float64 -> {
                double[] AP = new double[n * (n+1) / 2];
                yield new SymmMatrix64(uplo, n, AP);
            }
            case Float32 -> {
                float[] AP = new float[n * (n+1) / 2];
                yield new SymmMatrix32(uplo, n, AP);
            }
            default -> throw new UnsupportedOperationException("Unsupported ScalarType: " + scalarType);
        };
    }

    /**
     * Returns a symmetric matrix from a dense matrix.
     * @param A the dense symmetric matrix.
     * @return the packed symmetric matrix.
     */
    public static SymmMatrix of(DenseMatrix A) {
        if (!A.isSymmetric()) {
            throw new IllegalArgumentException("The input matrix is not symmetric");
        }

        int n = A.ncol();
        UPLO uplo = A.uplo();
        SymmMatrix matrix = zeros(A.scalarType(), uplo, n);
        switch (uplo) {
            case LOWER -> {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j <= i; j++) {
                        matrix.set(i, j, A.get(i, j));
                    }
                }
            }
            case UPPER -> {
                for (int i =  0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        matrix.set(i, j, A.get(i, j));
                    }
                }
            }
        }
        return matrix;
    }

    /**
     * Returns a symmetric matrix from a two-dimensional array.
     * @param uplo the symmetric matrix stores the upper or lower triangle.
     * @param AP the symmetric matrix.
     * @return the packed symmetric matrix.
     */
    public static SymmMatrix of(UPLO uplo, double[][] AP) {
        int n = AP.length;
        SymmMatrix matrix = zeros(Float64, uplo, n);
        switch (uplo) {
            case LOWER -> {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j <= i; j++) {
                        matrix.set(i, j, AP[i][j]);
                    }
                }
            }
            case UPPER -> {
                for (int i =  0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        matrix.set(i, j, AP[i][j]);
                    }
                }
            }
        }
        return matrix;
    }

    /**
     * Returns a symmetric matrix from a two-dimensional array.
     * @param uplo the symmetric matrix stores the upper or lower triangle.
     * @param AP the symmetric matrix.
     * @return the packed symmetric matrix.
     */
    public static SymmMatrix of(UPLO uplo, float[][] AP) {
        int n = AP.length;
        SymmMatrix matrix = zeros(Float32, uplo, n);
        switch (uplo) {
            case LOWER -> {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j <= i; j++) {
                        matrix.set(i, j, AP[i][j]);
                    }
                }
            }
            case UPPER -> {
                for (int i =  0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        matrix.set(i, j, AP[i][j]);
                    }
                }
            }
        }
        return matrix;
    }

    @Override
    public int nrow() {
        return n;
    }

    @Override
    public int ncol() {
        return n;
    }

    @Override
    public SymmMatrix scale(double alpha) {
        switch(scalarType()) {
            case Float64 -> cblas_dscal((int) length(), alpha, memory, 1);
            case Float32 -> cblas_sscal((int) length(), (float) alpha, memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        }
        return this;
    }

    @Override
    public SymmMatrix transpose() {
        return this;
    }

    /**
     * Returns the matrix layout.
     * @return the matrix layout.
     */
    public Order order() {
        return COL_MAJOR;
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
        if (!(o instanceof SymmMatrix)) {
            return false;
        }

        return equals((SymmMatrix) o, 1E-10);
    }

    /**
     * Returns true if two matrices equal in given precision.
     *
     * @param o the other matrix.
     * @param epsilon a number close to zero.
     * @return true if two matrices equal in given precision.
     */
    public boolean equals(SymmMatrix o, double epsilon) {
        if (n != o.n) {
            return false;
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if (!MathEx.isZero(get(i, j) - o.get(i, j), epsilon)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void mv(Transpose trans, double alpha, Vector x, double beta, Vector y) {
        switch(scalarType()) {
            case Float64 -> cblas_dspmv(order().blas(), uplo.blas(), n, alpha, memory, x.memory, 1, beta, y.memory, 1);
            case Float32 -> cblas_sspmv(order().blas(), uplo.blas(), n, (float) alpha, memory, x.memory, 1, (float) beta, y.memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        }
    }
}
