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
import smile.math.MathEx;
import smile.util.IntSet;

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
public class OneVersusRest<T> implements SoftClassifier<T> {
    private static final long serialVersionUID = 2L;

    /** The number of classes. */
    private int k;
    /** The binary classifier. */
    private Classifier<T>[] classifiers;
    /** The probability estimation by Platt scaling. */
    private PlattScaling[] platts;
    /** The class label encoder. */
    private IntSet labels;

    /**
     * Constructor.
     * @param classifiers the binary classifier for each one-vs-rest case.
     */
    public OneVersusRest(Classifier<T>[] classifiers, PlattScaling[] platts) {
        this.classifiers = classifiers;
        this.platts = platts;
        k = classifiers.length;
        labels = IntSet.of(k);
    }

    /**
     * Constructor.
     * @param classifiers the binary classifier for each one-vs-rest case.
     * @param labels the class labels.
     */
    public OneVersusRest(Classifier<T>[] classifiers, PlattScaling[] platts, IntSet labels) {
        this.classifiers = classifiers;
        this.platts = platts;
        this. k = classifiers.length;
        this.labels = labels;
    }

    /**
     * Fits a multi-class model with binary classifiers.
     * Use +1 and -1 as positive and negative class labels.
     * @param x the training samples.
     * @param y the training labels.
     * @param trainer the lambda to train binary classifiers.
     */
    public static <T> OneVersusRest<T> fit(T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
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
    @SuppressWarnings("unchecked")
    public static <T> OneVersusRest<T> fit(T[] x, int[] y, int pos, int neg, BiFunction<T[], int[], Classifier<T>> trainer) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        if (k <= 2) {
            throw new IllegalArgumentException(String.format("Only %d classes" + k));
        }

        int n = x.length;
        y = codec.y;

        Classifier<T>[] classifiers = new Classifier[k];
        PlattScaling[] platts = new PlattScaling[k];
        for (int i = 0; i < k; i++) {
            int[] yi = new int[n];
            for (int j = 0; j < n; j++) {
                yi[j] = y[j] == i ? pos : neg;
            }

            classifiers[i] = trainer.apply(x, yi);
            platts[i] = PlattScaling.fit(classifiers[i], x, yi);
        }

        return new OneVersusRest<>(classifiers, platts);
    }

    @Override
    public int predict(T x) {
        int y = 0;
        double maxf = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            double f = platts[i].scale(classifiers[i].f(x));
            if (f > maxf) {
                y = i;
                maxf = f;
            }
        }

        return labels.valueOf(y);
    }

    @Override
    public int predict(T x, double[] posteriori) {
        for (int i = 0; i < k; i++) {
            posteriori[i] = platts[i].scale(classifiers[i].f(x));
        }

        MathEx.unitize1(posteriori);
        return labels.valueOf(MathEx.whichMax(posteriori));
    }
}
