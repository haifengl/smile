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
import java.util.Arrays;
import java.util.Properties;
import smile.math.MathEx;
import smile.tensor.Matrix;

/**
 * Orthogonal Matching Pursuit (OMP) for sparse signal recovery.
 *
 * <p>OMP is a greedy algorithm that iteratively selects the column of the
 * measurement matrix {@code A} most correlated with the current residual,
 * augments the support set, and then re-solves a least-squares problem on
 * the active set to refine the coefficient estimates.
 *
 * <p>Given an underdetermined system {@code y = A*x} where {@code x} is known
 * to be {@code k}-sparse, OMP reconstructs {@code x} in exactly {@code k}
 * steps (assuming the <em>restricted isometry property</em> holds with
 * {@code δ_{2k} < √2 − 1}).
 *
 * <p>The noisy extension terminates when {@code ‖residual‖₂ ≤ tolerance}.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Per iteration: {@code O(m·n)} for the correlation step plus
 *       {@code O(m·k²)} for the least-squares step (via QR update).</li>
 *   <li>Total: {@code O(k·(m·n + m·k²))}.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li>J. A. Tropp and A. C. Gilbert, "Signal recovery from random
 *     measurements via orthogonal matching pursuit", IEEE Trans. Inf. Theory,
 *     53(12):4655–4666, 2007.</li>
 * <li>G. Davis, S. Mallat and M. Avellaneda, "Adaptive greedy approximations",
 *     Constr. Approx., 13(1):57–98, 1997.</li>
 * </ol>
 *
 * @param x       the recovered sparse signal (length {@code n}).
 * @param support the indices of the identified non-zero components.
 * @param iter    the number of iterations performed.
 * @author Haifeng Li
 */
