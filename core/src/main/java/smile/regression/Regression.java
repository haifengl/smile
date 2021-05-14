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

package smile.regression;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;
import smile.data.Dataset;
import smile.data.Instance;

/**
 * Regression analysis includes any techniques for modeling and analyzing
 * the relationship between a dependent variable and one or more independent
 * variables. Most commonly, regression analysis estimates the conditional
 * expectation of the dependent variable given the independent variables.
 * Regression analysis is widely used for prediction and forecasting, where
 * its use has substantial overlap with the field of machine learning. 
 * 
 * @author Haifeng Li
 */
public interface Regression<T> extends ToDoubleFunction<T>, Serializable {
    /**
     * Predicts the dependent variable of an instance.
     * @param x an instance.
     * @return the predicted value of dependent variable.
     */
    double predict(T x);

    @Override
    default double applyAsDouble(T x) {
        return predict(x);
    }

    /**
     * Predicts the dependent variable of an array of instances.
     *
     * @param x the instances.
     * @return the predicted values.
     */
    default double[] predict(T[] x) {
        return Arrays.stream(x).mapToDouble(this::predict).toArray();
    }

    /**
     * Predicts the dependent variable of a list of instances.
     *
     * @param x the instances to be classified.
     * @return the predicted class labels.
     */
    default double[] predict(List<T> x) {
        return x.stream().mapToDouble(this::predict).toArray();
    }

    /**
     * Predicts the dependent variable of a dataset.
     *
     * @param x the dataset to be classified.
     * @return the predicted class labels.
     */
    default double[] predict(Dataset<T> x) {
        return x.stream().mapToDouble(this::predict).toArray();
    }

    /**
     * Returns true if this is an online learner.
     *
     * @return true if online learner.
     */
    default boolean online() {
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
     * @param y the response variable.
     */
    default void update(T x, double y) {
        throw new UnsupportedOperationException("update a batch learner");
    }

    /**
     * Updates the model with a mini-batch of new samples.
     * @param x the training instances.
     * @param y the response variables.
     */
    default void update(T[] x, double[] y) {
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
    default void update(Dataset<Instance<T>> batch) {
        batch.stream().forEach(sample -> update(sample.x(), sample.y()));
    }

    /**
     * Return an ensemble of multiple base models to obtain better
     * predictive performance.
     *
     * @param models the base models.
     * @return the ensemble model.
     */
    @SafeVarargs
    static <T> Regression<T> ensemble(Regression<T>... models) {
        return new Regression<T>() {
            /** The ensemble is an online learner only if all the base models are. */
            private boolean online = Arrays.stream(models).allMatch(model -> model.online());

            @Override
            public boolean online() {
                return online;
            }

            @Override
            public double predict(T x) {
                double y = 0;
                for (Regression<T> model : models) {
                    y += model.predict(x);
                }
                return y / models.length;
            }

            @Override
            public void update(T x, double y) {
                for (Regression<T> model : models) {
                    model.update(x, y);
                }
            }
        };
    }
}
