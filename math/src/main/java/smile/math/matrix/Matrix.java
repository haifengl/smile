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

package smile.math.matrix;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import smile.stat.distribution.GaussianDistribution;
import smile.util.SparseArray;

/**
 * An abstract interface of matrix. The most important method is the matrix vector
 * multiplication, which is the only operation needed in many iterative matrix
 * algorithms, e.g. biconjugate gradient method for solving linear equations and
 * power iteration and Lanczos algorithm for eigen decomposition, which are
 * usually very efficient for very large and sparse matrices.
 * <p>
 * A matrix is a rectangular array of numbers. An item in a matrix is called
 * an entry or an element. Entries are often denoted by a variable with two
 * subscripts. Matrices of the same size can be added and subtracted entrywise
 * and matrices of compatible size can be multiplied. These operations have
 * many of the properties of ordinary arithmetic, except that matrix
 * multiplication is not commutative, that is, AB and BA are not equal in
 * general.
 * <p>
 * Matrices are a key tool in linear algebra. One use of matrices is to
 * represent linear transformations and matrix multiplication corresponds
 * to composition of linear transformations. Matrices can also keep track of
 * the coefficients in a system of linear equations. For a square matrix,
 * the determinant and inverse matrix (when it exists) govern the behavior
 * of solutions to the corresponding system of linear equations, and
 * eigenvalues and eigenvectors provide insight into the geometry of
 * the associated linear transformation.
 * <p>
 * There are several methods to render matrices into a more easily accessible
 * form. They are generally referred to as matrix transformation or matrix
 * decomposition techniques. The interest of all these decomposition techniques
 * is that they preserve certain properties of the matrices in question, such
 * as determinant, rank or inverse, so that these quantities can be calculated
 * after applying the transformation, or that certain matrix operations are
 * algorithmically easier to carry out for some types of matrices.
 * <p>
 * The LU decomposition factors matrices as a product of lower (L) and an upper
 * triangular matrices (U). Once this decomposition is calculated, linear
 * systems can be solved more efficiently, by a simple technique called
 * forward and back substitution. Likewise, inverses of triangular matrices
 * are algorithmically easier to calculate. The QR decomposition factors matrices
 * as a product of an orthogonal (Q) and a right triangular matrix (R). QR decomposition
 * is often used to solve the linear least squares problem, and is the basis for
 * a particular eigenvalue algorithm, the QR algorithm. Singular value decomposition
 * expresses any matrix A as a product UDV', where U and V are unitary matrices
 * and D is a diagonal matrix. The eigendecomposition or diagonalization
 * expresses A as a product VDV<sup>-1</sup>, where D is a diagonal matrix and
 * V is a suitable invertible matrix. If A can be written in this form, it is
 * called diagonalizable.
 *
 * @author Haifeng Li
 */
public interface Matrix extends Serializable {
    /**
     * Returns an matrix initialized by given two-dimensional array.
     */
    static DenseMatrix of(double[][] A) {
        return Factory.matrix(A);
    }

    /**
     * Returns a column vector/matrix initialized by given one-dimensional array.
     */
    static DenseMatrix of(double[] A) {
        return Factory.matrix(A);
    }

    /**
     * Creates a matrix filled with given value.
     */
    static DenseMatrix of(int rows, int cols, double value) {
        return Factory.matrix(rows, cols, value);
    }

    /**
     * Returns all-zero matrix.
     */
    static DenseMatrix zeros(int rows, int cols) {
        return Factory.matrix(rows, cols);
    }

    /**
     * Return an all-one matrix.
     */
    static DenseMatrix ones(int rows, int cols) {
        return Factory.matrix(rows, cols, 1.0);
    }

    /**
     * Returns an n-by-n identity matrix.
     */
    static DenseMatrix eye(int n) {
        DenseMatrix matrix = Factory.matrix(n, n);

        for (int i = 0; i < n; i++) {
            matrix.set(i, i, 1.0);
        }

        return matrix;
    }

    /**
     * Returns an m-by-n identity matrix.
     */
    static DenseMatrix eye(int m, int n) {
        DenseMatrix matrix = Factory.matrix(m, n);

        int k = Math.min(m, n);
        for (int i = 0; i < k; i++) {
            matrix.set(i, i, 1.0);
        }

        return matrix;
    }

    /**
     * Returns a square diagonal matrix with the elements of vector diag on the main diagonal.
     * @param A the array of diagonal elements.
     */
    static DenseMatrix diag(double[] A) {
        int n = A.length;
        DenseMatrix matrix = Factory.matrix(n, n);

        for (int i = 0; i < n; i++) {
            matrix.set(i, i, A[i]);
        }

        return matrix;
    }

    /**
     * Returns a random matrix of standard normal distributed values with given mean and standard dev.
     */
    static DenseMatrix randn(int rows, int cols) {
        return randn(rows, cols, 0.0, 1.0);
    }

