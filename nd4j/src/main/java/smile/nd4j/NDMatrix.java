/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.nd4j;

import smile.math.matrix.DenseMatrix;
import smile.math.matrix.SVD;
import smile.math.matrix.EVD;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.inverse.InvertMatrix;

/**
 * ND4j matrix wrapper.
 *
 * @author Haifeng Li
 */
public class NDMatrix implements DenseMatrix {
    private static final long serialVersionUID = 1L;

    static {
        // ND4J allows INDArrays to be backed by either float
        // or double-precision values. The default is single-precision
        // (float). Here we set the order globally to double precision.
        // Alternatively, we can set the property when launching the JVM:
        // -Ddtype=double

        // since beta4
        Nd4j.setDefaultDataTypes(org.nd4j.linalg.api.buffer.DataType.DOUBLE, org.nd4j.linalg.api.buffer.DataType.DOUBLE);
    }

    /**
     * The matrix storage.
     */
    INDArray A;
    /**
     * True if the matrix is symmetric.
     */
    private boolean symmetric = false;

    private static char RowOrder = 'e'; // CblasRowMajor
    private static char ColOrder = 'f'; // CblasColMajor
    private static char NoTranspose = 'N';
    private static char Transpose   = 'T';
    private static char ConjugateTranspose = 'C';

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
            A = Nd4j.zeros(rows, cols).assign(value);
    }

    public static NDMatrix eye(int n) {
        return new NDMatrix(Nd4j.eye(n));
    }

    @Override
    public void fill(double x) {
        A.assign(x);
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public boolean isSymmetric() {
        return symmetric;
    }

    @Override
    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
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
        INDArray r = Nd4j.getNDArrayFactory().lapack().getrf(A);
        int[] piv = new int[(int) r.length()];
        for (int i = 0; i < piv.length; i++) {
            piv[i] = r.getInt(i);
        }
        // Nd4j doesn't report if the matrix is singular.
        return new LU(this, piv, false);
    }

    @Override
    public Cholesky cholesky() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Cholesky decomposition on non-square matrix");
        }

        Nd4j.getNDArrayFactory().lapack().potrf(A, true);
        return new Cholesky(this);
    }

    @Override
    public QR qr() {
        double[] tau = new double[Math.min(nrows(), ncols())];
        INDArray R = Nd4j.create(ncols(), ncols());
        Nd4j.getNDArrayFactory().lapack().geqrf(A, R);
        for (int i = 0; i < tau.length; i++) {
            tau[i] = R.getDouble(i, i);
        }
        // Nd4j doesn't report if the matrix is singular.
        return new QR(this, tau, false);
    }

    @Override
    public SVD svd() {
        int m = nrows();
        int n = ncols();
        int mn = Math.min(m, n);

        INDArray S = Nd4j.create(mn);
        INDArray U = Nd4j.create(m, m);
        INDArray Vt = Nd4j.create(n, n);
        Nd4j.getNDArrayFactory().lapack().gesvd(A, S, U, Vt);

        double[] s = new double[mn];
        for (int i = 0; i < mn; i++) {
            s[i] = S.getDouble(i);
        }
        return new SVD(new NDMatrix(U), new NDMatrix(Vt.transpose()), s);
    }

    @Override
    public double[] eig() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Eigen decomposition on non-square matrix");
        }

        int n = nrows();
        INDArray V = Nd4j.create(n);

        if (symmetric) {
            Nd4j.getNDArrayFactory().lapack().syev('N', 'L', A, V);
        } else {
            throw new UnsupportedOperationException("Nd4j doesn't support eigen decomposition of asymmetric matrix");
        }

        // LAPACK returns eigen values in ascending order.
        // In contrast, JMatrix returns eigen values in descending order.
        // Reverse the array to match JMatrix.
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = V.getDouble(n - i - 1);
        }
        return d;
    }

    @Override
    public EVD eigen() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Eigen decomposition on non-square matrix");
        }

        int n = nrows();
        INDArray V = Nd4j.create(n);

        if (symmetric) {
            Nd4j.getNDArrayFactory().lapack().syev('V', 'L', A, V);
        } else {
            throw new UnsupportedOperationException("Nd4j doesn't support eigen decomposition of asymmetric matrix");
        }

        // LAPACK returns eigen values in ascending order.
        // In contrast, JMatrix returns eigen values in descending order.
        // Reverse the array to match JMatrix.
        INDArray a = Nd4j.create(n, n);
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = V.getDouble(n - i - 1);
            INDArrayIndex[] col = {NDArrayIndex.all(), NDArrayIndex.point(i)};
            a.put(col, A.get(NDArrayIndex.all(), NDArrayIndex.point(n - i - 1)));
        }
        return new EVD(new NDMatrix(a), d);
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{n, 1});
        INDArray ndy = Nd4j.create(m, 1);
        Nd4j.getBlasWrapper().level2().gemv(ColOrder, NoTranspose, 1.0, A, ndx, 0.0, ndy);
        for (int i = 0; i < m; i++) {
            y[i] = ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{n, 1});
        INDArray ndy = Nd4j.create(y, new int[]{m, 1});
        Nd4j.getBlasWrapper().level2().gemv(ColOrder, NoTranspose, 1.0, A, ndx, 1.0, ndy);
        for (int i = 0; i < m; i++) {
            y[i] = ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{n, 1});
        INDArray ndy = Nd4j.create(y, new int[]{m, 1});
        Nd4j.getBlasWrapper().level2().gemv(ColOrder, NoTranspose, 1.0, A, ndx, b, ndy);
        for (int i = 0; i < m; i++) {
            y[i] = ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{m, 1});
        INDArray ndy = Nd4j.create(n, 1);
        Nd4j.getBlasWrapper().level2().gemv(ColOrder, Transpose, 1.0, A, ndx, 0.0, ndy);
        for (int i = 0; i < n; i++) {
            y[i] = ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{m, 1});
        INDArray ndy = Nd4j.create(y, new int[]{n, 1});
        Nd4j.getBlasWrapper().level2().gemv(ColOrder, Transpose, 1.0, A, ndx, 1.0, ndy);
        for (int i = 0; i < n; i++) {
            y[i] = ndy.getDouble(i);
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        int m = nrows();
        int n = ncols();
        INDArray ndx = Nd4j.create(x, new int[]{m, 1});
        INDArray ndy = Nd4j.create(y, new int[]{n, 1});
        Nd4j.getBlasWrapper().level2().gemv(ColOrder, Transpose, 1.0, A, ndx, b, ndy);
        for (int i = 0; i < n; i++) {
            y[i] = ndy.getDouble(i);
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

        throw new IllegalArgumentException("NDMatrix.atbmm() parameter must be NDMatrix");
    }

    @Override
    public NDMatrix atbtmm(DenseMatrix B) {
        if (B instanceof NDMatrix) {
            NDMatrix b = (NDMatrix) B;
            return new NDMatrix(Nd4j.gemm(A, b.A, true, true));
        }

        throw new IllegalArgumentException("NDMatrix.atbtmm() parameter must be NDMatrix");
    }

    @Override
    public NDMatrix add(DenseMatrix b) {
        if (b instanceof NDMatrix)
            add((NDMatrix) b);
        else
            DenseMatrix.super.add(b);

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
            return DenseMatrix.super.add(b, c);
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
            DenseMatrix.super.sub(b);

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
            return DenseMatrix.super.sub(b, c);
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
            DenseMatrix.super.mul(b);

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
            return DenseMatrix.super.mul(b, c);
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
            DenseMatrix.super.div(b);

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
            return DenseMatrix.super.div(b, c);
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
            return DenseMatrix.super.add(x, c);
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
            return DenseMatrix.super.sub(x, c);
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
            return DenseMatrix.super.mul(x, c);
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
            return DenseMatrix.super.div(x, c);
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
