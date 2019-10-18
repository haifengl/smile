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

package smile.classification;

import smile.math.MathEx;

import java.util.Arrays;
import java.util.function.BiFunction;

import static java.lang.Math.abs;
import static java.lang.Math.max;

/**
 * One-vs-one strategy for reducing the problem of
 * multiclass classification to multiple binary classification problems.
 * This approach trains K (K − 1) / 2 binary classifiers for a
 * K-way multiclass problem; each receives the samples of a pair of
 * classes from the original training set, and must learn to distinguish
 * these two classes. At prediction time, a voting scheme is applied:
 * all K (K − 1) / 2 classifiers are applied to an unseen sample and the
 * class that got the highest number of positive predictions gets predicted
 * by the combined classifier.
 * Like One-vs-rest, one-vs-one suffers from ambiguities in that some
 * regions of its input space may receive the same number of votes.
 */
public class OneVersusOne<T> implements SoftClassifier<T> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OneVersusOne.class);

    /** The number of classes. */
    private int k;
    /** The binary classifier. */
    private Classifier<T>[][] classifiers;
    /** The class label encoder. */
    private ClassLabel labels;

    /**
     * Constructor.
     * @param classifiers the binary classifier for each one-vs-one case.
     *                    Only the lower half is needed.
     */
    public OneVersusOne(Classifier<T>[][] classifiers) {
        this.classifiers = classifiers;
        k = classifiers.length;
        labels = ClassLabel.of(k);
    }

    /**
     * Constructor.
     * @param classifiers the binary classifier for each one-vs-one case.
     *                    Only the lower half is needed.
     * @param labels the class labels.
     */
    public OneVersusOne(Classifier<T>[][] classifiers, ClassLabel labels) {
        this.classifiers = classifiers;
        this.k = classifiers.length;
        this.labels = labels;
    }

    /**
     * Fits a multi-class model with binary classifiers.
     * Use +1 and -1 as positive and negative class labels.
     * @param x the training samples.
     * @param y the training labels.
     * @param trainer the lambda to train binary clasifiers.
     */
    public static <T> OneVersusOne<T> fit(T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        return fit(x, y, +1, -1, trainer);
    }

    /**
     * Fits a multi-class model with binary classifiers.
     * @param x the training samples.
     * @param y the training labels.
     * @param pos the class label for one case.
     * @param neg the class label for rest cases.
     * @param trainer the lambda to train binary classifiers.
     */
    public static <T> OneVersusOne<T> fit(T[] x, int[] y, int pos, int neg, BiFunction<T[], int[], Classifier<T>> trainer) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        ClassLabel.Result codec = ClassLabel.fit(y);
        int k = codec.k;
        if (k <= 2) {
            throw new IllegalArgumentException(String.format("Only %d classes" + k));
        }

        // sample size per class.
        int[] ni = codec.ni;
        y = codec.y;

        Classifier<T>[][] classifiers = new Classifier[k][];
        for (int i = 1; i < k; i++) {
            classifiers[i] = new Classifier[i];
            for (int j = 0; j < i; j++) {
                int n = ni[i] + ni[j];

                @SuppressWarnings("unchecked")
                T[] xij = (T[]) java.lang.reflect.Array.newInstance(x.getClass().getComponentType(), n);
                int[] yij = new int[n];

                for (int l = 0, q = 0; l < y.length; l++) {
                    if (y[l] == i) {
                        xij[q] = x[l];
                        yij[q] = pos;
                        q++;
                    } else if (y[l] == j) {
                        xij[q] = x[l];
                        yij[q] = neg;
                        q++;
                    }
                }

                classifiers[i][j] = trainer.apply(xij, yij);
            }
        }

        return new OneVersusOne<>(classifiers);
    }

    @Override
    public int predict(T x) {
        double[] posteriori = new double[k];
        return predict(x, posteriori);
    }

    @Override
    public int predict(T x, double[] posteriori) {
        Arrays.fill(posteriori, 0.0);

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < i; j++) {
                int c = classifiers[i][j].predict(x);
                if (c > 0) {
                    posteriori[i]++;
                } else {
                    posteriori[j]++;
                }
            }
        }

        MathEx.unitize1(posteriori);
        return labels.label(MathEx.whichMax(posteriori));
    }

    /**
     * Estimates probability for multiclass problems by combining
     * calibrated binary probability estimates.
     *
     * <h2>References</h2>
     * <ol>
     * <li> B Zadrozny, C Elkan. Transforming classifier scores into accurate multiclass probability estimates, ACM SIGKDD, 2002.</li>
     * </ol>
     *
     * @param k the number of classes.
     * @param r the code matrix k by l, where k is the number of classes
     *          and l is the number of binary problems. If M(c, b) = +1, then
     *          the examples belonging to class c are considered to be
     *          positive examples for the binary classification problem b.
     *          Similarly, if M(c, b) = -1, the examples belonging to c are
     *          considered to be negative examples for b. Finally, if M(c, b) = 0,
     *          the examples belonging to c are not used in training a classifier for b.
     * @param p the estimated posteriori probabilities on output.
     */
    private static void multiclass(int k, double[][] r, double[] p) {
        double[][] Q = new double[k][k];
        double[] Qp = new double[k];
        double pQp, eps = 0.005 / k;

        for (int t = 0; t < k; t++) {
            p[t] = 1.0 / k;  // Valid if k = 1
            Q[t][t] = 0;
            for (int j = 0; j < t; j++) {
                Q[t][t] += r[j][t] * r[j][t];
                Q[t][j] = Q[j][t];
            }
            for (int j = t + 1; j < k; j++) {
                Q[t][t] += r[j][t] * r[j][t];
                Q[t][j] = -r[j][t] * r[t][j];
            }
        }

        int iter = 0;
        int maxIter = max(100, k);
        for (; iter < maxIter; iter++) {
            // stopping condition, recalculate QP,pQP for numerical accuracy
            pQp = 0;
            for (int t = 0; t < k; t++) {
                Qp[t] = 0;
                for (int j = 0; j < k; j++)
                    Qp[t] += Q[t][j] * p[j];
                pQp += p[t] * Qp[t];
            }
            double max_error = 0;
            for (int t = 0; t < k; t++) {
                double error = abs(Qp[t] - pQp);
                if (error > max_error)
                    max_error = error;
            }
            if (max_error < eps) break;

            for (int t = 0; t < k; t++) {
                double diff = (-Qp[t] + pQp) / Q[t][t];
                p[t] += diff;
                pQp = (pQp + diff * (diff * Q[t][t] + 2 * Qp[t])) / (1 + diff) / (1 + diff);
                for (int j = 0; j < k; j++) {
                    Qp[j] = (Qp[j] + diff * Q[t][j]) / (1 + diff);
                    p[j] /= (1 + diff);
                }
            }
        }

        if (iter >= maxIter) {
            logger.warn("Probability estimation reaches maximal iterations");
        }
    }
}
