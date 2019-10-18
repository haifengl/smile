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
import java.util.List;
import smile.math.MathEx;
import smile.math.SparseArray;

/** Linear support vector machine. */
public class LinearSupportVectorMachine implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * Weight vector for linear SVM.
     */
    private double[] w;
    /**
     * Threshold of decision function.
     */
    private double b = 0.0;

    /**
     * Constructor.
     *
     * @param w the weight vector.
     * @param b the intercept.
     */
    public LinearSupportVectorMachine(double[] w, double b) {
        this.w = w;
        this.b = b;
    }

    /**
     * Creates a linear SVM from a set of support vectors.
     * @param p the dimension of input vector.
     * @param sv the support vectors.
     * @param b the threshold of decision function.
     * @return a linear SVM
     */
    public static LinearSupportVectorMachine of(int p, double b, List<SupportVector<double[]>> sv) {
        double[] w = new double[p];

        for (SupportVector<double[]> v : sv) {
            double[] x = v.x;

            for (int i = 0; i < w.length; i++) {
                w[i] += v.alpha * x[i];
            }
        }

        return new LinearSupportVectorMachine(w, b);
    }

    /**
     * Creates a linear SVM from a set of binary sparse support vectors.
     * @param p the dimension of input vector.
     * @param sv the support vectors.
     * @param b the threshold of decision function.
     * @return a linear SVM
     */
    public static LinearSupportVectorMachine binary(int p, double b, List<SupportVector<int[]>> sv) {
        double[] w = new double[p];

        for (SupportVector<int[]> v : sv) {
            for (int i : v.x) {
                w[i] += v.alpha;
            }
        }

        return new LinearSupportVectorMachine(w, b);
    }

    /**
     * Creates a linear SVM from a set of sparse support vectors.
     * @param p the dimension of input vector.
     * @param sv the support vectors.
     * @param b the threshold of decision function.
     * @return a linear SVM
     */
    public static LinearSupportVectorMachine sparse(int p, double b, List<SupportVector<SparseArray>> sv) {
        double[] w = new double[p];

        for (SupportVector<SparseArray> v : sv) {
            for (SparseArray.Entry e : v.x) {
                w[e.i] += v.alpha * e.x;
            }
        }

        return new LinearSupportVectorMachine(w, b);
    }

    /**
     * Returns the value of decision function.
     */
    public double predict(double[] x) {
        return b + MathEx.dot(w, x);
    }

    /**
     * Returns the value of decision function.
     */
    public double predict(int[] x) {
        double f = b;
        for (int i : x) {
            f += w[i];
        }

        return f;
    }

    /**
     * Returns the value of decision function.
     */
    public double predict(SparseArray x) {
        double f = b;
        for (SparseArray.Entry e : x) {
            f += w[e.i] * e.x;
        }

        return f;
    }
}
