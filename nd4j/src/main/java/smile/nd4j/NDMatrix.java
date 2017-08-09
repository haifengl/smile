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

package smile.nd4j;

import smile.math.matrix.Cholesky;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.LU;
import smile.math.matrix.QR;
import smile.math.matrix.SVD;
import smile.math.matrix.EVD;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.inverse.InvertMatrix;

/**
 * ND4j matrix wrapper.
 *
 * @author Haifeng Li
 */
public class NDMatrix extends DenseMatrix {
    private static final long serialVersionUID = 1L;

    /**
     * The matrix storage.
     */
    private INDArray A;

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public NDMatrix(double[][] A) {
        this.A = Nd4j.create(A);
    }

    /**
     * Constructor.
     * @param A the array of column vector.
     */
    public NDMatrix(double[] A) {
        this.A = Nd4j.create(A, new int[]{A.length, 1});
    }

    /**
     * Constructor.
     * @param A the NDArray of matrix.
     */
    public NDMatrix(INDArray A) {
        this.A = A;
    }

    /**
     * Constructor of all-zero matrix.
     */
    public NDMatrix(int rows, int cols) {
        A = Nd4j.zeros(rows, cols);
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public NDMatrix(int rows, int cols, double value) {
        if (value == 0.0)
            A = Nd4j.zeros(rows, cols);
        else if (value == 1.0)
            A = Nd4j.ones(rows, cols);
        else
            A = Nd4j.zeros(rows, cols).addi(value);
    }

    public static NDMatrix eye(int n) {
        return new NDMatrix(Nd4j.eye(n));
    }

    @Override
    public NDMatrix copy() {
        return new NDMatrix(A.dup());
    }

    @Override
    public double[] data() {
        return A.data().asDouble();
    }

    @Override
    public int ld() {
        return nrows();
    }

    @Override
    public int nrows() {
        return A.rows();
    }

    @Override
    public int ncols() {
        return A.columns();
    }

    @Override
    public double get(int i, int j) {
        return A.getDouble(i, j);
    }

    @Override
    public double set(int i, int j, double x) {
        A.putScalar(i, j, x);
        return x;
    }

    @Override
    public LU lu() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cholesky cholesky() {
        throw new UnsupportedOperationException();
    }

    @Override
    public QR qr() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SVD svd() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double[] eig() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EVD eigen() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        // Nd4j.getBlasWrapper().level2().gemv() crashes.
        // Use gemm for now.
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{n, 1});
        INDArray ndy = Nd4j.gemm(A, ndx, false, false);
        for (int i = 0; i < m; i++) {
            y[i] = ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        // Nd4j.getBlasWrapper().level2().gemv() crashes.
        // Use gemm for now.
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{n, 1});
        INDArray ndy = Nd4j.gemm(A, ndx, false, false);
        for (int i = 0; i < m; i++) {
            y[i] += ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        // Nd4j.getBlasWrapper().level2().gemv() crashes.
        // Use gemm for now.
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{n, 1});
        INDArray ndy = Nd4j.gemm(A, ndx, false, false);
        for (int i = 0; i < m; i++) {
            y[i] = b * y[i] + ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        // Nd4j.getBlasWrapper().level2().gemv() crashes.
        // Use gemm for now.
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{m, 1});
        INDArray ndy = Nd4j.gemm(A, ndx, true, false);
        for (int i = 0; i < n; i++) {
            y[i] = ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        // Nd4j.getBlasWrapper().level2().gemv() crashes.
        // Use gemm for now.
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{m, 1});
        INDArray ndy = Nd4j.gemm(A, ndx, true, false);
        for (int i = 0; i < n; i++) {
            y[i] += ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        // Nd4j.getBlasWrapper().level2().gemv() crashes.
        // Use gemm for now.
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{m, 1});
        INDArray ndy = Nd4j.gemm(A, ndx, true, false);
        for (int i = 0; i < n; i++) {
            y[i] = b * y[i] + ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public NDMatrix ata() {
        return new NDMatrix(Nd4j.gemm(A, A, true, false));
    }

    @Override
    public NDMatrix aat() {
        return new NDMatrix(Nd4j.gemm(A, A, false, true));
    }

    @Override
    public double add(int i, int j, double x) {
        double y = get(i, j) + x;
        return set(i, j, y);
    }

    @Override
    public double sub(int i, int j, double x) {
        double y = get(i, j) - x;
        return set(i, j, y);
    }

    @Override
    public double mul(int i, int j, double x) {
        double y = get(i, j) * x;
        return set(i, j, y);
    }

    @Override
    public double div(int i, int j, double x) {
        double y = get(i, j) / x;
        return set(i, j, y);
    }

    @Override
    public NDMatrix abmm(DenseMatrix B) {
        if (B instanceof NDMatrix) {
            NDMatrix b = (NDMatrix) B;
            return new NDMatrix(Nd4j.gemm(A, b.A, false, false));
        }

        throw new IllegalArgumentException("NDMatrix.abmm() parameter must be NDMatrix");
    }

    @Override
    public NDMatrix abtmm(DenseMatrix B) {
        if (B instanceof NDMatrix) {
            NDMatrix b = (NDMatrix) B;
            return new NDMatrix(Nd4j.gemm(A, b.A, false, true));
        }

        throw new IllegalArgumentException("NDMatrix.abtmm() parameter must be NDMatrix");
    }

    @Override
    public NDMatrix atbmm(DenseMatrix B) {
        if (B instanceof NDMatrix) {
            NDMatrix b = (NDMatrix) B;
            return new NDMatrix(Nd4j.gemm(A, b.A, true, false));
        }

        throw new IllegalArgumentException("NDMatrix.abtmm() parameter must be NDMatrix");
    }

    @Override
    public NDMatrix add(DenseMatrix b) {
        if (b instanceof NDMatrix)
            add((NDMatrix) b);
        else
            super.add(b);

        return this;
    }

    public NDMatrix add(NDMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        A.add(b.A, A);
        return this;
    }

    @Override
    public DenseMatrix add(DenseMatrix b, DenseMatrix c) {
        if (b instanceof NDMatrix && c instanceof NDMatrix)
            return add((NDMatrix) b, (NDMatrix) c);
        else
            return super.add(b, c);
    }

    public NDMatrix add(NDMatrix b, NDMatrix c) {
        A.add(b.A, c.A);
        return c;
    }

    @Override
    public NDMatrix sub(DenseMatrix b) {
        if (b instanceof NDMatrix)
            sub((NDMatrix) b);
        else
            super.sub(b);

        return this;
    }

    public NDMatrix sub(NDMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        A.sub(b.A, A);
        return this;
    }

    @Override
    public DenseMatrix sub(DenseMatrix b, DenseMatrix c) {
        if (b instanceof NDMatrix && c instanceof NDMatrix)
            return sub((NDMatrix) b, (NDMatrix) c);
        else
            return super.sub(b, c);
    }

    public NDMatrix sub(NDMatrix b, NDMatrix c) {
        A.sub(b.A, c.A);
        return c;
    }

    @Override
    public NDMatrix mul(DenseMatrix b) {
        if (b instanceof NDMatrix)
            mul((NDMatrix) b);
        else
            super.mul(b);

        return this;
    }

    public NDMatrix mul(NDMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        A.mul(b.A, A);
        return this;
    }

    @Override
    public DenseMatrix mul(DenseMatrix b, DenseMatrix c) {
        if (b instanceof NDMatrix && c instanceof NDMatrix)
            return mul((NDMatrix) b, (NDMatrix) c);
        else
            return super.mul(b, c);
    }

    public NDMatrix mul(NDMatrix b, NDMatrix c) {
        A.mul(b.A, c.A);
        return c;
    }

    @Override
    public NDMatrix div(DenseMatrix b) {
        if (b instanceof NDMatrix)
            div((NDMatrix) b);
        else
            super.div(b);

        return this;
    }

    public NDMatrix div(NDMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        A.div(b.A, A);
        return this;
    }

    @Override
    public DenseMatrix div(DenseMatrix b, DenseMatrix c) {
        if (b instanceof NDMatrix && c instanceof NDMatrix)
            return div((NDMatrix) b, (NDMatrix) c);
        else
            return super.div(b, c);
    }

    public NDMatrix div(NDMatrix b, NDMatrix c) {
        A.div(b.A, c.A);
        return c;
    }

    @Override
    public NDMatrix add(double x) {
        A.addi(x);
        return this;
    }

    @Override
    public DenseMatrix add(double x, DenseMatrix c) {
        if (c instanceof NDMatrix)
            return add(x, (NDMatrix)c);
        else
            return super.add(x, c);
    }

    public NDMatrix add(double x, NDMatrix c) {
        A.addi(x, c.A);
        return c;
    }

    @Override
    public NDMatrix sub(double x) {
        A.subi(x);
        return this;
    }

    @Override
    public DenseMatrix sub(double x, DenseMatrix c) {
        if (c instanceof NDMatrix)
            return sub(x, (NDMatrix) c);
        else
            return super.sub(x, c);
    }

    public NDMatrix sub(double x, NDMatrix c) {
        A.subi(x, c.A);
        return c;
    }

    @Override
    public NDMatrix mul(double x) {
        A.muli(x);
        return this;
    }

    @Override
    public DenseMatrix mul(double x, DenseMatrix c) {
        if (c instanceof NDMatrix)
            return mul(x, (NDMatrix) c);
        else
            return super.mul(x, c);
    }

    public NDMatrix mul(double x, NDMatrix c) {
        A.muli(x, c.A);
        return c;
    }

    @Override
    public NDMatrix div(double x) {
        A.divi(x);
        return this;
    }

    @Override
    public DenseMatrix div(double x, DenseMatrix c) {
        if (c instanceof NDMatrix)
            return div(x, (NDMatrix) c);
        else
            return super.div(x, c);
    }

    public NDMatrix div(double x, NDMatrix c) {
        A.divi(x, c.A);
        return c;
    }

    @Override
    public double sum() {
        return A.sumNumber().doubleValue();
    }

    @Override
    public NDMatrix transpose() {
        return new NDMatrix(A.transpose());
    }

    @Override
    public NDMatrix inverse() {
        return new NDMatrix(InvertMatrix.invert(A, false));
    }

    @Override
    public NDMatrix inverse(boolean inPlace) {
        return new NDMatrix(InvertMatrix.invert(A, inPlace));
    }
}
