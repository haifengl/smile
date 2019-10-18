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

import java.util.function.BiFunction;

/**
 * One-vs-rest (or one-vs-all) strategy for reducing the problem of
 * multiclass classification to multiple binary classification problems.
 * It involves training a single classifier per class, with the samples
 * of that class as positive samples and all other samples as negatives.
 * This strategy requires the base classifiers to produce a real-valued
 * confidence score for its decision, rather than just a class label;
 * discrete class labels alone can lead to ambiguities, where multiple
 * classes are predicted for a single sample.
 * <p>
 * Making decisions means applying all classifiers to an unseen sample
 * x and predicting the label k for which the corresponding classifier
 * reports the highest confidence score.
 * <p>
 * Although this strategy is popular, it is a heuristic that suffers
 * from several problems. Firstly, the scale of the confidence values
 * may differ between the binary classifiers. Second, even if the class
 * distribution is balanced in the training set, the binary classification
 * learners see unbalanced distributions because typically the set of
 * negatives they see is much larger than the set of positives.
 */
public class OneRest<T> implements Classifier<T> {
    private int k;
    private Classifier<T>[] classifiers;

    public OneRest(Classifier<T>[] classifiers) {
        this.classifiers = classifiers;
        k = classifiers.length;
    }

    public static <T> OneRest<T> fit(T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        ClassLabel.Result codec = ClassLabel.fit(y);
        int k = codec.k;
        if (k <= 2) {
            throw new IllegalArgumentException(String.format("Only %d classes" + k));
        }

        int n = x.length;
        Classifier<T>[] classifiers = new Classifier[k];
        for (int i = 0; i < k; i++) {
            int[] yi = new int[n];
            for (int j = 0; j < n; j++) {
                yi[j] = y[j] == i ? +1 : -1;
            }

            classifiers[i] = trainer.apply(x, yi);
        }

        return new OneRest<>(classifiers);
    }

    @Override
    public int predict(T x) {
        int label = 0;
        double maxf = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            double f = classifiers[i].predict(x);
            if (f > maxf) {
                label = i;
                maxf = f;
            }
        }

        return label;
    }
}
