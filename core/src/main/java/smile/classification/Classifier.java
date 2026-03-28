/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import smile.data.Dataset;
import smile.math.MathEx;

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
 * @param <T> the type of model input object.
 * 
 * @author Haifeng Li
 */
public interface Classifier<T> extends ToIntFunction<T>, ToDoubleFunction<T>, Serializable {
    /**
     * The classifier trainer.
     * @param <T> the type of model input object.
     * @param <M> the type of model.
     */
    interface Trainer<T, M extends Classifier<T>> {
        /**
         * Fits a classification model with the default hyperparameters.
         * @param x the training samples.
         * @param y the training labels.
         * @return the model
         */
        default M fit(T[] x, int[] y) {
            Properties params = new Properties();
            return fit(x, y, params);
        }

        /**
         * Fits a classification model.
         * @param x the training samples.
         * @param y the training labels.
         * @param params the hyperparameters.
         * @return the model
         */
        M fit(T[] x, int[] y, Properties params);
    }

    /**
     * Returns the number of classes.
     * @return the number of classes.
     */
    int numClasses();

    /**
     * Returns the class labels.
     * @return the class labels.
     */
    int[] classes();

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
    default int[] predict(Dataset<T, ?> x) {
        return x.stream().mapToInt(sample -> predict(sample.x())).toArray();
    }

    /**
     * Returns true if this is a soft classifier that can estimate
     * the posteriori probabilities of classification.
     *
     * @return true if soft classifier.
     */
    default boolean isSoft() {
        try {
            predict(null, new double[numClasses()]);
        } catch (UnsupportedOperationException e) {
            return !e.getMessage().equals("soft classification with a hard classifier");
        } catch (Exception e) {
            return true;
        }
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
        throw new UnsupportedOperationException("soft classification with a hard classifier");
    }

    /**
     * Predicts the class labels of an array of instances.
     *
     * @param x the instances to be classified.
     * @param posteriori a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    default int[] predict(T[] x, double[][] posteriori) {
        int n = x.length;
        return IntStream.range(0, n).parallel()
                .map(i -> predict(x[i], posteriori[i]))
                .toArray();
    }

    /**
     * Predicts the class labels of a list of instances.
     *
     * @param x the instances to be classified.
     * @param posteriori an empty list to store a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    default int[] predict(List<T> x, List<double[]> posteriori) {
        int n = x.size();
        int k = numClasses();
        double[][] prob = new double[n][k];
        Collections.addAll(posteriori, prob);
        return IntStream.range(0, n).parallel()
                .map(i -> predict(x.get(i), prob[i]))
                .toArray();
    }

    /**
     * Predicts the class labels of a dataset.
     *
     * @param x the dataset to be classified.
     * @param posteriori an empty list to store a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    default int[] predict(Dataset<T, ?> x, List<double[]> posteriori) {
        int n = x.size();
        int k = numClasses();
        double[][] prob = new double[n][k];
        Collections.addAll(posteriori, prob);
        return IntStream.range(0, n).parallel()
                .map(i -> predict(x.get(i).x(), prob[i]))
                .toArray();
    }

    /**
     * Returns true if this is an online learner.
     *
     * @return true if online learner.
     */
    default boolean isOnline() {
        try {
            update(null, 0);
        } catch (UnsupportedOperationException e) {
            return !e.getMessage().equals("update a batch learner");
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    /**
     * Online update the classifier with a new training instance.
     * In general, this method may be NOT multi-thread safe.
     *
     * @param x the training instance.
     * @param y the training label.
     */
    default void update(T x, int y) {
        throw new UnsupportedOperationException("update a batch learner");
    }

    /**
     * Updates the model with a mini-batch of new samples.
     * @param x the training instances.
     * @param y the training labels.
     */
    default void update(T[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Input vector x of size %d not equal to length %d of y", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++){
            update(x[i], y[i]);
        }
    }

    /**
     * Updates the model with a mini-batch of new samples.
     * @param batch the training instances.
     */
    default void update(Dataset<T, Integer> batch) {
        batch.stream().forEach(sample -> update(sample.x(), sample.y()));
    }

    /**
     * Return an ensemble of multiple base models to obtain better
     * predictive performance.
     *
     * @param models the base models.
     * @param <T> the type of model input object.
     * @return the ensemble model.
     */
    @SafeVarargs
    static <T> Classifier<T> ensemble(Classifier<T>... models) {
        return new Classifier<>() {
            /**
             * The ensemble is a soft classifier only if all the base models are.
             */
            private final boolean soft = Arrays.stream(models).allMatch(Classifier::isSoft);

            /**
             * The ensemble is an online learner only if all the base models are.
             */
            private final boolean online = Arrays.stream(models).allMatch(Classifier::isOnline);

            @Override
            public boolean isSoft() {
                return soft;
            }

            @Override
            public boolean isOnline() {
                return online;
            }

            @Override
            public int numClasses() {
                return models[0].numClasses();
            }

            @Override
            public int[] classes() {
                return models[0].classes();
            }

            @Override
            public int predict(T x) {
                int[] labels = new int[models.length];
                for (int i = 0; i < models.length; i++) {
                    labels[i] = models[i].predict(x);
                }
                return MathEx.mode(labels);
            }

            @Override
            public int predict(T x, double[] posteriori) {
                Arrays.fill(posteriori, 0.0);
                double[] prob = new double[posteriori.length];

                for (Classifier<T> model : models) {
                    model.predict(x, prob);
                    for (int i = 0; i < prob.length; i++) {
                        posteriori[i] += prob[i];
                    }
                }

                for (int i = 0; i < posteriori.length; i++) {
                    posteriori[i] /= models.length;
                }
                return MathEx.whichMax(posteriori);
            }

            @Override
            public void update(T x, int y) {
                for (Classifier<T> model : models) {
                    model.update(x, y);
                }
            }
        };
    }
}
