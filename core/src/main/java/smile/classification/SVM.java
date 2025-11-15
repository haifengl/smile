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
package smile.classification;

import java.util.Arrays;
import java.util.Properties;
import smile.math.MathEx;
import smile.model.svm.KernelMachine;
import smile.model.svm.LASVM;
import smile.util.SparseArray;
import smile.math.kernel.*;

/**
 * Support vector machines for classification. The basic support vector machine
 * is a binary linear classifier which chooses the hyperplane that represents
 * the largest separation, or margin, between the two classes. If such a
 * hyperplane exists, it is known as the maximum-margin hyperplane and the
 * linear classifier it defines is known as a maximum margin classifier.
 * <p>
 * If there exists no hyperplane that can perfectly split the positive and
 * negative instances, the soft margin method will choose a hyperplane
 * that splits the instances as cleanly as possible, while still maximizing
 * the distance to the nearest cleanly split instances.
 * <p>
 * The soft margin parameter {@code C} trades off correct classification
 * of training examples against maximization of the decision function's
 * margin. For larger values of C, a smaller margin will be accepted if
 * the decision function is better at classifying all training points
 * correctly. A lower C will encourage a larger margin, therefore a
 * simpler decision function, at the cost of training accuracy.
 * <p>
 * The nonlinear SVMs are created by applying the kernel trick to
 * maximum-margin hyperplanes. The resulting algorithm is formally similar,
 * except that every dot product is replaced by a nonlinear kernel function.
 * This allows the algorithm to fit the maximum-margin hyperplane in a
 * transformed feature space. The transformation may be nonlinear and
 * the transformed space be high dimensional. For example, the feature space
 * corresponding Gaussian kernel is a Hilbert space of infinite dimension.
 * Thus, though the classifier is a hyperplane in the high-dimensional feature
 * space, it may be nonlinear in the original input space. Maximum margin
 * classifiers are well regularized, so the infinite dimension does not spoil
 * the results.
 * <p>
 * The effectiveness of SVM depends on the selection of kernel, the kernel's
 * parameters, and the soft margin parameter C. Given a kernel, the best combination
 * of C and kernel's parameters is often selected by a grid-search with
 * cross validation.
 * <p>
 * The dominant approach for creating multi-class SVMs is to reduce the
 * single multi-class problem into multiple binary classification problems.
 * Common methods for such reduction is to build binary classifiers which
 * distinguish between (i) one of the labels to the rest (one-versus-all)
 * or (ii) between every pair of classes (one-versus-one). Classification
 * of new instances for one-versus-all case is done by a winner-takes-all
 * strategy, in which the classifier with the highest output function assigns
 * the class. For the one-versus-one approach, classification
 * is done by a max-wins voting strategy, in which every classifier assigns
 * the instance to one of the two classes, then the vote for the assigned
 * class is increased by one vote, and finally the class with most votes
 * determines the instance classification.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Christopher J. C. Burges. A Tutorial on Support Vector Machines for Pattern Recognition. Data Mining and Knowledge Discovery 2:121-167, 1998.</li>
 * <li> John Platt. Sequential Minimal Optimization: A Fast Algorithm for Training Support Vector Machines.</li>
 * <li> Rong-En Fan, Pai-Hsuen, and Chih-Jen Lin. Working Set Selection Using Second Order Information for Training Support Vector Machines. JMLR, 6:1889-1918, 2005.</li>
 * <li> Antoine Bordes, Seyda Ertekin, Jason Weston and Leon Bottou. Fast Kernel Classifiers with Online and Active Learning, Journal of Machine Learning Research, 6:1579-1619, 2005.</li>
 * <li> Tobias Glasmachers and Christian Igel. Second Order SMO Improves SVM Online and Active Learning.</li>
 * <li> Chih-Chung Chang and Chih-Jen Lin. LIBSVM: a Library for Support Vector Machines.</li>
 * </ol>
 *
 * @see OneVersusOne
 * @see OneVersusRest
 *
 * @param <T> the data type of model input objects.
 *
 * @author Haifeng Li
 */
public class SVM<T> extends KernelMachine<T> implements Classifier<T> {
    /**
     * Constructor.
     * @param kernel Kernel function.
     * @param vectors The support vectors.
     * @param weight The weights of instances.
     * @param b The intercept.
     */
    public SVM(MercerKernel<T> kernel, T[] vectors, double[] weight, double b) {
        super(kernel, vectors, weight, b);
    }

    @Override
    public int numClasses() {
        return 2;
    }

    @Override
    public int[] classes() {
        return new int[]{-1, +1};
    }

