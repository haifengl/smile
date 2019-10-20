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

package smile.base.svm;

import java.util.ArrayList;
import java.util.List;
import smile.math.MathEx;
import smile.math.kernel.MercerKernel;
import smile.regression.KernelMachine;

/**
 * Epsilon support vector regression. Like SVMs for classification, the model produced
 * by SVR depends only on a subset of the training data, because the cost
 * function ignores any training data close to the model prediction (within
 * a threshold &epsilon;).
 *
 * <h2>References</h2>
 * <ol>
 * <li> A. J Smola and B. Scholkopf. A Tutorial on Support Vector Regression.</li>
 * <li> Gary William Flake and Steve Lawrence. Efficient SVM Regression Training with SMO.</li>
 * <li> Christopher J. C. Burges. A Tutorial on Support Vector Machines for Pattern Recognition. Data Mining and Knowledge Discovery 2:121-167, 1998.</li>
 * <li> John Platt. Sequential Minimal Optimization: A Fast Algorithm for Training Support Vector Machines.</li>
 * <li> Rong-En Fan, Pai-Hsuen, and Chih-Jen Lin. Working Set Selection Using Second Order Information for Training Support Vector Machines. JMLR, 6:1889-1918, 2005.</li>
 * <li> Antoine Bordes, Seyda Ertekin, Jason Weston and Leon Bottou. Fast Kernel Classifiers with Online and Active Learning, Journal of Machine Learning Research, 6:1579-1619, 2005.</li>
 * <li> Tobias Glasmachers and Christian Igel. Second Order SMO Improves SVM Online and Active Learning.</li>
 * <li> Chih-Chung Chang and Chih-Jen Lin. LIBSVM: a Library for Support Vector Machines.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class SVR<T> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SVR.class);

    /**
     * The default value for K_tt + K_ss - 2 * K_ts if kernel is not positive.
     */
    private static final double TAU = 1E-12;

    /**
     * The kernel function.
     */
    private MercerKernel<T> kernel;
    /**
     * The loss function error threshold.
     */
    private double eps = 0.1;
    /**
     * The soft margin penalty parameter.
     */
    private double C = 1.0;
    /**
     * The tolerance of convergence test.
     */
    private double tol = 1E-3;
    /**
     * Support vectors.
     */
    private List<SupportVector> sv;
    /**
     * Threshold of decision function.
     */
    private double b = 0.0;
    /**
     * Most violating pair.
     * argmin gi of m_i < alpha_i
     * argmax gi of alpha_i < M_i
     * where m_i = min{0, y_i * C}
     * and   M_i = max{0, y_i * C}
     */
    private SupportVector svmin = null;
    private SupportVector svmax = null;
    private double gmin = Double.MAX_VALUE;
    private double gmax = -Double.MAX_VALUE;
    private int gminindex;
    private int gmaxindex;

    /**
     * The kernel matrix.
     */
    private double[][] K;

    /**
     * Support vector.
     */
    class SupportVector {
        /**
         * The index of support vector in training samples.
         */
        final int i;
        /**
         * Support vector.
         */
        final T x;
        /**
         * Lagrangian multipliers of support vector.
         */
        double[] alpha = new double[2];
        /**
         * Gradient y - K&alpha;.
         */
        double[] g = new double[2];
        /**
         * Kernel value k(x, x)
         */
        double k;

        SupportVector(int i, T x, double y) {
            this.i = i;
            this.x = x;
            g[0] = eps + y;
            g[1] = eps - y;
            k = kernel.k(x, x);
        }
    }

    /**
     * Constructor.
     * @param kernel the kernel function.
     */
    public SVR(MercerKernel<T> kernel, double eps, double C, double tol) {
        if (eps <= 0) {
            throw new IllegalArgumentException("Invalid error threshold: " + eps);
        }

        if (C < 0) {
            throw new IllegalArgumentException("Invalid soft margin penalty: " + C);
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance of convergence test:" + tol);
        }

        this.kernel = kernel;
        this.eps = eps;
        this.C = C;
        this.tol = tol;
    }

    /**
     * Fits a epsilon support vector regression model.
     * @param x training instances.
     * @param y response variable.
     */
    public KernelMachine<T> fit(T[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        int n = x.length;
        K = new double[n][];

        // Initialize support vectors.
        sv = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sv.add(new SupportVector(i, x[i], y[i]));
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
            SupportVector v = sv.get(i);
            if (v.alpha[0] == v.alpha[1]) {
                sv.set(i, null);
            } else {
                nsv++;
                if (v.alpha[0] == C || v.alpha[1] == C) {
                    bsv++;
                }
            }
        }

        double[] alpha = new double[nsv];
        @SuppressWarnings("unchecked")
        T[] vectors = (T[]) java.lang.reflect.Array.newInstance(x.getClass().getComponentType(), nsv);

        int i = 0;
        for (SupportVector v : sv) {
            if (v != null) {
                vectors[i] = v.x;
                alpha[i++] = v.alpha[1] - v.alpha[0];
            }
        }

        logger.info("{} samples, {} support vectors, {} bounded", n, nsv, bsv);

        return new KernelMachine<>(kernel, vectors, alpha, b);
    }

    /**
     * Find support vectors with smallest (of I_up) and largest (of I_down) gradients.
     */
    private void minmax() {
        gmin = Double.MAX_VALUE;
        gmax = -Double.MAX_VALUE;

        for (SupportVector v : sv) {
            double g = -v.g[0];
            double a = v.alpha[0];
            if (g < gmin && a > 0.0) {
                svmin = v;
                gmin = g;
                gminindex = 0;
            }
            if (g > gmax && a < C) {
                svmax = v;
                gmax = g;
                gmaxindex = 0;
            }

            g = v.g[1];
            a = v.alpha[1];
            if (g < gmin && a < C) {
                svmin = v;
                gmin = g;
                gminindex = 1;
            }
            if (g > gmax && a > 0.0) {
                svmax = v;
                gmax = g;
                gmaxindex = 1;
            }
        }
    }

    /**
     * Calculate the row of kernel matrix for a vector i.
     * @param v data vector to evaluate kernel matrix.
     */
    private double[] gram(SupportVector v) {
        if (K[v.i] == null) {
            double[] ki = new double[sv.size()];
            sv.stream().parallel().forEach(vi -> ki[vi.i] = kernel.k(v.x, vi.x));
            K[v.i] = ki;
        }
        return K[v.i];
    }

    /**
     * Sequential minimal optimization.
     */
    private boolean smo(double epsgr) {
        SupportVector v1 = svmax;
        int i = gmaxindex;
        double old_alpha_i = v1.alpha[i];

        double[] k1 = gram(v1);

        SupportVector v2 = svmin;
        int j = gminindex;
        double old_alpha_j = v2.alpha[j];

        // Second order working set selection.
        double best = 0.0;
        double gi = i == 0 ? -v1.g[0] : v1.g[1];
        for (SupportVector v : sv) {
            double curv = v1.k + v.k - 2 * k1[v.i];
            if (curv <= 0.0) curv = TAU;

            double gj = -v.g[0];
            if (v.alpha[0] > 0.0 && gj < gi) {
                double gain = -MathEx.sqr(gi - gj) / curv;
                if (gain < best) {
                    best = gain;
                    v2 = v;
                    j = 0;
                    old_alpha_j = v2.alpha[0];
                }
            }

            gj = v.g[1];
            if (v.alpha[1] < C && gj < gi) {
                double gain = -MathEx.sqr(gi - gj) / curv;
                if (gain < best) {
                    best = gain;
                    v2 = v;
                    j = 1;
                    old_alpha_j = v2.alpha[1];
                }
            }
        }

        double[] k2 = gram(v2);

        // Determine curvature
        double curv = v1.k + v2.k - 2 * k1[v2.i];
        if (curv <= 0.0) curv = TAU;

        if (i != j) {
            double delta = (-v1.g[i] - v2.g[j]) / curv;
            double diff = v1.alpha[i] - v2.alpha[j];
            v1.alpha[i] += delta;
            v2.alpha[j] += delta;

            if (diff > 0.0) {
                // Region III
                if (v2.alpha[j] < 0.0) {
                    v2.alpha[j] = 0.0;
                    v1.alpha[i] = diff;
                }
            } else {
                // Region IV
                if (v1.alpha[i] < 0.0) {
                    v1.alpha[i] = 0.0;
                    v2.alpha[j] = -diff;
                }
            }

            if (diff > 0) {
                // Region I
                if (v1.alpha[i] > C) {
                    v1.alpha[i] = C;
                    v2.alpha[j] = C - diff;
                }
            } else {
                // Region II
                if (v2.alpha[j] > C) {
                    v2.alpha[j] = C;
                    v1.alpha[i] = C + diff;
                }
            }
        } else {

            double delta = (v1.g[i] - v2.g[j]) / curv;
            double sum = v1.alpha[i] + v2.alpha[j];
            v1.alpha[i] -= delta;
            v2.alpha[j] += delta;

            if (sum > C) {
                if (v1.alpha[i] > C) {
                    v1.alpha[i] = C;
                    v2.alpha[j] = sum - C;
                }
            } else {
                if (v2.alpha[j] < 0) {
                    v2.alpha[j] = 0;
                    v1.alpha[i] = sum;
                }
            }

            if (sum > C) {
                if (v2.alpha[j] > C) {
                    v2.alpha[j] = C;
                    v1.alpha[i] = sum - C;
                }
            } else {
                if (v1.alpha[i] < 0) {
                    v1.alpha[i] = 0.0;
                    v2.alpha[j] = sum;
                }
            }
        }

        double delta_alpha_i = v1.alpha[i] - old_alpha_i;
        double delta_alpha_j = v2.alpha[j] - old_alpha_j;

        int si = 2 * i - 1;
        int sj = 2 * j - 1;
        for (SupportVector v : sv) {
            v.g[0] -= si * k1[v.i] * delta_alpha_i + sj * k2[v.i] * delta_alpha_j;
            v.g[1] += si * k1[v.i] * delta_alpha_i + sj * k2[v.i] * delta_alpha_j;
        }

        // optimality test
        minmax();
        b = -(gmax + gmin) / 2;
        return gmax - gmin > epsgr;
    }
}