    /**
     * Returns a random matrix of normal distributed values with given mean and standard dev.
     */
    static DenseMatrix randn(int rows, int cols, double mu, double sigma) {
        DenseMatrix a = zeros(rows, cols);
        GaussianDistribution g = new GaussianDistribution(mu, sigma);

        for (int j = 0; j < cols; j++) {
            for (int i = 0; i < rows; i++) {
                a.set(i, j, g.rand());
            }
        }

        return a;
    }

    /**
     * Returns the string representation of matrix.
     * @param full Print the full matrix if true. Otherwise,
     *             print only top left 7 x 7 submatrix.
     */
    default String toString(boolean full) {
        return full ? toString(nrows(), ncols()) : toString(7, 7);
    }

    /**
     * Returns the string representation of matrix.
     * @param m the number of rows to print.
     * @param n the number of columns to print.
     */
    default String toString(int m, int n) {
        StringBuilder sb = new StringBuilder(nrows() + " x " + ncols() + "\n");
        m = Math.min(m, nrows());
        n = Math.min(n, ncols());

        String newline = n < ncols() ? "...\n" : "\n";

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sb.append(String.format("%8.4f  ", get(i, j)));
            }
            sb.append(newline);
        }

        if (m < nrows()) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }

    /** Returns true if the matrix is symmetric. */
    default boolean isSymmetric() {
        return false;
    }

    /**
     * Sets if the matrix is symmetric. It is the caller's responability to
     * make sure if the matrix symmetric. Also the matrix won't update this
     * property if the matrix values are changed.
     */
    default void setSymmetric(boolean symmetric) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the number of rows.
     */
    int nrows();

    /**
     * Returns the number of columns.
     */
    int ncols();

    /**
     * Returns the matrix transpose.
     */
    Matrix transpose();

    /**
     * Returns the entry value at row i and column j.
     */
    double get(int i, int j);

    /**
     * Returns the entry value at row i and column j. For Scala users.
     */
    default double apply(int i, int j) {
        return get(i, j);
    }

    /**
     * Returns the diagonal elements.
     */
    default double[] diag() {
        int n = Math.min(nrows(), ncols());

        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = get(i, i);
        }

        return d;
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     */
    default double trace() {
        int n = Math.min(nrows(), ncols());

        double t = 0.0;
        for (int i = 0; i < n; i++) {
            t += get(i, i);
        }

        return t;
    }

    /**
     * Returns A' * A
     */
    Matrix ata();

    /**
     * Returns A * A'
     */
    Matrix aat();

    /**
     * y = A * x
     * @return y
     */
    double[] ax(double[] x, double[] y);

    /**
     * y = A * x + y
     * @return y
     */
    double[] axpy(double[] x, double[] y);

    /**
     * y = A * x + b * y
     * @return y
     */
    double[] axpy(double[] x, double[] y, double b);

    /**
     * y = A' * x
     * @return y
     */
    double[] atx(double[] x, double[] y);

    /**
     * y = A' * x + y
     * @return y
     */
    double[] atxpy(double[] x, double[] y);

    /**
     * y = A' * x + b * y
     * @return y
     */
    double[] atxpy(double[] x, double[] y, double b);

    /**
     * Find k largest approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param k the number of eigenvalues we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     */
    default EVD eigen(int k) {
        return eigen(k, 1.0E-8, 10 * nrows());
    }

    /**
     * Find k largest approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param k the number of eigenvalues we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     * @param kappa relative accuracy of ritz values acceptable as eigenvalues.
     * @param maxIter Maximum number of iterations.
     */
    default EVD eigen(int k, double kappa, int maxIter) {
        try {
            Class<?> clazz = Class.forName("smile.netlib.ARPACK");
            java.lang.reflect.Method method = clazz.getMethod("eigen", Matrix.class, Integer.TYPE, String.class, Double.TYPE, Integer.TYPE);
            return (EVD) method.invoke(null, this, k, "LA", kappa, maxIter);
        } catch (Exception e) {
            if (!(e instanceof ClassNotFoundException)) {
                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Matrix.class);
                logger.info("Matrix.eigen({}, {}, {}):", k, kappa, maxIter, e);
            }
            return Lanczos.eigen(this, k, kappa, maxIter);
        }
    }

    /**
     * Find k largest approximate singular triples of a matrix by the
     * Lanczos algorithm.
     *
     * @param k the number of singular triples we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     */
    default SVD svd(int k) {
        return svd(k, 1.0E-8, 10 * nrows());
    }

    /**
     * Find k largest approximate singular triples of a matrix by the
     * Lanczos algorithm.
     *
     * @param k the number of singular triples we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     * @param kappa relative accuracy of ritz values acceptable as singular values.
     * @param maxIter Maximum number of iterations.
     */
    default SVD svd(int k, double kappa, int maxIter) {
        ATA B = new ATA(this);
        EVD eigen = Lanczos.eigen(B, k, kappa, maxIter);

        double[] s = eigen.getEigenValues();
        for (int i = 0; i < s.length; i++) {
            s[i] = Math.sqrt(s[i]);
        }

        int m = nrows();
        int n = ncols();

        if (m >= n) {

            DenseMatrix V = eigen.getEigenVectors();

            double[] tmp = new double[m];
            double[] vi = new double[n];
            DenseMatrix U = Matrix.zeros(m, s.length);
            for (int i = 0; i < s.length; i++) {
                for (int j = 0; j < n; j++) {
                    vi[j] = V.get(j, i);
                }

                ax(vi, tmp);

                for (int j = 0; j < m; j++) {
                    U.set(j, i, tmp[j] / s[i]);
                }
            }

            return new SVD(U, V, s);

        } else {

            DenseMatrix U = eigen.getEigenVectors();

            double[] tmp = new double[n];
            double[] ui = new double[m];
            DenseMatrix V = Matrix.zeros(n, s.length);
            for (int i = 0; i < s.length; i++) {
                for (int j = 0; j < m; j++) {
                    ui[j] = U.get(j, i);
                }

                atx(ui, tmp);

                for (int j = 0; j < n; j++) {
                    V.set(j, i, tmp[j] / s[i]);
                }
            }

            return new SVD(U, V, s);
        }
    }

    /**
     * Reads a matrix from a Matrix Market File Format file.
     * For details, see
     * <a href="http://people.sc.fsu.edu/~jburkardt/data/mm/mm.html">http://people.sc.fsu.edu/~jburkardt/data/mm/mm.html</a>.
     *
     * The returned matrix may be dense or sparse.
     *
     * @param path the input file path.
     * @return a dense or sparse matrix.
     * @author Haifeng Li
     */
    static Matrix market(Path path) throws IOException, ParseException {
        try (LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(path));
             Scanner scanner = new Scanner(reader)) {

            // The header line has the form
            // %%MatrixMarket object format field symmetry
            String header = scanner.next();
            if (!header.equals("%%MatrixMarket")) {
                throw new ParseException("Invalid Matrix Market file header", reader.getLineNumber());
            }

            String object = scanner.next();
            if (!object.equals("matrix")) {
                throw new UnsupportedOperationException("The object is not a matrix file: " + object);
            }

            String format = scanner.next();
            String field = scanner.next();
            if (field.equals("complex") || field.equals("pattern")) {
                throw new UnsupportedOperationException("No support of complex or pattern matrix");
            }
            String symmetry = scanner.nextLine().trim();
            boolean symmetric = symmetry.equals("symmetric");
            boolean skew = symmetry.equals("skew-symmetric");

            // Ignore comment lines
            String line = scanner.nextLine();
            while (line.startsWith("%")) {
                line = scanner.nextLine();
            }

            if (format.equals("coordinate")) {
                // Size line
                Scanner s = new Scanner(line);
                int nrows = s.nextInt();
                int ncols = s.nextInt();
                int nz = s.nextInt();

                int[] colSize = new int[ncols];
                List<SparseArray> rows = new ArrayList<>();
                for (int i = 0; i < nrows; i++) {
                    rows.add(new SparseArray());
                }

                for (int k = 0; k < nz; k++) {
                    String[] tokens = scanner.nextLine().trim().split("\\s+");
                    if (tokens.length != 3) {
                        throw new ParseException("Invalid data line: " + line, reader.getLineNumber());
                    }

                    int i = Integer.parseInt(tokens[0]) - 1;
                    int j = Integer.parseInt(tokens[1]) - 1;
                    double x = Double.parseDouble(tokens[2]);

                    SparseArray row = rows.get(i);
                    row.set(j, x);
                    colSize[j] += 1;

                    if (symmetric) {
                        row = rows.get(j);
                        row.set(i, x);
                        colSize[i] += 1;
                    } else if (skew) {
                        row = rows.get(j);
                        row.set(i, -x);
                        colSize[i] += 1;
                    }
                }

                int[] pos = new int[ncols];
                int[] colIndex = new int[ncols + 1];
                for (int i = 0; i < ncols; i++) {
                    colIndex[i + 1] = colIndex[i] + colSize[i];
                }

                if (symmetric || skew) {
                    nz *= 2;
                }
                int[] rowIndex = new int[nz];
                double[] x = new double[nz];

                for (int i = 0; i < nrows; i++) {
                    for (SparseArray.Entry e :rows.get(i)) {
                        int j = e.i;
                        int k = colIndex[j] + pos[j];

                        rowIndex[k] = i;
                        x[k] = e.x;
                        pos[j]++;
                    }
                }

                SparseMatrix matrix = new SparseMatrix(nrows, ncols, x, rowIndex, colIndex);
                matrix.setSymmetric(symmetric);

                return matrix;

            } else if (format.equals("array")) {
                // Size line
                Scanner s = new Scanner(line);
                int nrows = s.nextInt();
                int ncols = s.nextInt();

                DenseMatrix matrix = Matrix.of(nrows, ncols, 0.0);
                matrix.setSymmetric(symmetric);
                for (int j = 0; j < ncols; j++) {
                    for (int i = 0; i < nrows; i++) {
                        double x = scanner.nextDouble();
                        matrix.set(i, j, x);
                    }
                }

                return matrix;

            } else {
                throw new ParseException("Invalid Matrix Market format: " + format, 0);
            }
        }
    }
}