public record OMP(double[] x, int[] support, int iter) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OMP.class);

    // =========================================================================
    //  Hyperparameters
    // =========================================================================

    /**
     * Hyperparameters for OMP.
     *
     * @param sparsity  the target sparsity {@code k} (maximum number of
     *                  non-zero components). The algorithm terminates once
     *                  {@code k} atoms have been selected, unless the residual
     *                  tolerance is reached first.
     * @param tol       residual tolerance. The algorithm stops early if
     *                  {@code ‖residual‖₂ / ‖y‖₂ < tol}.
     */
    public record Options(int sparsity, double tol) {
        /** Constructs options. */
        public Options {
            if (sparsity <= 0) throw new IllegalArgumentException("sparsity must be > 0: " + sparsity);
            if (tol < 0)       throw new IllegalArgumentException("tol must be ≥ 0: " + tol);
        }

        /**
         * Returns properties for persistence.
         * @return the properties.
         */
        public Properties toProperties() {
            var p = new Properties();
            p.setProperty("smile.cs.omp.sparsity", Integer.toString(sparsity));
            p.setProperty("smile.cs.omp.tol",      Double.toString(tol));
            return p;
        }

        /**
         * Creates options from properties.
         * @param props the properties.
         * @return the options.
         */
        public static Options of(Properties props) {
            int    sparsity = Integer.parseInt( props.getProperty("smile.cs.omp.sparsity", "10"));
            double tol      = Double.parseDouble(props.getProperty("smile.cs.omp.tol",      "1e-6"));
            return new Options(sparsity, tol);
        }
    }

    // =========================================================================
    //  Public factory methods
    // =========================================================================

    /**
     * Recovers a sparse signal via Orthogonal Matching Pursuit.
     *
     * @param A        the {@code m × n} measurement matrix.
     * @param y        the {@code m}-dimensional measurement vector.
     * @param sparsity the target sparsity level {@code k}.
     * @return the recovered signal.
     */
    public static OMP fit(Matrix A, double[] y, int sparsity) {
        return fit(A, y, new Options(sparsity, 1e-6));
    }

    /**
     * Recovers a sparse signal via Orthogonal Matching Pursuit.
     *
     * @param A       the {@code m × n} measurement matrix.
     * @param y       the {@code m}-dimensional measurement vector.
     * @param options solver hyperparameters.
     * @return the recovered signal.
     */
    public static OMP fit(Matrix A, double[] y, Options options) {
        int m = A.nrow();
        int n = A.ncol();
        if (y.length != m) {
            throw new IllegalArgumentException(
                    "y length %d does not match matrix rows %d".formatted(y.length, m));
        }

        int    k   = Math.min(options.sparsity, Math.min(m, n));
        double tol = options.tol;

        // Pre-compute all columns of A via unit-vector matvec: cols[j] = A e_j.
        // This is done once up-front so the hot inner loop is O(1) per entry.
        // For implicit matrices this avoids calling get(i,j) which may throw.
        double[][] cols = new double[n][m];
        double[]   colNorm = new double[n];
        double[] ej = new double[n];
        for (int j = 0; j < n; j++) {
            ej[j] = 1.0;
            cols[j] = BasisPursuit.matvec(A, ej, m, n, false);
            ej[j] = 0.0;
            colNorm[j] = MathEx.norm(cols[j]);
        }

        double ynorm = MathEx.norm(y);
        if (ynorm < 1e-30) {
            logger.info("OMP: y is near zero; returning zero solution.");
            return new OMP(new double[n], new int[0], 0);
        }

        // Active support indices
        int[] support = new int[k];
        // A_S: selected columns, stored as aS[s][i]
        double[][] aS = new double[k][m];

        double[] residual = Arrays.copyOf(y, m);
        double[] x = new double[n];
        double[] coeff = null;

        // Incremental QR (Gram–Schmidt)
        double[][] Q = new double[k][m];  // orthonormal basis rows

        int iter = 0;
        for (iter = 0; iter < k; iter++) {
            // Correlation step: e = A^T r, find largest |e_j| (normalised by col norm)
            double[] Atr = BasisPursuit.matvec(A, residual, m, n, true);
            int pivot = -1;
            double maxCorr = -1;
            for (int j = 0; j < n; j++) {
                boolean inSupport = false;
                for (int s = 0; s < iter; s++) {
                    if (support[s] == j) { inSupport = true; break; }
                }
                if (inSupport) continue;
                double corr = Math.abs(Atr[j]) / (colNorm[j] > 1e-30 ? colNorm[j] : 1.0);
                if (corr > maxCorr) { maxCorr = corr; pivot = j; }
            }
            if (pivot < 0) break;

            support[iter] = pivot;
            // Store the selected column (already computed above)
            aS[iter] = cols[pivot];

            // Gram-Schmidt orthogonalisation to get new Q row
            double[] q = Arrays.copyOf(aS[iter], m);
            for (int s = 0; s < iter; s++) {
                double proj = MathEx.dot(q, Q[s]);
                for (int i = 0; i < m; i++) q[i] -= proj * Q[s][i];
            }
            double qnorm = MathEx.norm(q);
            if (qnorm < 1e-12) {
                logger.warn("OMP: column {} is linearly dependent on existing support; stopping.", pivot);
                iter++;  // count this iteration
                break;
            }
            for (int i = 0; i < m; i++) q[i] /= qnorm;
            Q[iter] = q;

            // Compute coefficients on support via Q^T y
            // Projection: coeff[s] = Q[s] · y  for each s
            coeff = new double[iter + 1];
            for (int s = 0; s <= iter; s++) {
                coeff[s] = MathEx.dot(Q[s], y);
            }

            // Update residual: r = y − Q Q^T y
            System.arraycopy(y, 0, residual, 0, m);
            for (int s = 0; s <= iter; s++) {
                double proj = coeff[s];
                for (int i = 0; i < m; i++) residual[i] -= proj * Q[s][i];
            }

            double relResidual = MathEx.norm(residual) / ynorm;
            logger.debug("OMP iter {}: selected col {}, rel residual = {}", iter, pivot, relResidual);
            if (relResidual < tol) {
                iter++;
                break;
            }
        }

        // Recover x from coeff via back-substitution through the Q-R relation:
        // A_S = Q R  →  x_S = R^{-1} Q^T y  = R^{-1} coeff
        // But we stored coeff = Q^T y already.
        // We need R so that A_S coeff_LS = y restricted to support.
        // Simpler: direct least-squares on the small (m × iter) system A_S * x_S ≈ y
        int activeLen = Math.min(iter, k);
        if (coeff != null && activeLen > 0) {
            int[] activeSup = Arrays.copyOf(support, activeLen);
            double[] ls = leastSquares(A, y, activeSup, m, activeLen);
            for (int s = 0; s < activeLen; s++) {
                x[activeSup[s]] = ls[s];
            }
        }

        return new OMP(x, Arrays.copyOf(support, Math.min(iter, k)), iter);
    }

    // =========================================================================
    //  Utilities
    // =========================================================================

    /**
     * Solves the small least-squares system {@code A_S * c ≈ y} where
     * {@code A_S} is the sub-matrix of {@code A} with columns indexed by
     * {@code support}, using QR decomposition (Gram–Schmidt).
     *
     * @param A       the full measurement matrix.
     * @param y       the observation vector.
     * @param support the active column indices.
     * @param m       number of rows.
     * @param k       size of support.
     * @return the least-squares coefficient vector of length {@code k}.
     */
    static double[] leastSquares(Matrix A, double[] y, int[] support, int m, int k) {
        int n = A.ncol();
        // Build A_S as double[k][m] by applying A to unit vectors e_{support[j]}
        double[][] AS = new double[k][m];
        double[] ej = new double[n];
        for (int j = 0; j < k; j++) {
            int col = support[j];
            ej[col] = 1.0;
            AS[j] = BasisPursuit.matvec(A, ej, m, n, false);
            ej[col] = 0.0;
        }
        // QR via Gram-Schmidt
        double[][] Q = new double[k][m];
        double[][] R = new double[k][k];
        for (int j = 0; j < k; j++) {
            double[] q = Arrays.copyOf(AS[j], m);
            for (int s = 0; s < j; s++) {
                R[s][j] = MathEx.dot(AS[j], Q[s]);
                for (int i = 0; i < m; i++) q[i] -= R[s][j] * Q[s][i];
            }
            R[j][j] = MathEx.norm(q);
            if (R[j][j] < 1e-12) break;
            for (int i = 0; i < m; i++) Q[j][i] = q[i] / R[j][j];
        }
        // coeff = Q^T y
        double[] c = new double[k];
        for (int j = 0; j < k; j++) c[j] = MathEx.dot(Q[j], y);
        // Back-substitute R x = c
        double[] sol = new double[k];
        for (int j = k - 1; j >= 0; j--) {
            double sum = c[j];
            for (int s = j + 1; s < k; s++) sum -= R[j][s] * sol[s];
            sol[j] = R[j][j] > 1e-12 ? sum / R[j][j] : 0.0;
        }
        return sol;
    }
}

