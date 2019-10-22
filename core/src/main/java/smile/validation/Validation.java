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

package smile.validation;

import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;
import smile.data.DataFrame;

/**
 * A utility class for validating predictive models on test data.
 * 
 * @author Haifeng
 */
public interface Validation {
    /**
     * Tests a classifier on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param classifier a trained classifier to be tested.
     * @param x the test data set.
     * @return the predictions.
     */
    static <T> int[] test(Classifier<T> classifier, T[] x) {
        int n = x.length;
        int[] prediction = new int[n];
        for (int i = 0; i < n; i++) {
            prediction[i] = classifier.predict(x[i]);
        }

        return prediction;
    }

    /**
     * Tests a regression model on a validation set.
     *
     * @param model a trained regression model to be tested.
     * @param data the test data set.
     * @return the predictions.
     */
    static int[] test(DataFrameClassifier model, DataFrame data) {
        return model.predict(data);
    }

    /**
     * Tests a regression model on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param regression a trained regression model to be tested.
     * @param x the test data set.
     * @return the predictions.
     */
    static <T> double[] test(Regression<T> regression, T[] x) {
        int n = x.length;
        double[] prediction = new double[n];
        for (int i = 0; i < n; i++) {
            prediction[i] = regression.predict(x[i]);
        }

        return prediction;
    }

    /**
     * Tests a regression model on a validation set.
     *
     * @param model a trained regression model to be tested.
     * @param data the test data set.
     * @return the predictions.
     */
    static double[] test(DataFrameRegression model, DataFrame data) {
        return model.predict(data);
    }
}
