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

package smile.regression;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smile.math.DoubleArrayList;
import smile.math.Math;
import smile.math.kernel.MercerKernel;
import smile.util.MulticoreExecutor;

/**
 * Support vector regression. Like SVMs for classification, the model produced
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
public class SVR<T> implements Regression<T>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(SVR.class);

    /**
     * The default value for K_tt + K_ss - 2 * K_ts if kernel is not positive.
     */
    private static final double TAU = 1E-12;

    /**
     * The kernel function.
     */
    private MercerKernel<T> kernel;
    /**
     * The soft margin penalty parameter.
     */
    private double C = 1.0;
    /**
     * The loss function error threshold.
     */
    private double eps = 0.1;
    /**
     * The tolerance of convergence test.
     */
    private double tol = 1E-3;
    /**
     * Support vectors.
     */
    private List<SupportVector> sv = new ArrayList<>();
    /**
     * Threshold of decision function.
     */
    private double b = 0.0;
    /**
     * The number of support vectors.
     */
    private int nsv = 0;
    /**
     * The number of bounded support vectors.
     */
    private int nbsv = 0;
    /**
     * Most violating pair.
     * argmin gi of m_i < alpha_i
     * argmax gi of alpha_i < M_i
     * where m_i = min{0, y_i * C}
     * and   M_i = max{0, y_i * C}
     */
    private transient SupportVector svmin = null;
    private transient SupportVector svmax = null;
    private transient double gmin = Double.MAX_VALUE;
    private transient double gmax = -Double.MAX_VALUE;
    private transient int gminindex;
    private transient int gmaxindex;

    /**
     * Support vector.
     */
    class SupportVector implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Support vector.
         */
        T x;
        /**
         * Support vector response value.
         */
        double y;
        /**
         * Lagrangian multipliers of support vector.
         */
        double[] alpha = new double[2];
        /**
         * The soft margin penalty parameter.
         */
        private double C = 1.0;
        /**
         * Gradient y - K&alpha;.
         */
        double[] g = new double[2];
        /**
         * Kernel value k(x, x)
         */
        double k;
        /**
         * Kernel value cache.
         */
        DoubleArrayList kcache;
    }

    /**
     * Trainer for support vector regression.
     */
    public static class Trainer<T> extends RegressionTrainer<T> {
        /**
         * The kernel function.
         */
        private MercerKernel<T> kernel;
        /**
         * The soft margin penalty parameter.
         */
        private double C = 1.0;
        /**
         * The loss function error threshold.
         */
        private double eps = 0.001;
        /**
         * The tolerance of convergence test.
         */
        private double tol = 1E-3;

        /**
         * Constructor of trainer for binary SVMs.
         * @param kernel the kernel function.
         * @param eps the loss function error threshold.
         * @param C the soft margin penalty parameter.
         */
        public Trainer(MercerKernel<T> kernel, double eps, double C) {
            if (C < 0) {
                throw new IllegalArgumentException("Invalid soft margin penalty: " + C);
            }

            this.kernel = kernel;
            this.C = C;
            this.eps = eps;
        }

        /**
         * Sets the tolerance of convergence test.
         * 
         * @param tol the tolerance of convergence test.
         */
        public Trainer<T> setTolerance(double tol) {
            if (tol <= 0.0) {
                throw new IllegalArgumentException("Invalid tolerance of convergence test:" + tol);
            }

            this.tol = tol;
            return this;
        }

        @Override
        public SVR<T> train(T[] x, double[] y) {
            SVR<T> svr = new SVR<>(x, y, kernel, eps, C, tol);
            return svr;
        }
    }

    /**
     * Constructor.
     * @param x training instances.
     * @param y response variable.
     * @param kernel the kernel function.
     * @param eps the loss function error threshold.
     * @param C the soft margin penalty parameter.
     */
    public SVR(T[] x, double[] y, MercerKernel<T> kernel, double eps, double C) {
        this(x, y, null, kernel, eps, C);
    }
    
    /**
     * Constructor.
     * @param x training instances.
     * @param y response variable.
     * @param weight positive instance weight. The soft margin penalty
     * parameter for instance i will be weight[i] * C.
     * @param kernel the kernel function.
     * @param eps the loss function error threshold.
     * @param C the soft margin penalty parameter.
     */
    public SVR(T[] x, double[] y, double[] weight, MercerKernel<T> kernel, double eps, double C) {
        this(x, y, weight, kernel, eps, C, 1E-3);
    }
    
    /**
     * Constructor.
     * @param x training instances.
     * @param y response variable.
     * @param kernel the kernel function.
     * @param eps the loss function error threshold.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     */
    public SVR(T[] x, double[] y, MercerKernel<T> kernel, double eps, double C, double tol) {
        this(x, y, null, kernel, eps, C, tol);
    }
    
    /**
     * Constructor.
     * @param x training instances.
     * @param y response variable.
     * @param weight positive instance weight. The soft margin penalty
     * parameter for instance i will be weight[i] * C.
     * @param kernel the kernel function.
     * @param eps the loss function error threshold.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     */
    public SVR(T[] x, double[] y, double[] weight, MercerKernel<T> kernel, double eps, double C, double tol) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (weight != null && x.length != weight.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and instance weight don't match: %d != %d", x.length, weight.length));
        }

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
        
        int n = x.length;
        // Initialize support vectors.
        for (int i = 0; i < n; i++) {
            double w = 1.0;
            if (weight != null) {
                w = weight[i];
                if (w <= 0.0) {
                    throw new IllegalArgumentException("Invalid instance weight: " + w);
                }
            }
            
            SupportVector v = new SupportVector();
            v.x = x[i];
            v.y = y[i];
            v.C = w * C;
            v.g[0] = eps + y[i];
            v.g[1] = eps - y[i];
            v.k = kernel.k(x[i], x[i]);
            sv.add(v);
        }

        minmax();
        int phase = Math.min(n, 1000);
        for (int count = 1; smo(tol); count++) {
            if (count % phase == 0) {
                logger.info("SVR finishes {} SMO iterations", count);
            }
        }   
        logger.info("SVR finishes training");
        
        Iterator<SupportVector> iter = sv.iterator();
        while (iter.hasNext()) {
            SupportVector v = iter.next();
            if (v.alpha[0] == 0.0 && v.alpha[1] == 0.0) {
                iter.remove();
            }
        }

        // Cleanup kernel cache to free memory.
        nsv = sv.size();
        nbsv = 0;
        for (SupportVector v : sv) {
            v.kcache = null;
            if (v.alpha[0] == C || v.alpha[1] == C) {
                nbsv++;
            }
        }
        
        logger.info("{} support vectors, {} bounded", nsv, nbsv);
    }

    @Override
    public double predict(T x) {
        double f = b;

        for (SupportVector v : sv) {
            f += (v.alpha[1] - v.alpha[0]) * kernel.k(v.x, x);
        }

        return f;
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
            if (g > gmax && a < v.C) {
                svmax = v;
                gmax = g;
                gmaxindex = 0;
            }

            g = v.g[1];
            a = v.alpha[1];
            if (g < gmin && a < v.C) {
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
     * Task to calculate a row of kernel matrix.
     */
    class KernelTask implements Callable<double[]> {

        /**
         * The data vector.
         */
        SupportVector i;
        /**
         * The start index of data portion for this task.
         */
        int start;
        /**
         * The end index of data portion for this task.
         */
        int end;

        KernelTask(SupportVector i, int start, int end) {
            this.i = i;
            this.start = start;
            this.end = end;
        }

        @Override
        public double[] call() {
            double[] ki = new double[end - start];
            for (int j = start; j < end; j++) {
                ki[j-start] = kernel.k(i.x, sv.get(j).x); 
            }
            return ki;
        }
    }
    
    /**
     * Calculate the row of kernel matrix for a vector i.
     * @param i data vector to evaluate kernel matrix.
     */
    private void gram(SupportVector i) {
        int n = sv.size();
        int m = MulticoreExecutor.getThreadPoolSize();
        i.kcache = new DoubleArrayList(n);
        if (n < 100 || m < 2) {
            for (SupportVector v : sv) {
                i.kcache.add(kernel.k(i.x, v.x));
            }
        } else {
            List<KernelTask> tasks = new ArrayList<>(m + 1);
            int step = n / m;
            if (step < 100) {
                step = 100;
            }

            int start = 0;
            int end = step;
            for (int l = 0; l < m-1; l++) {
                tasks.add(new KernelTask(i, start, end));
                start += step;
                end += step;
            }
            tasks.add(new KernelTask(i, start, n));

            try {
                for (double[] ki : MulticoreExecutor.run(tasks)) {
                    for (double kij : ki) {
                        i.kcache.add(kij);
                    }
                }
            } catch (Exception ex) {
                for (SupportVector v : sv) {
                    i.kcache.add(kernel.k(i.x, v.x));
                }
            }         
        }
    }
    
    /**
     * Sequential minimal optimization.
     */
    private boolean smo(double epsgr) {
        SupportVector i = svmax;
        int ii = gmaxindex;
        double old_alpha_i = i.alpha[ii];

        if (i.kcache == null) {
            gram(i);
        }

        SupportVector j = svmin;        
        int jj = gminindex;        
        double old_alpha_j = j.alpha[jj];
        
        // Second order working set selection.
        double best = 0.0;
        double gi = ii == 0 ? -i.g[0] : i.g[1];
        for (SupportVector v : sv) {
            double curv = i.k + v.k - 2 * kernel.k(i.x, v.x);
            if (curv <= 0.0) curv = TAU;
            
            double gj = -v.g[0];
            if (v.alpha[0] > 0.0 && gj < gi) {
                double gain = -Math.sqr(gi - gj) / curv;
                if (gain < best) {
                    best = gain;
                    j = v;
                    jj = 0;
                    old_alpha_j = j.alpha[0];
                }
            }
            
            gj = v.g[1];
            if (v.alpha[1] < v.C && gj < gi) {
                double gain = -Math.sqr(gi - gj) / curv;
                if (gain < best) {
                    best = gain;
                    j = v;
                    jj = 1;
                    old_alpha_j = j.alpha[1];
                }                
            }
        }
        
        if (j.kcache == null) {
            gram(j);
        }
        
        // Determine curvature
        double curv = i.k + j.k - 2 * kernel.k(i.x, j.x);
        if (curv <= 0.0) curv = TAU;

        if (ii != jj) {
            double delta = (-i.g[ii] - j.g[jj]) / curv;
            double diff = i.alpha[ii] - j.alpha[jj];
            i.alpha[ii] += delta;
            j.alpha[jj] += delta;

            if (diff > 0.0) {
                // Region III
                if (j.alpha[jj] < 0.0) {
                    j.alpha[jj] = 0.0;
                    i.alpha[ii] = diff;
                }
            } else {
                // Region IV
                if (i.alpha[ii] < 0.0) {
                    i.alpha[ii] = 0.0;
                    j.alpha[jj] = -diff;
                }
            }
            
            if (diff > i.C - j.C) {
                // Region I
                if (i.alpha[ii] > i.C) {
                    i.alpha[ii] = i.C;
                    j.alpha[jj] = i.C - diff;
                }
            } else {
                // Region II
                if (j.alpha[jj] > j.C) {
                    j.alpha[jj] = j.C;
                    i.alpha[ii] = j.C + diff;
                }
            }
        } else {

            double delta = (i.g[ii] - j.g[jj]) / curv;
            double sum = i.alpha[ii] + j.alpha[jj];
            i.alpha[ii] -= delta;
            j.alpha[jj] += delta;

            if (sum > i.C) {
                if (i.alpha[ii] > i.C) {
                    i.alpha[ii] = i.C;
                    j.alpha[jj] = sum - i.C;
                }
            } else {
                if (j.alpha[jj] < 0) {
                    j.alpha[jj] = 0;
                    i.alpha[ii] = sum;
                }
            }

            if (sum > j.C) {
                if (j.alpha[jj] > j.C) {
                    j.alpha[jj] = j.C;
                    i.alpha[ii] = sum - j.C;
                }
            } else {
                if (i.alpha[ii] < 0) {
                    i.alpha[ii] = 0.0;
                    j.alpha[jj] = sum;
                }
            }
        }
        
        double delta_alpha_i = i.alpha[ii] - old_alpha_i;
        double delta_alpha_j = j.alpha[jj] - old_alpha_j;

        int si = 2 * ii - 1;
        int sj = 2 * jj - 1;
        for (int k = 0; k < sv.size(); k++) {
            SupportVector v = sv.get(k);
            v.g[0] -= si * i.kcache.get(k) * delta_alpha_i + sj * j.kcache.get(k) * delta_alpha_j;
            v.g[1] += si * i.kcache.get(k) * delta_alpha_i + sj * j.kcache.get(k) * delta_alpha_j;
        }

        // optimality test
        minmax();
        b = -(gmax + gmin) / 2;
        if (gmax - gmin < epsgr) {
            return false;
        }

        return true;
    }
    
    /**
     * Returns the soft margin penalty parameter.
     * @return the soft margin penalty parameter.
     */
    public double getC() {
    	return C;
    }
    
    /**
     * Returns the loss function error threshold.
     * @return the loss function error threshold.
     */
    public double getEpsilon() {
    	return eps;
    }
    
    /**
     * Returns the tolerance of convergence test.
     * @return tolerance of convergence test.
     */
    public double getTolerance() {
    	return tol;
    }
}
