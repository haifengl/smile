/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.classification;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static java.lang.Math.abs;
import static java.lang.Math.max;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.util.IntSet;

/**
 * One-vs-one strategy for reducing the problem of
 * multiclass classification to multiple binary classification problems.
 * This approach trains {@code K (K − 1) / 2} binary classifiers for a
 * K-way multiclass problem; each receives the samples of a pair of
 * classes from the original training set, and must learn to distinguish
 * these two classes. At prediction time, a voting scheme is applied:
 * all {@code K (K − 1) / 2} classifiers are applied to an unseen
 * sample and the class that got the highest number of positive predictions
 * gets predicted by the combined classifier. Like One-vs-rest, one-vs-one
 * suffers from ambiguities in that some regions of its input space may
 * receive the same number of votes.
 *
 * @author Haifeng Li
 */
public class OneVersusOne<T> extends AbstractClassifier<T> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OneVersusOne.class);

    /** The number of classes. */
    private final int k;
    /** The binary classifier. */
    private final Classifier<T>[][] classifiers;
    /** The binary classifier. */
    private final PlattScaling[][] platt;

    /**
     * Constructor.
     * @param classifiers the binary classifier for each one-vs-one case.
     *                    Only the lower half is needed.
     * @param platt Platt scaling models.
     */
    public OneVersusOne(Classifier<T>[][] classifiers, PlattScaling[][] platt) {
        this(classifiers, platt, IntSet.of(classifiers.length));
    }

    /**
     * Constructor.
     * @param classifiers the binary classifier for each one-vs-one case.
     *                    Only the lower half is needed.
     * @param platt Platt scaling models.
     * @param labels the class label encoder.
     */
    public OneVersusOne(Classifier<T>[][] classifiers, PlattScaling[][] platt, IntSet labels) {
        super(labels);
        this.classifiers = classifiers;
        this.platt = platt;
        this.k = classifiers.length;
    }

    /**
     * Fits a multi-class model with binary classifiers.
     * Use +1 and -1 as positive and negative class labels.
     * @param x the training samples.
     * @param y the training labels.
     * @param trainer the lambda to train binary classifiers.
     * @param <T> the data type.
     * @return the model.
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
     * @param <T> the data type.
     * @return the model.
     */
    @SuppressWarnings("unchecked")
    public static <T> OneVersusOne<T> fit(T[] x, int[] y, int pos, int neg, BiFunction<T[], int[], Classifier<T>> trainer) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        if (k <= 2) {
            throw new IllegalArgumentException(String.format("Only %d classes", k));
        }

        int[] ni = codec.ni; // sample size per class.
        int[] labels = codec.y;

        Classifier<T>[][] classifiers = new Classifier[k][];
        PlattScaling[][] platts = new PlattScaling[k][];
        for (int i = 1; i < k; i++) {
            classifiers[i] = new Classifier[i];
            platts[i] = new PlattScaling[i];
        }

        IntStream.range(0, k * (k - 1) / 2).parallel().forEach(index -> {
            int j = k - 2 - (int) Math.floor(Math.sqrt(-8*index + 4*k*(k-1)-7)/2.0 - 0.5);
            int i = index + j + 1 - k*(k-1)/2 + (k-j)*((k-j)-1)/2;
            int n = ni[i] + ni[j];

            @SuppressWarnings("unchecked")
            T[] xij = (T[]) java.lang.reflect.Array.newInstance(x.getClass().getComponentType(), n);
            int[] yij = new int[n];

            for (int l = 0, q = 0; l < labels.length; l++) {
                if (labels[l] == i) {
                    xij[q] = x[l];
                    yij[q] = pos;
                    q++;
                } else if (labels[l] == j) {
                    xij[q] = x[l];
                    yij[q] = neg;
                    q++;
                }
            }

            classifiers[i][j] = trainer.apply(xij, yij);

            try {
                platts[i][j] = PlattScaling.fit(classifiers[i][j], xij, yij);
            } catch (UnsupportedOperationException ex) {
                logger.info("The classifier doesn't support score function. Don't fit Platt scaling.");
            }
        });

        return new OneVersusOne<>(classifiers, platts[1][0] == null ? null : platts);
    }

    /**
     * Fits a multi-class model with binary data frame classifiers.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param trainer the lambda to train binary classifiers.
     * @return the model.
     */
    public static DataFrameClassifier fit(Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameClassifier> trainer) {
        Tuple[] x = data.stream().toArray(Tuple[]::new);
        int[] y = formula.y(data).toIntArray();
        OneVersusOne<Tuple> model = fit(x, y, 1, 0, (Tuple[] rows, int[] labels) -> {
            DataFrame df = DataFrame.of(Arrays.asList(rows));
            return trainer.apply(formula, df);
        });

        StructType schema = formula.x(data.get(0)).schema();
        return new DataFrameClassifier() {
            @Override
            public int numClasses() {
                return model.numClasses();
            }

            @Override
            public int[] classes() {
                return model.classes();
            }

            @Override
            public int predict(Tuple x) {
                return model.predict(x);
            }

            @Override
            public Formula formula() {
                return formula;
            }

            @Override
            public StructType schema() {
                return schema;
            }
        };
    }

    /** Prediction is based on voting. */
    @Override
    public int predict(T x) {
        int[] count = new int[k];

        for (int i = 1; i < k; i++) {
            for (int j = 0; j < i; j++) {
                if (classifiers[i][j].predict(x) > 0) {
                    count[i]++;
                } else {
                    count[j]++;
                }
            }
        }

        return classes.valueOf(MathEx.whichMax(count));
    }

    @Override
    public boolean soft() {
        return true;
    }

    /**
     * Prediction is based posteriori probability estimation.
     * The result may be different from predict(T x).
     */
    @Override
    public int predict(T x, double[] posteriori) {
        if (platt == null) {
            throw new UnsupportedOperationException("Platt scaling is not available");
        }

        double[][] r = new double[k][k];

        for (int i = 1; i < k; i++) {
            for (int j = 0; j < i; j++) {
                r[i][j] = platt[i][j].scale(classifiers[i][j].score(x));
                r[j][i] = 1.0 - r[i][j];
            }
        }

        coupling(r, posteriori);
        return classes.valueOf(MathEx.whichMax(posteriori));
    }

    /**
     * Combines pairwise class probability estimates into
     * a joint probability estimate for all k classes.
     *
     * This method implements Method 2 from the paper by Wu, Lin, and Weng.
     *
     * <h2>References</h2>
     * <ol>
     * <li>T. Hastie and R. Tibshirani. Classification by pairwise coupling. NIPS, 1998.</li>
     * <li>B. Zadrozny and C. Elkan. Transforming classifier scores into accurate multiclass probability estimates. ACM SIGKDD, 2002.</li>
     * <li>B. Zadrozny. Reducing multiclass to binary by coupling probability estimates. NIPS, 2002.</li>
     * <li>Wu, Lin and Weng. Probability estimates for multi-class classification by pairwise coupling. JMLR 5:975-1005, 2004.</li>
     * </ol>
     *
     * @param r pairwise class probability
     * @param p the estimated posteriori probabilities on output.
     */
    private void coupling(double[][] r, double[] p) {
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
            logger.warn("coupling reaches maximal iterations");
        }
    }
}
