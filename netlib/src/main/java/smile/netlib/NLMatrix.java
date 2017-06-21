/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.netlib;

import smile.math.matrix.DenseMatrix;
import smile.math.matrix.ColumnMajorMatrix;
import com.github.fommil.netlib.BLAS;
import com.github.fommil.netlib.LAPACK;

/**
 * Column-major matrix that employs netlib for matrix-vector and matrix-matrix
 * computation.
 *
 * @author Haifeng Li
 */
public class NLMatrix extends ColumnMajorMatrix {
    private static String NoTranspose = "N";
    private static String Transpose   = "T";

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public NLMatrix(double[][] A) {
        super(A);
    }

    /**
     * Constructor of a square diagonal matrix with the elements of vector diag on the main diagonal.
     * @param A the array of diagonal elements.
     */
    public NLMatrix(double[] A) {
        super(A);
    }

    /**
     * Constructor of all-zero matrix.
     */
    public NLMatrix(int rows, int cols) {
        super(rows, cols);
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public NLMatrix(int rows, int cols, double value) {
        super(rows, cols, value);
    }

    /**
     * Constructor.
     * @param value the array of matrix values arranged in column major format
     */
    public NLMatrix(int rows, int cols, double[] value) {
        super(rows, cols, value);
    }

    public static NLMatrix eye(int n) {
        NLMatrix matrix = new NLMatrix(n, n);
        for (int i = 0; i < n; i++) {
            matrix.set(i, i, 1.0);
        }
        return matrix;
    }

    @Override
    public NLMatrix copy() {
        return new NLMatrix(nrows(), ncols(), data().clone());
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        BLAS.getInstance().dgemv(NoTranspose, nrows(), ncols(), 1.0, data(), nrows(), x, 1, 0.0, y, 1);
        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        BLAS.getInstance().dgemv(NoTranspose, nrows(), ncols(), 1.0, data(), nrows(), x, 1, 1.0, y, 1);
        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        BLAS.getInstance().dgemv(NoTranspose, nrows(), ncols(), 1.0, data(), nrows(), x, 1, b, y, 1);
        return y;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        BLAS.getInstance().dgemv(Transpose, nrows(), ncols(), 1.0, data(), nrows(), x, 1, 0.0, y, 1);
        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        BLAS.getInstance().dgemv(Transpose, nrows(), ncols(), 1.0, data(), nrows(), x, 1, 1.0, y, 1);
        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        BLAS.getInstance().dgemv(Transpose, nrows(), ncols(), 1.0, data(), nrows(), x, 1, b, y, 1);
        return y;
    }

    @Override
    public NLMatrix ata() {
        return atbmm(this);
    }

    @Override
    public NLMatrix aat() {
        return abtmm(this);
    }

    @Override
    public NLMatrix abmm(DenseMatrix B) {
        if (B instanceof ColumnMajorMatrix) {
            NLMatrix C = new NLMatrix(nrows(), B.ncols());
            BLAS.getInstance().dgemm(NoTranspose, NoTranspose,
                    nrows(), B.ncols(), ncols(), 1.0, data(), nrows(), ((ColumnMajorMatrix) B).data(),
                    B.nrows(), 1, C.data(), C.nrows());
            return C;
        }

        throw new IllegalArgumentException("NLMatrix.abmm() parameter must be ColumnMajorMatrix");
    }

    @Override
    public NLMatrix abtmm(DenseMatrix B) {
        if (B instanceof ColumnMajorMatrix) {
            NLMatrix C = new NLMatrix(nrows(), B.ncols());
            BLAS.getInstance().dgemm(NoTranspose, Transpose,
                    nrows(), B.ncols(), ncols(), 1.0, data(), nrows(), ((ColumnMajorMatrix) B).data(),
                    B.nrows(), 1, C.data(), C.nrows());
            return C;
        }

        throw new IllegalArgumentException("NLMatrix.abmm() parameter must be ColumnMajorMatrix");
    }

    @Override
    public NLMatrix atbmm(DenseMatrix B) {
        if (B instanceof ColumnMajorMatrix) {
            NLMatrix C = new NLMatrix(nrows(), B.ncols());
            BLAS.getInstance().dgemm(Transpose, NoTranspose,
                    nrows(), B.ncols(), ncols(), 1.0, data(), nrows(), ((ColumnMajorMatrix) B).data(),
                    B.nrows(), 1, C.data(), C.nrows());
            return C;
        }

        throw new IllegalArgumentException("NLMatrix.abmm() parameter must be ColumnMajorMatrix");
    }

    @Override
    public NLMatrix transpose() {
        NLMatrix B = new NLMatrix(ncols(), nrows());
        for (int i = 0; i < nrows(); i++) {
            for (int j = 0; j < ncols(); j++) {
                B.set(j, i, get(i, j));
            }
        }

        return B;
    }
}
