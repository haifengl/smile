/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.classification;

import java.util.Arrays;
import java.util.Properties;
import smile.base.svm.KernelMachine;
import smile.base.svm.LinearKernelMachine;
import smile.base.svm.LASVM;
import smile.math.MathEx;
import smile.util.IntSet;
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
     * @param b The intercept;
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
     * Fits a binary linear SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @return the model.
     */
    public static Classifier<double[]> fit(double[][] x, int[] y, double C, double tol) {
        return fit(x, y, C, tol, 1);
    }

    /**
     * Fits a binary linear SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @param epochs the number of epochs, usually 1 or 2 is sufficient.
     * @return the model.
     */
    public static Classifier<double[]> fit(double[][] x, int[] y, double C, double tol, int epochs) {
        LASVM<double[]> lasvm = new LASVM<>(new LinearKernel(), C, tol);
        KernelMachine<double[]> svm = lasvm.fit(x, y, epochs);

        IntSet labels = new IntSet(new int[]{-1, +1});
        return new AbstractClassifier<>(labels) {
            final LinearKernelMachine model = LinearKernelMachine.of(svm);

            @Override
            public int predict(double[] x) {
                return model.f(x) > 0 ? +1 : -1;
            }
        };
    }

    /**
     * Fits a binary linear SVM of binary sparse data.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param p the dimension of input vector.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @return the model.
     */
    public static Classifier<int[]> fit(int[][] x, int[] y, int p, double C, double tol) {
        return fit(x, y, p, C, tol, 1);
    }

    /**
     * Fits a binary linear SVM of binary sparse data.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param p the dimension of input vector.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @param epochs the number of epochs, usually 1 or 2 is sufficient.
     * @return the model.
     */
    public static Classifier<int[]> fit(int[][] x, int[] y, int p, double C, double tol, int epochs) {
        LASVM<int[]> lasvm = new LASVM<>(new BinarySparseLinearKernel(), C, tol);
        KernelMachine<int[]> svm = lasvm.fit(x, y, epochs);

        IntSet labels = new IntSet(new int[]{-1, +1});
        return new AbstractClassifier<>(labels) {
            final LinearKernelMachine model = LinearKernelMachine.binary(p, svm);

            @Override
            public int predict(int[] x) {
                return model.f(x) > 0 ? +1 : -1;
            }
        };
    }

    /**
     * Fits a binary linear SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param p the dimension of input vector.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @return the model.
     */
    public static Classifier<SparseArray> fit(SparseArray[] x, int[] y, int p, double C, double tol) {
        return fit(x, y, p, C, tol, 1);
    }

    /**
     * Fits a binary linear SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param p the dimension of input vector.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @param epochs the number of epochs, usually 1 or 2 is sufficient.
     * @return the model.
     */
    public static Classifier<SparseArray> fit(SparseArray[] x, int[] y, int p, double C, double tol, int epochs) {
        LASVM<SparseArray> lasvm = new LASVM<>(new SparseLinearKernel(), C, tol);
        KernelMachine<SparseArray> svm = lasvm.fit(x, y, epochs);

        IntSet labels = new IntSet(new int[]{-1, +1});
        return new AbstractClassifier<>(labels) {
            final LinearKernelMachine model = LinearKernelMachine.sparse(p, svm);

            @Override
            public int predict(SparseArray x) {
                return model.f(x) > 0 ? +1 : -1;
            }
        };
    }

    /**
     * Fits a binary SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param kernel the kernel function.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> SVM<T> fit(T[] x, int[] y, MercerKernel<T> kernel, double C, double tol) {
        return fit(x, y, kernel, C, tol, 1);
    }

    /**
     * Fits a binary SVM.
     * @param x training samples.
     * @param y training labels of {-1, +1}.
     * @param kernel the kernel function.
     * @param C the soft margin penalty parameter.
     * @param tol the tolerance of convergence test.
     * @param epochs the number of epochs, usually 1 or 2 is sufficient.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> SVM<T> fit(T[] x, int[] y, MercerKernel<T> kernel, double C, double tol, int epochs) {
        LASVM<T> lasvm = new LASVM<>(kernel, C, tol);
        KernelMachine<T> model = lasvm.fit(x, y, epochs);
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
        double C = Double.parseDouble(params.getProperty("smile.svm.C", "1.0"));
        double tol = Double.parseDouble(params.getProperty("smile.svm.tolerance", "1E-3"));
        int epochs = Integer.parseInt(params.getProperty("smile.svm.epochs", "1"));

        int[] classes = MathEx.unique(y);
        String trainer = params.getProperty("smile.svm.type", classes.length == 2 ? "binary" : "ovr").toLowerCase();
        switch (trainer) {
            case "ovr":
                if (kernel instanceof LinearKernel) {
                    return OneVersusRest.fit(x, y, (xi, yi) -> SVM.fit(xi, yi, C, tol, epochs));
                } else {
                    return OneVersusRest.fit(x, y, (xi, yi) -> SVM.fit(xi, yi, kernel, C, tol, epochs));
                }
            case "ovo":
                if (kernel instanceof LinearKernel) {
                    return OneVersusOne.fit(x, y, (xi, yi) -> SVM.fit(xi, yi, C, tol, epochs));
                } else {
                    return OneVersusOne.fit(x, y, (xi, yi) -> SVM.fit(xi, yi, kernel, C, tol, epochs));
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
                    return SVM.fit(x, y, C, tol, epochs);
                } else {
                    return SVM.fit(x, y, kernel, C, tol, epochs);
                }
            default:
                throw new IllegalArgumentException("Unknown SVM type: " + trainer);
        }
    }
}
