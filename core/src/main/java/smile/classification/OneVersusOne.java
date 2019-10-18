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
public class OneOne<T> implements SoftClassifier<T> {

    private int k;
    private Classifier<T>[][] classifiers;

    public OneOne(Classifier<T>[][] classifiers) {
        this.classifiers = classifiers;
        k = classifiers.length;
    }

    public static <T> OneOne<T> fit(T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
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

        Classifier<T>[][] classifiers = new Classifier[k][];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < i; j++) {
                classifiers[i] = new Classifier[i-1];
                int n = ni[i] + ni[j];

                T[] xij = (T[]) new Object[n];
                int[] yij = new int[n];

                for (int l = 0, q = 0; l < y.length; l++) {
                    if (y[l] == i) {
                        xij[q] = x[l];
                        yij[q] = +1;
                        q++;
                    } else if (y[l] == j) {
                        xij[q] = x[l];
                        yij[q] = -1;
                        q++;
                    }
                }

                classifiers[i][j] = trainer.apply(xij, yij);
            }
        }

        return new OneOne<>(classifiers);
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
                double f = classifiers[i][j].predict(x);
                if (f > 0) {
                    posteriori[i]++;
                } else {
                    posteriori[j]++;
                }
            }
        }

        MathEx.unitize1(posteriori);
        return MathEx.whichMax(posteriori);
    }
}
