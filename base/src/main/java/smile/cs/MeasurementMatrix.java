/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.cs;

import java.io.Serial;
import java.io.Serializable;
import smile.math.MathEx;
import smile.stat.distribution.GaussianDistribution;
import smile.tensor.DenseMatrix;
import smile.tensor.Matrix;
import smile.wavelet.Wavelet;

import static smile.tensor.ScalarType.Float64;

/**
 * Measurement matrix for compressed sensing.
 *
 * <p>In compressed sensing the measurement process is modelled as
 * {@code y = Φ Ψ s}, where:
 * <ul>
 *   <li>{@code Φ} is the {@code m × n} <em>sensing matrix</em> (e.g. random
 *       Gaussian or Bernoulli projections),</li>
 *   <li>{@code Ψ} is the {@code n × n} <em>sparsifying basis</em> (e.g. a
 *       wavelet transform), and</li>
 *   <li>{@code s} is the sparse coefficient vector in the basis domain.</li>
 * </ul>
 *
 * <p>Recovery algorithms ({@link BasisPursuit}, {@link OMP}, {@link CoSaMP})
 * operate on the <em>compound</em> measurement matrix {@code A = Φ Ψ} or,
 * when no sparsifying basis is needed, directly on {@code Φ}.
 *
 * <p>For large {@code n} the wavelet sparsifying basis is applied
 * <em>implicitly</em> as a matrix–vector operator (forward / inverse DWT)
 * rather than explicitly forming the full {@code n × n} matrix, which makes
 * the approach memory-efficient.
 *
 * <h2>Supported sensing matrix types</h2>
 * <ul>
 *   <li>{@link #gaussian(int, int)} – i.i.d. Gaussian entries scaled by
 *       {@code 1/√m}; satisfies the RIP with high probability for
 *       {@code m = O(k log(n/k))}.</li>
 *   <li>{@link #bernoulli(int, int)} – i.i.d. ±1/√m Bernoulli entries; also
 *       satisfies the RIP with the same sample complexity.</li>
 *   <li>{@link #partial(int[], int)} – a random row-sub-sampling of the
 *       {@code n × n} identity matrix (partial identity / random sampling).</li>
 * </ul>
 *
 * <h2>Sparsifying bases</h2>
 * <p>When the signal is sparse in a wavelet domain, pass a {@link Wavelet}
 * instance to {@link #withWavelet(Wavelet)}.  The returned matrix operator
 * composes {@code Φ} with the DWT implicitly, so {@code y = Φ DWT(x)} for
 * a signal {@code x} of length {@code n} (a power of 2).
 *
 * <h2>References</h2>
 * <ol>
 * <li>E. J. Candès and M. B. Wakin, "An introduction to compressive sampling",
 *     IEEE Signal Process. Mag., 25(2):21–30, 2008.</li>
 * <li>R. Baraniuk, "Compressive sensing", IEEE Signal Process. Mag.,
 *     24(4):118–121, 2007.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class MeasurementMatrix implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MeasurementMatrix.class);

    /** The underlying sensing matrix Φ (m × n). */
    private final DenseMatrix phi;

    /** Optional wavelet sparsifying basis. May be null. */
    private final Wavelet wavelet;

    /**
     * Constructs a measurement matrix without a sparsifying basis.
     *
     * @param phi the sensing matrix Φ.
     */
    public MeasurementMatrix(DenseMatrix phi) {
        this(phi, null);
    }

    /**
     * Constructs a measurement matrix with an optional wavelet sparsifying basis.
     *
     * @param phi     the sensing matrix Φ (m × n).
     * @param wavelet the wavelet defining the sparsifying basis Ψ; may be {@code null}.
     */
    public MeasurementMatrix(DenseMatrix phi, Wavelet wavelet) {
        this.phi     = phi;
        this.wavelet = wavelet;
    }

    // =========================================================================
    //  Properties
    // =========================================================================

    /**
     * Returns the number of measurements (rows of Φ).
     *
     * @return {@code m}.
     */
    public int nrow() { return phi.nrow(); }

    /**
     * Returns the signal dimension (columns of Φ).
     *
     * @return {@code n}.
     */
    public int ncol() { return phi.ncol(); }

    /**
     * Returns the underlying sensing matrix Φ.
     *
     * @return the sensing matrix.
     */
    public DenseMatrix phi() { return phi; }

    /**
     * Returns the wavelet sparsifying basis, or {@code null} if not set.
     *
     * @return the wavelet.
     */
    public Wavelet wavelet() { return wavelet; }

    // =========================================================================
    //  Measurement operator
    // =========================================================================

    /**
     * Computes measurements {@code y = Φ x} (no wavelet basis).
     *
     * @param x the signal vector of length {@code n}.
     * @return the measurement vector of length {@code m}.
     */
    public double[] measure(double[] x) {
        int n = phi.ncol();
        if (x.length != n) throw new IllegalArgumentException("x.length != n");
        double[] y = new double[phi.nrow()];
        var xv = smile.tensor.Vector.column(x);
        var yv = smile.tensor.Vector.column(y);
        phi.mv(xv, yv);
        return yv.toArray(y);
    }

    /**
     * Computes measurements {@code y = Φ Ψ s} where {@code Ψ} is the
     * inverse-DWT operator (synthesis).
     *
     * <p>When no wavelet is configured this method is equivalent to
     * {@link #measure(double[])}.
     *
     * @param s the sparse coefficient vector in the wavelet domain
     *          (length {@code n}, power of 2).
     * @return the measurement vector of length {@code m}.
     */
    public double[] measureSparse(double[] s) {
        if (wavelet == null) return measure(s);
        // Synthesise signal from wavelet coefficients: x = Ψ s = IDWT(s)
        double[] x = s.clone();
        wavelet.inverse(x);
        return measure(x);
    }

    /**
     * Computes the adjoint operation {@code v = Φ^T y} (back-projection).
     *
     * @param y the measurement vector of length {@code m}.
     * @return the signal-domain vector of length {@code n}.
     */
    public double[] backProject(double[] y) {
        int m = phi.nrow();
        if (y.length != m) throw new IllegalArgumentException("y.length != m");
        int n = phi.ncol();
        double[] v = new double[n];
        var yv = smile.tensor.Vector.column(y);
        var vv = smile.tensor.Vector.column(v);
        phi.tv(yv, vv);
        return vv.toArray(v);
    }

    /**
     * Computes the adjoint in the sparse (wavelet) domain:
     * {@code v = Ψ^T Φ^T y = DWT(Φ^T y)}.
     *
     * @param y the measurement vector of length {@code m}.
     * @return the sparse-domain vector of length {@code n}.
     */
    public double[] backProjectSparse(double[] y) {
        double[] v = backProject(y);
        if (wavelet != null) wavelet.transform(v);
        return v;
    }

    // =========================================================================
    //  Compound matrix for solvers
    // =========================================================================

    /**
     * Returns an implicit {@link Matrix} that represents the compound operator
     * {@code A = Φ Ψ} (or {@code A = Φ} when no wavelet is set).  The matrix
     * is {@code m × n} and supports both {@code mv} and {@code tv} (adjoint)
     * matrix–vector products without explicitly forming the full matrix.
     *
     * <p>Pass the returned matrix directly to {@link BasisPursuit#fit},
     * {@link OMP#fit}, or {@link CoSaMP#fit}.
     *
     * @return the implicit compound measurement matrix.
     */
    public Matrix toMatrix() {
        if (wavelet == null) return phi;
        return new WaveletMatrix(phi, wavelet);
    }

    // =========================================================================
    //  Factory methods
    // =========================================================================

    /**
     * Creates a random Gaussian sensing matrix with entries
     * {@code Φ_{ij} ~ N(0, 1/m)}.
     *
     * <p>Gaussian matrices satisfy the <em>restricted isometry property</em>
     * (RIP) of order {@code k} with high probability when
     * {@code m ≥ C k log(n/k)}, for a universal constant {@code C}.
     *
     * @param m the number of measurements (rows).
     * @param n the signal dimension (columns).
     * @return the sensing matrix.
     */
    public static MeasurementMatrix gaussian(int m, int n) {
        var dist = new GaussianDistribution(0.0, 1.0 / Math.sqrt(m));
        DenseMatrix phi = DenseMatrix.zeros(Float64, m, n);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                phi.set(i, j, dist.rand());
            }
        }
        logger.info("Created Gaussian sensing matrix ({} × {})", m, n);
        return new MeasurementMatrix(phi);
    }

    /**
     * Creates a random Bernoulli sensing matrix with entries
     * {@code Φ_{ij} ∈ {+1/√m, −1/√m}} with equal probability.
     *
     * @param m the number of measurements (rows).
     * @param n the signal dimension (columns).
     * @return the sensing matrix.
     */
    public static MeasurementMatrix bernoulli(int m, int n) {
        double scale = 1.0 / Math.sqrt(m);
        DenseMatrix phi = DenseMatrix.zeros(Float64, m, n);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                phi.set(i, j, MathEx.random() < 0.5 ? scale : -scale);
            }
        }
        logger.info("Created Bernoulli sensing matrix ({} × {})", m, n);
        return new MeasurementMatrix(phi);
    }

    /**
     * Creates a partial identity (random row-sub-sampling) sensing matrix.
     *
     * <p>The sensing matrix is a sub-matrix of the {@code n × n} identity
     * formed by randomly selecting {@code m} rows without replacement.
     * Equivalently, {@code (Φ x)_i = x_{rows[i]}}.
     *
     * @param rows the row (sample) indices to retain; must be in {@code [0, n)}.
     * @param n    the signal dimension.
     * @return the sensing matrix.
     */
    public static MeasurementMatrix partial(int[] rows, int n) {
        int m = rows.length;
        DenseMatrix phi = DenseMatrix.zeros(Float64, m, n);
        for (int i = 0; i < m; i++) {
            phi.set(i, rows[i], 1.0);
        }
        logger.info("Created partial-identity sensing matrix ({} × {})", m, n);
        return new MeasurementMatrix(phi);
    }

    /**
     * Creates a random partial identity sensing matrix by selecting {@code m}
     * rows uniformly at random without replacement.
     *
     * @param m the number of measurements.
     * @param n the signal dimension.
     * @return the sensing matrix.
     */
    public static MeasurementMatrix partial(int m, int n) {
        if (m > n) throw new IllegalArgumentException("m (%d) > n (%d)".formatted(m, n));
        int[] perm = MathEx.permutate(n);
        int[] rows = new int[m];
        System.arraycopy(perm, 0, rows, 0, m);
        java.util.Arrays.sort(rows);
        return partial(rows, n);
    }

    /**
     * Returns a new {@code MeasurementMatrix} that combines this sensing matrix
     * with the given wavelet sparsifying basis.
     *
     * @param wavelet the wavelet defining the sparsifying transform Ψ.
     * @return the compound measurement matrix.
     */
    public MeasurementMatrix withWavelet(Wavelet wavelet) {
        return new MeasurementMatrix(phi, wavelet);
    }

    // =========================================================================
    //  Inner class: implicit wavelet compound matrix
    // =========================================================================

    /**
     * An implicit matrix representing {@code A = Φ Ψ} where {@code Ψ} is a
     * wavelet synthesis (inverse-DWT) operator.
     *
     * <p>The forward product is {@code y = Φ · IDWT(s)} and the adjoint is
     * {@code v = DWT(Φ^T y)}.  Neither {@code Ψ} nor its transpose is ever
     * formed explicitly.
     */
    static class WaveletMatrix implements Matrix, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final DenseMatrix phi;
        private final Wavelet wavelet;

        WaveletMatrix(DenseMatrix phi, Wavelet wavelet) {
            this.phi     = phi;
            this.wavelet = wavelet;
        }

        @Override public int nrow() { return phi.nrow(); }
        @Override public int ncol() { return phi.ncol(); }
        @Override public smile.tensor.ScalarType scalarType() { return phi.scalarType(); }

        // ---- unsupported mutation ops ----
        @Override public double get(int i, int j) { throw new UnsupportedOperationException(); }
        @Override public void set(int i, int j, double x) { throw new UnsupportedOperationException(); }
        @Override public void add(int i, int j, double x) { throw new UnsupportedOperationException(); }
        @Override public void sub(int i, int j, double x) { throw new UnsupportedOperationException(); }
        @Override public void mul(int i, int j, double x) { throw new UnsupportedOperationException(); }
        @Override public void div(int i, int j, double x) { throw new UnsupportedOperationException(); }
        @Override public Matrix scale(double alpha) { throw new UnsupportedOperationException(); }
        @Override public Matrix copy() { throw new UnsupportedOperationException(); }
        @Override public Matrix transpose() { throw new UnsupportedOperationException(); }

        /**
         * {@code y = alpha * Φ IDWT(x) + beta * y}  (forward)
         * {@code y = alpha * DWT(Φ^T x) + beta * y}  (transpose)
         */
        @Override
        public void mv(smile.linalg.Transpose trans, double alpha,
                       smile.tensor.Vector x, double beta, smile.tensor.Vector y) {
            if (trans == smile.linalg.Transpose.NO_TRANSPOSE) {
                // Forward: y = alpha * Φ IDWT(x) + beta * y
                double[] xArr = x.toArray(new double[x.size()]);
                wavelet.inverse(xArr);
                var xv = smile.tensor.Vector.column(xArr);
                phi.mv(smile.linalg.Transpose.NO_TRANSPOSE, alpha, xv, beta, y);
            } else {
                // Adjoint: y = alpha * DWT(Φ^T x) + beta * y
                double[] tmp = new double[phi.ncol()];
                var tmpV = smile.tensor.Vector.column(tmp);
                phi.mv(smile.linalg.Transpose.TRANSPOSE, 1.0, x, 0.0, tmpV);
                tmpV.toArray(tmp);
                wavelet.transform(tmp);
                int sz = y.size();
                for (int i = 0; i < sz; i++) {
                    y.set(i, alpha * tmp[i] + beta * y.get(i));
                }
            }
        }

        @Override
        public void mv(smile.tensor.Vector work, int inputOffset, int outputOffset) {
            int n = phi.ncol();
            int m = phi.nrow();
            double[] xArr = new double[n];
            for (int i = 0; i < n; i++) xArr[i] = work.get(inputOffset + i);
            wavelet.inverse(xArr);
            var xv = smile.tensor.Vector.column(xArr);
            var yv = smile.tensor.Vector.column(new double[m]);
            phi.mv(xv, yv);
            for (int i = 0; i < m; i++) work.set(outputOffset + i, yv.get(i));
        }

        @Override
        public void tv(smile.tensor.Vector work, int inputOffset, int outputOffset) {
            int n = phi.ncol();
            int m = phi.nrow();
            double[] xArr = new double[m];
            for (int i = 0; i < m; i++) xArr[i] = work.get(inputOffset + i);
            var xv = smile.tensor.Vector.column(xArr);
            double[] tmp = new double[n];
            var tmpV = smile.tensor.Vector.column(tmp);
            phi.tv(xv, tmpV);
            tmpV.toArray(tmp);
            wavelet.transform(tmp);
            for (int i = 0; i < n; i++) work.set(outputOffset + i, tmp[i]);
        }
    }
}

