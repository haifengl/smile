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
package smile.anomaly;

import java.util.Arrays;
import java.util.Properties;
import smile.math.kernel.MercerKernel;
import smile.model.svm.KernelMachine;
import smile.model.svm.OCSVM;

/**
 * One-class support vector machines for novelty detection.
 * One-class SVM relies on identifying the smallest hypersphere
 * consisting of all the data points. Therefore, it is sensitive to outliers.
 * If the training data is not contaminated by outliers, the model is best
 * suited for novelty detection.
 * <p>
 * The {@link #score(Object)} method (inherited from {@link KernelMachine})
 * returns the raw decision function value: <em>positive</em> values indicate
 * inliers (normal observations) and <em>negative</em> values indicate
 * anomalies. This convention is the opposite of {@link IsolationForest},
 * where higher scores signal anomalies.
 *
 * <h2>References</h2>
 * <ol>
 * <li>B. Schölkopf, J. Platt, J. Shawe-Taylor, A. J. Smola, and R. C. Williamson. Estimating the support of a high-dimensional distribution. Neural Computation, 2001.</li>
 * <li>Jia Jiong and Zhang Hao-ran. A Fast Learning Algorithm for One-Class Support Vector Machine. ICNC 2007.</li>
 * </ol>
 *
 * @param <T> the data type of model input objects.
 *
 * @author Haifeng Li
 */
public class SVM<T> extends KernelMachine<T>  {
    /**
     * SVM hyperparameters.
     * @param nu the parameter sets an upper bound on the fraction of outliers
     *           (training examples regarded out-of-class) and it is a lower
     *           bound on the number of training examples used as Support Vector.
     * @param tol the tolerance of convergence test.
     */
    public record Options(double nu, double tol) {
        /** Constructor. */
        public Options {
            if (nu <= 0 || nu > 1) {
                throw new IllegalArgumentException("Invalid nu: " + nu);
            }

            if (tol <= 0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }
        }

        /** Constructor. */
        public Options() {
            this(0.5, 1E-3);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.svm.nu", Double.toString(nu));
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
            double nu = Double.parseDouble(props.getProperty("smile.svm.nu", "0.5"));
            double tol = Double.parseDouble(props.getProperty("smile.svm.tolerance", "1E-3"));
            return new SVM.Options(nu, tol);
        }
    }

    /**
     * Constructor.
     * @param kernel Kernel function.
     * @param vectors The support vectors.
     * @param weight The weights of instances.
     * @param b The intercept;
     */
    public SVM(MercerKernel<T> kernel, T[] vectors, double[] weight, double b) {
        super(kernel, vectors, weight, b);
    }

    /**
     * Fits a one-class SVM.
     * @param x training samples.
     * @param kernel the kernel function.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> SVM<T> fit(T[] x, MercerKernel<T> kernel) {
        return fit(x, kernel, new Options());
    }

    /**
     * Fits a one-class SVM.
     * @param x training samples.
     * @param kernel the kernel function.
     * @param options the hyperparameters.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> SVM<T> fit(T[] x, MercerKernel<T> kernel, Options options) {
        if (x == null || x.length == 0) {
            throw new IllegalArgumentException("Training data is empty");
        }
        if (kernel == null) {
            throw new IllegalArgumentException("kernel is null");
        }
        if (options == null) {
            throw new IllegalArgumentException("options is null");
        }

        OCSVM<T> svm = new OCSVM<>(kernel, options.nu, options.tol);
        KernelMachine<T> model = svm.fit(x);
        return new SVM<>(model.kernel(), model.vectors(), model.weights(), model.intercept());
    }

    /**
     * Returns the decision function values for an array of samples.
     * Positive values indicate inliers; negative values indicate anomalies.
     *
     * @param x the samples.
     * @return the decision function values.
     */
    public double[] score(T[] x) {
        return Arrays.stream(x).parallel().mapToDouble(this::score).toArray();
    }

    /**
     * Predicts whether a sample is an anomaly. A sample is considered an
     * anomaly when its decision function value is below {@code threshold}.
     * Use {@code threshold = 0.0} for the natural SVM decision boundary.
     *
     * @param x the sample.
     * @param threshold the decision value threshold below which the sample
     *                  is declared an anomaly.
     * @return {@code true} if the sample is predicted as an anomaly.
     */
    public boolean predict(T x, double threshold) {
        return score(x) < threshold;
    }
}
