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

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import smile.math.MathEx;
import smile.math.SparseArray;
import smile.math.kernel.MercerKernel;

/**
 * LASVM is an approximate SVM solver that uses online approximation.
 * It reaches accuracies similar to that of a real SVM after performing
 * a single sequential pass through the training examples. Further
 * benefits can be achieved using selective sampling techniques to
 * choose which example should be considered next.
 * LASVM requires considerably less memory than a regular SVM solver.
 * This becomes a considerable speed advantage for large training sets.
 */
public class LASVM<T> implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LASVM.class);

    /**
     * The default value for K_tt + K_ss - 2 * K_ts if kernel is not positive.
     */
    private static final double TAU = 1E-12;

    /**
     * The kernel function.
     */
    private MercerKernel<T> kernel;
    /**
     * The soft margin penalty parameter for positive samples.
     */
    private double Cp = 1.0;
    /**
     * The soft margin penalty parameter for negative samples.
     */
    private double Cn = 1.0;
    /**
     * The tolerance of convergence test.
     */
    private double tol = 1E-3;
    /**
     * Support vectors.
     */
    private LinkedList<SupportVector<T>> sv = new LinkedList<>();
    /**
     * Threshold of decision function.
     */
    private double b = 0.0;
    /**
     * The number of bounded support vectors.
     */
    private int bsv = 0;
    /**
     * True if minmax is already called after update.
     */
    private boolean minmaxflag = false;
    /**
     * Most violating pair.
     * argmin gi of m_i < alpha_i
     * argmax gi of alpha_i < M_i
     * where m_i = min{0, y_i * C}
     * and   M_i = max{0, y_i * C}
     */
    private SupportVector<T> svmin = null;
    private SupportVector<T> svmax = null;
    private double gmin = Double.MAX_VALUE;
    private double gmax = -Double.MAX_VALUE;

    /**
     * The kernel value cache.
     */
    private SparseArray[] cache;

    /**
     * Constructor.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     */
    public LASVM(MercerKernel<T> kernel, double C, double tol) {
        this(kernel, C, C, tol);
    }

    /**
     * Constructor.
     * @param Cp the soft margin penalty parameter for positive instances.
     * @param Cn the soft margin penalty parameter for negative instances.
     * @param tol the tolerance of convergence test.
     */
    public LASVM(MercerKernel<T> kernel, double Cp, double Cn, double tol) {
        this.kernel = kernel;
        this.Cp = Cp;
        this.Cn = Cn;
        this.tol = tol;
    }

    /**
     * Trains the model.
     * @param x training samples.
     * @param y training labels.
     */
    public KernelMachine<T> fit(T[] x, int[] y) {
        return fit(x, y, 2);
    }

    /**
     * Trains the model.
     * @param x training samples.
     * @param y training labels.
     * @param epoch the number of epochs, usually 1 or 2 is sufficient.
     */
    public KernelMachine<T>  fit(T[] x, int[] y, int epoch) {
        cache = new SparseArray[x.length];
        init(x, y);

        for (int i = 0; i < epoch; i++) {
            update(x, y);
        }
        finish();

        int n = sv.size();
        @SuppressWarnings("unchecked")
        T[] vectors = (T[]) java.lang.reflect.Array.newInstance(x.getClass().getComponentType(), n);
        double[] alpha = new double[n];
        for (int i = 0; i < n; i++) {
            SupportVector<T> v = sv.get(i);
            vectors[i] = v.x;
            alpha[i] = v.alpha;
        }
        return new KernelMachine<>(kernel, vectors, alpha, b);
    }

    /**
     * Trains the SVM with the given dataset for one epoch. The caller may
     * call this method multiple times to obtain better accuracy although
     * one epoch is usually sufficient. After calling this method sufficient
     * times (usually 1 or 2), the users should call {@link #finish()}
     * to further process support vectors.
     */
    private void update(T[] x, int[] y) {
        // train SVM in a stochastic order.
        for (int i : MathEx.permutate(x.length)) {
            process(i, x[i], y[i]);

            do {
                reprocess(tol); // at least one call to reprocess
                minmax();
            } while (gmax - gmin > 1000);
        }
    }

    /**
     * Initialize the SVM with some instances as support vectors.
     */
    private void init(T[] x, int[] y) {
        int few = 5;
        int cp = 0, cn = 0;
        for (int i : MathEx.permutate(x.length)) {
            if (y[i] == 1 && cp < few) {
                if (process(i, x[i], y[i])) cp++;
            } else if (y[i] == -1 && cn < few) {
                if (process(i, x[i], y[i])) cn++;
            }

            if (cp >= few && cn >= few) break;
        }
    }

    /**
     * Find support vectors with smallest (of I_up) and largest (of I_down) gradients.
     */
    private void minmax() {
        if (minmaxflag) return;

        gmin = Double.MAX_VALUE;
        gmax = -Double.MAX_VALUE;

        for (SupportVector<T> v : sv) {
            if (v != null) {
                double gi = v.g;
                double ai = v.alpha;
                if (gi < gmin && ai > v.cmin) {
                    svmin = v;
                    gmin = gi;
                }
                if (gi > gmax && ai < v.cmax) {
                    svmax = v;
                    gmax = gi;
                }
            }
        }

        minmaxflag = true;
    }

    /** Computes the kernel cache for a support vector. */
    private SparseArray cache(SupportVector<T> v) {
        int i = v.i;

        SparseArray cachei = cache[i];
        if (cachei == null) {
            cachei = new SparseArray();
            T x = v.x;
            for (SupportVector<T> v2 : sv) {
                cachei.append(v2.i, kernel.k(x, v2.x));
            }
        }

        return cachei;
    }

    /**
     * Returns the cached kernel value.
     * @param i the index of support vector.
     * @param j the index of support vector.
     */
    private double k(int i, int j) {
        return cache[i].get(j);
    }

    /**
     * Sequential minimal optimization.
     * @param v1 the first vector of working set.
     * @param v2 the second vector of working set.
     * @param epsgr the tolerance of convergence test.
     */
    private boolean smo(SupportVector<T> v1, SupportVector<T> v2, double epsgr) {
        // SMO working set selection
        // Determine coordinate to process
        if (v1 == null && v2 == null) {
            minmax();

            if (gmax > -gmin) {
                v2 = svmax;
            } else {
                v1 = svmin;
            }
        }

        if (v2 == null) {
            SparseArray k1 = cache(v1);

            // determine imax
            double km = v1.k;
            double gm = v1.g;
            double best = 0.0;
            for (SupportVector<T> v : sv) {
                double Z = v.g - gm;
                double k = k1.get(v.i);
                double curv = km + v.k - 2.0 * k;
                if (curv <= 0.0) curv = TAU;
                double mu = Z / curv;
                if ((mu > 0.0 && v.alpha < v.cmax) || (mu < 0.0 && v.alpha > v.cmin)) {
                    double gain = Z * mu;
                    if (gain > best) {
                        best = gain;
                        v2 = v;
                    }
                }
            }
        }

        if (v1 == null) {
            SparseArray k2 = cache(v2);

            // determine imin
            double km = v2.k;
            double gm = v2.g;
            double best = 0.0;
            for (SupportVector<T> v : sv) {
                double Z = gm - v.g;
                double k = k2.get(v.i);
                double curv = km + v.k - 2.0 * k;
                if (curv <= 0.0) curv = TAU;

                double mu = Z / curv;
                if ((mu > 0.0 && v.alpha > v.cmin) || (mu < 0.0 && v.alpha < v.cmax)) {
                    double gain = Z * mu;
                    if (gain > best) {
                        best = gain;
                        v1 = v;
                    }
                }
            }
        }

        if (v1 == null || v2 == null) {
            return false;
        }

        SparseArray k1 = cache(v1);
        SparseArray k2 = cache(v2);

        // Determine curvature
        double curv = v1.k + v2.k - 2 * kernel.k(v1.x, v2.x);
        if (curv <= 0.0) curv = TAU;

        double step = (v2.g - v1.g) / curv;

        // Determine maximal step
        if (step >= 0.0) {
            double ostep = v1.alpha - v1.cmin;
            if (ostep < step) {
                step = ostep;
            }
            ostep = v2.cmax - v2.alpha;
            if (ostep < step) {
                step = ostep;
            }
        } else {
            double ostep = v2.cmin - v2.alpha;
            if (ostep > step) {
                step = ostep;
            }
            ostep = v1.alpha - v1.cmax;
            if (ostep > step) {
                step = ostep;
            }
        }

        // Perform update
        v1.alpha -= step;
        v2.alpha += step;
        for (int i = 0; i < sv.size(); i++) {
            SupportVector v = sv.get(i);
            if (v != null) {
                v.g -= step * (k2.get(i) - k1.get(i));
            }
        }

        minmaxflag = false;

        // optimality test
        minmax();
        b = (gmax + gmin) / 2;
        return gmax - gmin > epsgr;
    }

    /**
     * Process a new sample.
     */
    private boolean process(int i, T x, int y) {
        if (y != +1 && y != -1) {
            throw new IllegalArgumentException("Invalid label: " + y);
        }

        // Compute gradient
        double g = y;
        SparseArray cachei = new SparseArray();

        for (SupportVector<T> v : sv) {
            // Bail out if already in expansion?
            if (v.x == x) return true;

            double k = kernel.k(v.x, x);
            g -= v.alpha * k;
            cachei.append(v.i, k);
            cache[v.i].append(i, k);
        }

        // Decide insertion
        minmax();
        if (gmin < gmax) {
            if ((y > 0 && g < gmin) || (y < 0 && g > gmax)) {
                return false;
            }
        }

        // Insert
        SupportVector<T> v = new SupportVector<>(i, x, y, 0.0, g, Cp, Cn, kernel.k(x, x));
        sv.addFirst(v);
        cache[i] = cachei;

        // Process
        if (y > 0) {
            smo(null, v, 0.0);
        } else {
            smo(v, null, 0.0);
        }

        minmaxflag = false;
        return true;
    }

    /**
     * Reprocess support vectors.
     * @param epsgr the tolerance of convergence test.
     */
    private boolean reprocess(double epsgr) {
        boolean status = smo(null, null, epsgr);
        evict();
        return status;
    }

    /**
     * Call reprocess until converge.
     */
    private void finish() {
        finish(tol, sv.size());

        bsv = 0;
        for (SupportVector v : sv) {
            if (v.alpha == v.cmin || v.alpha == v.cmax) {
                bsv++;
            }
        }

        logger.info("{} support vectors, {} bounded\n", sv.size(), bsv);
    }

    /**
     * Call reprocess until converge.
     * @param epsgr the tolerance of convergence test.
     * @param maxIter the maximum number of iterations.
     */
    private void finish(double epsgr, int maxIter) {
        logger.info("SVM is finalizing the training by reprocess.");
        for (int count = 1; count <= maxIter && smo(null, null, epsgr); count++) {
            if (count % 1000 == 0) {
                logger.info("finishing {} reprocess iterations.", count);
            }
        }
        evict();
        logger.info("LASVM finished the reprocess.");
    }

    private void evict() {
        minmax();

        Iterator<SupportVector<T>> iter = sv.iterator();
        while (iter.hasNext()) {
            SupportVector v = iter.next();
            if (v.alpha == 0) {
                if ((v.g >= gmax && 0 >= v.cmax) || (v.g <= gmin && 0 <= v.cmin)) {
                    iter.remove();
                }
            }
        }
    }
}