    @Override
    public int predict(T x) {
        return score(x) > 0 ? +1 : -1;
    }

    /**
     * SVM hyperparameters.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @param epochs the number of epochs, usually 1 or 2 is sufficient.
     */
    public record Options(double C, double tol, int epochs) {
        /** Constructor. */
        public Options {
            if (C < 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + C);
            }
            if (tol <= 0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }
            if (epochs < 1) {
                throw new IllegalArgumentException("Invalid epochs: " + epochs);
            }
        }

        /**
         * Constructor.
         * @param C the soft margin penalty parameter.
         */
        public Options(double C) {
            this(C, 1E-3, 1);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.svm.C", Double.toString(C));
            props.setProperty("smile.svm.tolerance", Double.toString(tol));
            props.setProperty("smile.svm.epochs", Integer.toString(epochs));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static SVM.Options of(Properties props) {
            double C = Double.parseDouble(props.getProperty("smile.svm.C", "1.0"));
            double tol = Double.parseDouble(props.getProperty("smile.svm.tolerance", "1E-3"));
            int epochs = Integer.parseInt(props.getProperty("smile.svm.epochs", "1"));
            return new SVM.Options(C, tol, epochs);
        }
    }

    /**
     * Fits a binary linear SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static LinearSVM fit(double[][] x, int[] y, Options options) {
        LASVM<double[]> lasvm = new LASVM<>(new LinearKernel(), options.C, options.tol);
        KernelMachine<double[]> svm = lasvm.fit(x, y, options.epochs);
        return new LinearSVM(svm);
    }

    /**
     * Fits a binary linear SVM of binary sparse data.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param p the dimension of input vector.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static BinarySparseLinearSVM fit(int[][] x, int[] y, int p, Options options) {
        LASVM<int[]> lasvm = new LASVM<>(new BinarySparseLinearKernel(), options.C, options.tol);
        KernelMachine<int[]> svm = lasvm.fit(x, y, options.epochs);
        return new BinarySparseLinearSVM(p, svm);
    }

    /**
     * Fits a binary linear SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param p the dimension of input vector.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static SparseLinearSVM fit(SparseArray[] x, int[] y, int p, Options options) {
        LASVM<SparseArray> lasvm = new LASVM<>(new SparseLinearKernel(), options.C, options.tol);
        KernelMachine<SparseArray> svm = lasvm.fit(x, y, options.epochs);
        return new SparseLinearSVM(p, svm);
    }

    /**
     * Fits a binary SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param kernel the kernel function.
     * @param options the hyperparameters.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> SVM<T> fit(T[] x, int[] y, MercerKernel<T> kernel, Options options) {
        LASVM<T> lasvm = new LASVM<>(kernel, options.C, options.tol);
        KernelMachine<T> model = lasvm.fit(x, y, options.epochs);
        return new SVM<>(model.kernel(), model.vectors(), model.weights(), model.intercept());
    }

    /**
     * Fits a binary or multiclass SVM.
     * @param x training samples.
     * @param y training labels.
     * @param params the hyperparameters.
     * @return the model.
     */
    public static Classifier<double[]> fit(double[][] x, int[] y, Properties params) {
        MercerKernel<double[]> kernel = MercerKernel.of(params.getProperty("smile.svm.kernel", "linear"));
        var options = Options.of(params);

        int[] classes = MathEx.unique(y);
        String trainer = params.getProperty("smile.svm.type", classes.length == 2 ? "binary" : "ovr").toLowerCase();
        switch (trainer) {
            case "ovr":
                if (kernel instanceof LinearKernel) {
                    return OneVersusRest.fit(x, y, (xi, yi) -> SVM.fit(xi, yi, options));
                } else {
                    return OneVersusRest.fit(x, y, (xi, yi) -> SVM.fit(xi, yi, kernel, options));
                }
            case "ovo":
                if (kernel instanceof LinearKernel) {
                    return OneVersusOne.fit(x, y, (xi, yi) -> SVM.fit(xi, yi, options));
                } else {
                    return OneVersusOne.fit(x, y, (xi, yi) -> SVM.fit(xi, yi, kernel, options));
                }
            case "binary":
                Arrays.sort(classes);
                if (classes[0] != -1 || classes[1] != 1) {
                    y = y.clone();
                    for (int i = 0; i < y.length; i++) {
                        y[i] = y[i] == classes[0] ? -1 : +1;
                    }
                }
                if (kernel instanceof LinearKernel) {
                    return SVM.fit(x, y, options);
                } else {
                    return SVM.fit(x, y, kernel, options);
                }
            default:
                throw new IllegalArgumentException("Unknown SVM type: " + trainer);
        }
    }
}
