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

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import smile.data.Dataset;
import smile.data.measure.NominalScale;

/**
 * A classifier assigns an input object into one of a given number of categories.
 * The input object is formally termed an instance, and the categories are
 * termed classes. The instance is usually described by a vector of features,
 * which together constitute a description of all known characteristics of the
 * instance.
 * <p>
 * Classification normally refers to a supervised procedure, i.e. a procedure
 * that produces an inferred function to predict the output value of new
 * instances based on a training set of pairs consisting of an input object
 * and a desired output value. The inferred function is called a classifier
 * if the output is discrete or a regression function if the output is
 * continuous.
 * 
 * @param <T> the type of input object
 * 
 * @author Haifeng Li
 */
public interface Classifier<T> extends ToIntFunction<T>, ToDoubleFunction<T>, Serializable {
    /**
     * Returns the number of classes.
     * @return the number of classes.
     */
    int numClasses();

    /**
     * Returns the class labels.
     * @return the class labels.
     */
    int[] labels();

    /**
     * Returns the nominal scale of the class labels.
     * @return the nominal scale of the class labels.
     */
    NominalScale scale();

    /**
     * Predicts the class label of an instance.
     *
     * @param x the instance to be classified.
     * @return the predicted class label.
     */
    int predict(T x);

    /**
     * The raw prediction score.
     *
     * @param x the instance to be classified.
     * @return the raw prediction score.
     */
    default double score(T x) {
        throw new UnsupportedOperationException();
    }

    @Override
    default int applyAsInt(T x) {
        return predict(x);
    }

    @Override
    default double applyAsDouble(T x) {
        return score(x);
    }

    /**
     * Predicts the class labels of an array of instances.
     *
     * @param x the instances to be classified.
     * @return the predicted class labels.
     */
    default int[] predict(T[] x) {
        return Arrays.stream(x).mapToInt(this::predict).toArray();
    }

    /**
     * Predicts the class labels of a list of instances.
     *
     * @param x the instances to be classified.
     * @return the predicted class labels.
     */
    default int[] predict(List<T> x) {
        return x.stream().mapToInt(this::predict).toArray();
    }

    /**
     * Predicts the class labels of a dataset.
     *
     * @param x the dataset to be classified.
     * @return the predicted class labels.
     */
    default int[] predict(Dataset<T> x) {
        return x.stream().mapToInt(this::predict).toArray();
    }

    /**
     * Returns true if this is a soft classifier that can estimate
     * the posteriori probabilities of classification.
     *
     * @return true if soft classifier.
     */
    default boolean soft() {
        return false;
    }

    /**
     * Predicts the class label of an instance and also calculate a posteriori
     * probabilities. Classifiers may NOT support this method since not all
     * classification algorithms are able to calculate such a posteriori
     * probabilities.
     *
     * @param x an instance to be classified.
     * @param posteriori a posteriori probabilities on output.
     * @return the predicted class label
     */
    default int predict(T x, double[] posteriori) {
        throw new UnsupportedOperationException();
    }

    /**
     * Predicts the class labels of an array of instances.
     *
     * @param x the instances to be classified.
     * @param posteriori a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    default int[] predict(T[] x, double[][] posteriori) {
        int k = numClasses();
        int n = x.length;
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            if (posteriori[i] == null) {
                posteriori[i] = new double[k];
            }
            y[i] = predict(x[i], posteriori[i]);
        }
        return y;
    }

    /**
     * Predicts the class labels of a list of instances.
     *
     * @param x the instances to be classified.
     * @param posteriori an empty list to store a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    default int[] predict(List<T> x, List<double[]> posteriori) {
        int k = numClasses();
        return x.stream().mapToInt(xi -> {
            double[] prob = new double[k];
            posteriori.add(prob);
            return predict(xi, prob);
        }).toArray();
    }

    /**
     * Predicts the class labels of a dataset.
     *
     * @param x the dataset to be classified.
     * @param posteriori an empty list to store a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    default int[] predict(Dataset<T> x, List<double[]> posteriori) {
        int k = numClasses();
        return x.stream().mapToInt(xi -> {
            double[] prob = new double[k];
            posteriori.add(prob);
            return predict(xi, prob);
        }).toArray();
    }
}
