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
package smile.regression;

import java.util.Properties;
import smile.math.kernel.*;
import smile.util.SparseArray;

/**
 * Epsilon support vector regression. Like SVMs for classification, the model
 * produced by SVR depends only on a subset of the training data, because
 * the cost function ignores any training data close to the model prediction
 * (within a threshold &epsilon;).
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
public class SVM {
    /** Private constructor to prevent object creation. */
    private SVM() {

    }

    /**
     * SVM hyperparameters.
     * @param eps the parameter of epsilon-insensitive hinge loss.
     *            There is no penalty associated with samples which are
     *            predicted within distance epsilon from the actual value.
     *            Decreasing epsilon forces closer fitting
     *            to the calibration/training data.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     */
    public record Options(double eps, double C, double tol) {
        /** Constructor. */
        public Options {
            if (eps <= 0) {
                throw new IllegalArgumentException("Invalid epsilon: " + eps);
            }
            if (tol <= 0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }
            if (C < 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + C);
            }
        }

        /**
         * Constructor.
         * @param eps the parameter of epsilon-insensitive hinge loss.
         *            There is no penalty associated with samples which are
         *            predicted within distance epsilon from the actual value.
         *            Decreasing epsilon forces closer fitting
         *            to the calibration/training data.
         * @param C the soft margin penalty parameter.
         */
        public Options(double eps, double C) {
            this(eps, C, 1E-3);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.svm.epsilon", Double.toString(eps));
            props.setProperty("smile.svm.C", Double.toString(C));
            props.setProperty("smile.svm.tolerance", Double.toString(tol));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            double eps = Double.parseDouble(props.getProperty("smile.svm.epsilon", "1.0"));
            double C = Double.parseDouble(props.getProperty("smile.svm.C", "1.0"));
            double tol = Double.parseDouble(props.getProperty("smile.svm.tolerance", "1E-3"));
            return new Options(eps, C, tol);
        }
    }

    /**
     * Fits a linear epsilon-SVR.
     * @param x training samples.
     * @param y response variable.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static Regression<double[]> fit(double[][] x, double[] y, Options options) {
        smile.model.svm.SVR<double[]> svr = new smile.model.svm.SVR<>(new LinearKernel(), options.eps, options.C, options.tol);
        return new LinearSVM(svr.fit(x, y));
    }

    /**
     * Fits a linear epsilon-SVR of binary sparse data.
     * @param x training samples.
     * @param y response variable.
     * @param p the dimension of input vector.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static BinarySparseLinearSVM fit(int[][] x, double[] y, int p, Options options) {
        smile.model.svm.SVR<int[]> svr = new smile.model.svm.SVR<>(new BinarySparseLinearKernel(), options.eps, options.C, options.tol);
        return new BinarySparseLinearSVM(p, svr.fit(x, y));
    }

    /**
     * Fits a linear epsilon-SVR of sparse data.
     * @param x training samples.
     * @param y response variable.
     * @param p the dimension of input vector.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static SparseLinearSVM fit(SparseArray[] x, double[] y, int p, Options options) {
        smile.model.svm.SVR<SparseArray> svr = new smile.model.svm.SVR<>(new SparseLinearKernel(), options.eps, options.C, options.tol);
        return new SparseLinearSVM(p, svr.fit(x, y));
    }

    /**
     * Fits an epsilon-SVR.
     * @param x training samples.
     * @param y response variable.
     * @param kernel the kernel function.
     * @param options the hyperparameters.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> KernelMachine<T> fit(T[] x, double[] y, MercerKernel<T> kernel, Options options) {
        smile.model.svm.SVR<T> svr = new smile.model.svm.SVR<>(kernel, options.eps, options.C, options.tol);
        return svr.fit(x, y);
    }

    /**
     * Fits an epsilon-SVR.
     * @param x training samples.
     * @param y response variable.
     * @param params the hyperparameters.
     * @return the model.
     */
    public static Regression<double[]> fit(double[][] x, double[] y, Properties params) {
        MercerKernel<double[]> kernel = MercerKernel.of(params.getProperty("smile.svm.kernel", "linear"));
        if (kernel instanceof LinearKernel) {
            return SVM.fit(x, y, Options.of(params));
        } else {
            return SVM.fit(x, y, kernel, Options.of(params));
        }
    }
}
