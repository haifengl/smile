/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.anomaly;

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
        OCSVM<T> svm = new OCSVM<>(kernel, options.nu, options.tol);
        KernelMachine<T> model = svm.fit(x);
        return new SVM<>(model.kernel(), model.vectors(), model.weights(), model.intercept());
    }
}
