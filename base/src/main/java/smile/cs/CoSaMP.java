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
import smile.sort.Sort;
import smile.tensor.Matrix;

/**
 * Compressive Sampling Matching Pursuit (CoSaMP) for sparse signal recovery.
 *
 * <p>CoSaMP is a greedy recovery algorithm with provably near-optimal
 * guarantees. Given an {@code m × n} measurement matrix {@code A} and
 * observations {@code y = A x}, where {@code x} is {@code k}-sparse,
 * CoSaMP recovers {@code x} in {@code O(log(‖x‖/ε))} iterations, each of
 * which requires only a matrix–vector product and a least-squares solve on a
 * small sub-system.
 *
 * <p>The algorithm maintains a candidate support of size at most {@code 2k},
 * updates it via a "signal proxy" (the gradient of the data-fit term), prunes
 * it back to size {@code k}, and re-estimates the signal coefficients by
 * least-squares on the pruned support.
 *
 * <h2>Algorithm sketch (one iteration)</h2>
 * <ol>
 *   <li><b>Proxy:</b>  {@code e = A^T r}  where {@code r = y − A x_old}.</li>
 *   <li><b>Identify:</b> merge support of {@code x_old} with the {@code 2k}
 *       largest entries of {@code e}; form union support Ω.</li>
 *   <li><b>Least-squares:</b>  {@code b = arg min ‖y − A_Ω b‖₂}.</li>
 *   <li><b>Prune:</b>  retain only the {@code k} largest entries of {@code b};
 *       this gives the new support and estimate {@code x_new}.</li>
 *   <li><b>Residual:</b>  {@code r = y − A x_new}.</li>
 * </ol>
 *
 * <h2>References</h2>
 * <ol>
 * <li>D. Needell and J. A. Tropp, "CoSaMP: Iterative signal recovery from
 *     incomplete and inaccurate samples", Appl. Comput. Harmon. Anal.,
 *     26(3):301–321, 2009.</li>
 * </ol>
 *
 * @param x       the recovered sparse signal (length {@code n}).
 * @param support the indices of the identified non-zero components.
 * @param iter    the number of iterations performed.
 * @author Haifeng Li
 */
