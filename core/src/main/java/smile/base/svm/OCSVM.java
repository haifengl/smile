/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.base.svm;

import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.kernel.MercerKernel;

/**
 * One-class support vector machine.
 *
 * @author Haifeng Li
 */
public class OCSVM<T> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OCSVM.class);

    /**
     * The default value for K_tt + K_ss - 2 * K_ts if kernel is not positive.
     */
    private static final double TAU = 1E-12;

    /**
     * The kernel function.
     */
    private final MercerKernel<T> kernel;
    /**
     * The parameter sets an upper bound on the fraction of outliers
     * (training examples regarded out-of-class). It is also the lower
     * bound on the number of training examples used as Support Vector.
     */
    private final double nu;
    /**
     * The tolerance of convergence test.
     */
    private final double tol;
    /**
     * The upper bound of Lagrangian multiplier 1 / (nu * n).
     */
    private double C;
    /**
     * Support vectors.
     */
    private T[] x;
    /**
     * Threshold of decision function.
     */
    private double rho;
    /**
     * Lagrangian multiplier of support vector.
     */
    private double[] alpha;
    /**
     * Ki * alpha.
     */
    private double[] O;
    /**
     * The kernel matrix.
     */
    private double[][] K;
    /**
     * Most violating pair.
     * argmin gi of m_i < alpha_i
     * argmax gi of alpha_i < M_i
     * where m_i = min{0, y_i * C}
     * and   M_i = max{0, y_i * C}
     */
    private int svmin = -1;
    private int svmax = -1;
    private double omin = Double.MAX_VALUE;
    private double omax = -Double.MAX_VALUE;

    /**
     * Constructor.
     * @param kernel the kernel function.
     * @param nu the parameter sets an upper bound on the fraction of outliers
     *           (training examples regarded out-of-class) and it is a lower
     *           bound on the number of training examples used as Support Vector.
     * @param tol the tolerance of convergence test.
     */
    public OCSVM(MercerKernel<T> kernel, double nu, double tol) {
        if (nu <= 0 || nu > 1) {
            throw new IllegalArgumentException("Invalid nu: " + nu);
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance of convergence test:" + tol);
        }

        this.kernel = kernel;
        this.nu = nu;
        this.tol = tol;
    }

    /**
     * Fits an one-class support vector machine.
     * @param x training instances.
     * @return the model.
     */
    public KernelMachine<T> fit(T[] x) {
        this.x = x;
        int n = x.length;
        K = new double[n][n];
        IntStream.range(0, n).parallel().forEach(i -> {
            T xi = x[i];
            double[] Ki = K[i];
            for (int j = 0; j < n; j++) {
                Ki[j] = kernel.k(xi, x[j]);
            }
        });

        // Initialize support vectors.
        int vl = (int) Math.round(nu * n);
        C = 1.0 / vl;

        int[] index = MathEx.permutate(n);
        alpha = new double[n];
        for (int i = 0; i < vl; i++) {
            alpha[index[i]] = C;
        }

        O = new double[n];
        rho = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double[] Ki = K[i];
            for (int j = 0; j < n; j++) {
                O[i] += Ki[j] * alpha[j];
            }

            if (alpha[i] > 0 && rho < O[i]) {
                rho = O[i];
            }
        }

        minmax();
        int phase = Math.min(n, 1000);
        for (int count = 1; smo(tol); count++) {
            if (count % phase == 0) {
                logger.info("{} SMO iterations", count);
            }
        }

        int nsv = 0;
        int bsv = 0;

        for (int i = 0; i < n; i++) {
            if (alpha[i] > 0.0) {
                nsv++;
                if (alpha[i] == C) {
                    bsv++;
                }
            }
        }

        @SuppressWarnings("unchecked")
        T[] vectors = (T[]) java.lang.reflect.Array.newInstance(x.getClass().getComponentType(), nsv);
        double[] weight = new double[nsv];
        // Since we want the final decision function to evaluate to 1 for points
        // which lie on the margin, we need to subtract this tol from the offset rho.
        // Note that in the paper, the decision function is w * x - rho. But in
        // other SVM and KernelMachine class, we have w * x + b. So we set b = -rho.
        double b = -(rho - tol);

        for (int i = 0, j = 0; i < n; i++) {
            if (alpha[i] > 0.0) {
                vectors[j] = x[i];
                weight[j++] = alpha[i];
            }
        }

        logger.info("{} samples, {} support vectors, {} bounded", n, nsv, bsv);

        return new KernelMachine<>(kernel, vectors, weight, b);
    }

    /**
     * Find support vectors with smallest (of I_up) and largest (of I_down) gradients.
     */
    private void minmax() {
        svmin = -1;
        svmax = -1;
        omin = Double.MAX_VALUE;
        omax = -Double.MAX_VALUE;

        int n = x.length;
        for (int i = 0; i < n; i++) {
            double oi = O[i];
            double ai = alpha[i];
            if (oi < omin && ai < C) {
                svmin = i;
                omin = oi;
            }
            if (oi > omax && ai > 0) {
                svmax = i;
                omax = oi;
            }
        }
    }

    /**
     * Sequential minimal optimization.
     */
    private boolean smo(double epsgr) {
        int v1 = svmin;
        int v2 = svmax;

        // Second order working set selection.
        int n = x.length;
        if (v2 < 0) {
            // determine imax
            double O1 = O[v1];
            double[] K1 = K[v1];
            double k11 = K1[v1];
            double best = 0.0;
            for (int i = 0; i < n; i++) {
                double Z = O[i] - O1;
                double curv = k11 + K[i][i] - 2 * K1[i];
                if (curv <= 0.0) curv = TAU;

                double mu = Z / curv;
                if (O[i] > O1 && alpha[i] > 0) {
                    double gain = -Z * mu;
                    if (gain < best) {
                        best = gain;
                        v2 = i;
                    }
                }
            }
        }

        if (v1 < 0) {
            // determine imin
            double O2 = O[v2];
            double[] K2 = K[v2];
            double k22 = K2[v2];
            double best = 0.0;
            for (int i = 0; i < n; i++) {
                double Z = O2 - O[i];
                double curv = k22 + K[i][i] - 2.0 * K2[i];
                if (curv <= 0.0) curv = TAU;

                double mu = Z / curv;
                if (O[i] < O2 && alpha[i] < C) {
                    double gain = -Z * mu;
                    if (gain < best) {
                        best = gain;
                        v1 = i;
                    }
                }
            }
        }

        if (v1 < 0 || v2 < 0) return false;

        double old_alpha1 = alpha[v1];
        double old_alpha2 = alpha[v2];
        double[] k1 = K[v1];
        double[] k2 = K[v2];

        // Determine curvature
        double curv = K[v1][v1] + K[v2][v2] - 2 * K[v1][v2];
        if (curv <= 0.0) curv = TAU;
        double delta = (O[v1] - O[v2]) / curv;
        double sum = alpha[v1] + alpha[v2];
        alpha[v2] += delta;
        alpha[v1] -= delta;

        if (sum > C) {
            if (alpha[v1] > C) {
                alpha[v1] = C;
                alpha[v2] = sum - C;
            }
        } else {
            if (alpha[v2] < 0) {
                alpha[v2] = 0;
                alpha[v1] = sum;
            }
        }

        if (sum > C) {
            if (alpha[v2] > C) {
                alpha[v2] = C;
                alpha[v1] = sum - C;
            }
        } else {
            if (alpha[v1] < 0) {
                alpha[v1] = 0.0;
                alpha[v2] = sum;
            }
        }

        double delta_alpha1 = alpha[v1] - old_alpha1;
        double delta_alpha2 = alpha[v2] - old_alpha2;
        for (int i = 0; i < n; i++) {
            O[i] += k1[i] * delta_alpha1 + k2[i] * delta_alpha2;
        }

        rho = (omax + omin) / 2;
        // optimality test
        minmax();
        return omax - omin > epsgr;
    }
}
