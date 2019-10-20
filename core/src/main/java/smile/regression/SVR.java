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

package smile.regression;

import smile.base.svm.LinearKernelMachine;
import smile.util.SparseArray;
import smile.math.kernel.BinarySparseLinearKernel;
import smile.math.kernel.LinearKernel;
import smile.math.kernel.MercerKernel;
import smile.math.kernel.SparseLinearKernel;

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
public class SVR {
    /**
     * Fits a linear epsilon-SVR.
     * @param x training samples.
     * @param y response variable.
     * @param eps threshold parameter. There is no penalty associated with
     *            samples which are predicted within distance epsilon from
     *            the actual value. Decreasing epsilon forces closer fitting
     *            to the calibration/training data.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     */
    public static Regression<double[]> fit(double[][] x, double[] y, double eps, double C, double tol) {
        smile.base.svm.SVR<double[]> svr = new smile.base.svm.SVR<>(new LinearKernel(), eps, C, tol);
        KernelMachine<double[]> svm = svr.fit(x, y);

        return new Regression<double[]>() {
            LinearKernelMachine model = LinearKernelMachine.of(svm);

            @Override
            public double predict(double[] x) {
                return model.f(x);
            }
        };
    }

    /**
     * Fits a linear epsilon-SVR of binary sparse data.
     * @param x training samples.
     * @param y response variable.
     * @param eps threshold parameter. There is no penalty associated with
     *            samples which are predicted within distance epsilon from
     *            the actual value. Decreasing epsilon forces closer fitting
     *            to the calibration/training data.
     * @param p the dimension of input vector.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     */
    public static Regression<int[]> fit(int[][] x, double[] y, int p, double eps, double C, double tol) {
        smile.base.svm.SVR<int[]> svr = new smile.base.svm.SVR<>(new BinarySparseLinearKernel(), eps, C, tol);
        KernelMachine<int[]> svm = svr.fit(x, y);

        return new Regression<int[]>() {
            LinearKernelMachine model = LinearKernelMachine.binary(p, svm);

            @Override
            public double predict(int[] x) {
                return model.f(x);
            }
        };
    }

    /**
     * Fits a linear epsilon-SVR of sparse data.
     * @param x training samples.
     * @param y response variable.
     * @param eps threshold parameter. There is no penalty associated with
     *            samples which are predicted within distance epsilon from
     *            the actual value. Decreasing epsilon forces closer fitting
     *            to the calibration/training data.
     * @param p the dimension of input vector.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     */
    public static Regression<SparseArray> fit(SparseArray[] x, double[] y, int p, double eps, double C, double tol) {
        smile.base.svm.SVR<SparseArray> svr = new smile.base.svm.SVR<>(new SparseLinearKernel(), eps, C, tol);
        KernelMachine<SparseArray> svm = svr.fit(x, y);

        return new Regression<SparseArray>() {
            LinearKernelMachine model = LinearKernelMachine.sparse(p, svm);

            @Override
            public double predict(SparseArray x) {
                return model.f(x) > 0 ? +1 : -1;
            }
        };
    }

    /**
     * Fits a epsilon-SVR.
     * @param x training samples.
     * @param y response variable.
     * @param eps threshold parameter. There is no penalty associated with
     *            samples which are predicted within distance epsilon from
     *            the actual value. Decreasing epsilon forces closer fitting
     *            to the calibration/training data.
     * @param kernel the kernel function.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     */
    public static <T> KernelMachine<T> fit(T[] x, double[] y, MercerKernel<T> kernel, double eps, double C, double tol) {
        smile.base.svm.SVR<T> svr = new smile.base.svm.SVR<T>(kernel, eps, C, tol);
        return svr.fit(x, y);
    }
}
