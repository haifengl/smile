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

import java.util.ArrayList;
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
    private double gmin = Double.MAX_VALUE;
    private double gmax = -Double.MAX_VALUE;

    /** Returns true if the support vector violates KKT conditions. */
    private boolean violateKKT(int i) {
        return (O[i] - rho) * alpha[i] > 0.0 || (rho - O[i]) * (C - alpha[i]) > 0.0;
    }

    /** Returns true if the support vector is not bounded. */
    private boolean unbounded(int i) {
        return alpha[i] > 0 && alpha[i] < C;
    }

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
        int n = x.length;
        this.x = x;
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
        for (int i = 0; i < n; i++) {
            double[] Ki = K[i];
            for (int j = 0; j < n; j++) {
                O[i] += Ki[j] * alpha[j];
            }
        }

        rho = MathEx.max(O);

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
            if (alpha[i] != 0.0) {
                nsv++;
                if (alpha[i] == C) {
                    bsv++;
                }
            }
        }

        double[] alpha = new double[nsv];
        @SuppressWarnings("unchecked")
        T[] vectors = (T[]) java.lang.reflect.Array.newInstance(x.getClass().getComponentType(), nsv);

        for (int i = 0; i < n; i++) {
            if (alpha[i] != 0.0) {
                vectors[i] = this.x[i];
                alpha[i++] = this.alpha[i];
            }
        }

        logger.info("{} samples, {} support vectors, {} bounded", n, nsv, bsv);

        return new KernelMachine<>(kernel, vectors, alpha, -rho);
    }

    /**
     * Find support vectors with smallest (of I_up) and largest (of I_down) gradients.
     */
    private void minmax() {
        gmin = Double.MAX_VALUE;
        gmax = -Double.MAX_VALUE;

        int n = x.length;
        for (int i = 0; i < n; i++) {
            double g = O[i];
            double a = alpha[i];
            if (g < gmin && a > 0.0) {
                svmin = i;
                gmin = g;
            }
            if (g > gmax && a < C) {
                svmax = i;
                gmax = g;
            }
        }
    }

    /**
     * Sequential minimal optimization.
     */
    private boolean smo(double epsgr) {
        int v1 = svmax;
        int v2 = svmin;

        double old_alpha_i = alpha[v1];
        double old_alpha_j = alpha[v2];
        double[] k1 = K[v1];
        double[] k2 = K[v2];

        // Determine curvature
        double curv = k1[v1] + k2[v2] - 2 * k1[v2];
        if (curv <= 0.0) curv = TAU;

        double delta = (O[v1] - O[v2]) / curv;
        alpha[v2] += delta;

        double diff = alpha[v1] - alpha[v2];
        alpha[v1] += delta;
        alpha[v2] += delta;

        if (diff > 0.0) {
            // Region III
            if (alpha[v2] < 0.0) {
                alpha[v2] = 0.0;
                alpha[v1] = diff;
            }
        } else {
            // Region IV
            if (alpha[v1] < 0.0) {
                alpha[v1] = 0.0;
                alpha[v2] = -diff;
            }
        }

        if (diff > 0) {
            // Region I
            if (alpha[v1] > C) {
                alpha[v1] = C;
                alpha[v2] = C - diff;
            }
        } else {
            // Region II
            if (alpha[v2] > C) {
                alpha[v2] = C;
                alpha[v1] = C + diff;
            }
        }

        double delta_alpha_i = alpha[v1] - old_alpha_i;
        double delta_alpha_j = alpha[v2] - old_alpha_j;

        int n = x.length;
        for (int i = 0; i < n; i++) {
            alpha[i] -= k1[i] * delta_alpha_i + k2[i] * delta_alpha_j;
        }

        // optimality test
        minmax();
        rho = -(gmax + gmin) / 2;
        return gmax - gmin > epsgr;
    }
}