public record CoSaMP(double[] x, int[] support, int iter) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CoSaMP.class);

    // =========================================================================
    //  Hyperparameters
    // =========================================================================

    /**
     * Hyperparameters for CoSaMP.
     *
     * @param sparsity the target sparsity level {@code k}.
     * @param maxIter  the maximum number of iterations.
     * @param tol      relative residual tolerance; the algorithm stops when
     *                 {@code ‖r‖₂ / ‖y‖₂ < tol}.
     */
    public record Options(int sparsity, int maxIter, double tol) {
        /** Constructs options. */
        public Options {
            if (sparsity <= 0) throw new IllegalArgumentException("sparsity must be > 0: " + sparsity);
            if (maxIter  <= 0) throw new IllegalArgumentException("maxIter must be > 0: " + maxIter);
            if (tol < 0)       throw new IllegalArgumentException("tol must be ≥ 0: " + tol);
        }

        /**
         * Returns properties for persistence.
         * @return the properties.
         */
        public Properties toProperties() {
            var p = new Properties();
            p.setProperty("smile.cs.cosamp.sparsity", Integer.toString(sparsity));
            p.setProperty("smile.cs.cosamp.maxIter",  Integer.toString(maxIter));
            p.setProperty("smile.cs.cosamp.tol",      Double.toString(tol));
            return p;
        }

        /**
         * Creates options from properties.
         * @param props the properties.
         * @return the options.
         */
        public static Options of(Properties props) {
            int    sparsity = Integer.parseInt( props.getProperty("smile.cs.cosamp.sparsity", "10"));
            int    maxIter  = Integer.parseInt( props.getProperty("smile.cs.cosamp.maxIter",  "50"));
            double tol      = Double.parseDouble(props.getProperty("smile.cs.cosamp.tol",     "1e-6"));
            return new Options(sparsity, maxIter, tol);
        }
    }

    // =========================================================================
    //  Public factory methods
    // =========================================================================

    /**
     * Recovers a sparse signal via CoSaMP.
     *
     * @param A        the {@code m × n} measurement matrix.
     * @param y        the {@code m}-dimensional measurement vector.
     * @param sparsity the target sparsity level {@code k}.
     * @return the recovered signal.
     */
    public static CoSaMP fit(Matrix A, double[] y, int sparsity) {
        return fit(A, y, new Options(sparsity, 50, 1e-6));
    }

    /**
     * Recovers a sparse signal via CoSaMP.
     *
     * @param A       the {@code m × n} measurement matrix.
     * @param y       the {@code m}-dimensional measurement vector.
     * @param options solver hyperparameters.
     * @return the recovered signal.
     */
    public static CoSaMP fit(Matrix A, double[] y, Options options) {
        int m = A.nrow();
        int n = A.ncol();
        if (y.length != m) {
            throw new IllegalArgumentException(
                    "y length %d does not match matrix rows %d".formatted(y.length, m));
        }

        int    k       = Math.min(options.sparsity, Math.min(m - 1, n));
        int    maxIter = options.maxIter;
        double tol     = options.tol;

        double ynorm = MathEx.norm(y);
        if (ynorm < 1e-30) {
            logger.info("CoSaMP: y is near zero; returning zero solution.");
            return new CoSaMP(new double[n], new int[0], 0);
        }

        double[] x = new double[n];          // current estimate
        double[] r = Arrays.copyOf(y, m);    // residual = y − A x

        int[] curSupport = new int[0];
        int iter = 0;

        for (iter = 0; iter < maxIter; iter++) {
            // --- Step 1: Proxy  e = A^T r ---
            double[] e = BasisPursuit.matvec(A, r, m, n, true);

            // --- Step 2: Identify 2k largest |e| entries ---
            int[] eIdx = largestIndices(e, 2 * k);

            // --- Step 3: Union Ω = curSupport ∪ eIdx ---
            int[] omega = union(curSupport, eIdx);

            // --- Step 4: LS on Ω: b = arg min ‖y − A_Ω b‖₂ ---
            double[] b_omega = OMP.leastSquares(A, y, omega, m, omega.length);

            // --- Step 5: Prune to k largest |b| ---
            // Build full-length vector, then extract support
            double[] bFull = new double[n];
            for (int s = 0; s < omega.length; s++) bFull[omega[s]] = b_omega[s];

            curSupport = largestIndices(bFull, k);

            // --- Step 6: Update x ---
            Arrays.fill(x, 0.0);
            double[] xSup = OMP.leastSquares(A, y, curSupport, m, curSupport.length);
            for (int s = 0; s < curSupport.length; s++) x[curSupport[s]] = xSup[s];

            // --- Step 7: Update residual r = y − A x ---
            double[] Ax = BasisPursuit.matvec(A, x, m, n, false);
            for (int i = 0; i < m; i++) r[i] = y[i] - Ax[i];

            double relRes = MathEx.norm(r) / ynorm;
            logger.debug("CoSaMP iter {}: rel residual = {}", iter, relRes);
            if (relRes < tol) {
                iter++;
                break;
            }
        }

        return new CoSaMP(x, curSupport, iter);
    }

    // =========================================================================
    //  Utilities
    // =========================================================================

    /**
     * Returns the indices of the {@code k} entries with the largest absolute
     * values in {@code v}.
     *
     * @param v the input vector.
     * @param k the number of entries to select.
     * @return sorted array of indices (ascending).
     */
    static int[] largestIndices(double[] v, int k) {
        int n = v.length;
        k = Math.min(k, n);
        // Partial selection sort (sufficient for k ≪ n)
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        // Partial selection: bring k largest to idx[0..k-1]
        for (int i = 0; i < k; i++) {
            int maxJ = i;
            double maxV = Math.abs(v[idx[i]]);
            for (int j = i + 1; j < n; j++) {
                double av = Math.abs(v[idx[j]]);
                if (av > maxV) { maxV = av; maxJ = j; }
            }
            Sort.swap(idx, i, maxJ);
        }
        int[] result = Arrays.copyOf(idx, k);
        Arrays.sort(result);
        return result;
    }

    /**
     * Returns the sorted union of two sorted integer arrays.
     *
     * @param a a sorted integer array.
     * @param b a sorted integer array.
     * @return the sorted union.
     */
    static int[] union(int[] a, int[] b) {
        int maxLen = a.length + b.length;
        int[] tmp = new int[maxLen];
        int ia = 0, ib = 0, k = 0;
        while (ia < a.length && ib < b.length) {
            if (a[ia] < b[ib]) tmp[k++] = a[ia++];
            else if (a[ia] > b[ib]) tmp[k++] = b[ib++];
            else { tmp[k++] = a[ia++]; ib++; }  // deduplicate
        }
        while (ia < a.length) tmp[k++] = a[ia++];
        while (ib < b.length) tmp[k++] = b[ib++];
        return Arrays.copyOf(tmp, k);
    }
}

