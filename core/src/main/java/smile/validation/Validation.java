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

import java.util.function.BiFunction;
import smile.math.MathEx;
import smile.classification.Classifier;
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
     * @param y the test data labels.
     * @return the accuracy on the test dataset
     */
    static <T> double test(Classifier<T> classifier, T[] x, int[] y) {
        int n = x.length;
        int[] prediction = new int[n];
        for (int i = 0; i < n; i++) {
            prediction[i] = classifier.predict(x[i]);
        }
        
        return Accuracy.apply(y, prediction);
    }

    /**
     * Tests a regression model on a validation set.
     *
     * @param <T> the data type of input objects.
     * @param model a trained regression model to be tested.
     * @param data the test data set.
     * @return root mean squared error
     */
    static <T> double test(Classifier<T> model, DataFrame data) {
        int[] prediction = model.predict(data);
        int[] y = model.formula().get().response(data).toIntArray();
        return Accuracy.apply(y, prediction);
    }

    /**
     * Tests a regression model on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param regression a trained regression model to be tested.
     * @param x the test data set.
     * @param y the test data response values.
     * @return root mean squared error
     */
    static <T> double test(Regression<T> regression, T[] x, double[] y) {
        int n = x.length;
        double[] predictions = new double[n];
        for (int i = 0; i < n; i++) {
            predictions[i] = regression.predict(x[i]);
        }
        
        return RMSE.apply(y, predictions);
    }

    /**
     * Tests a regression model on a validation set.
     *
     * @param <T> the data type of input objects.
     * @param model a trained regression model to be tested.
     * @param data the test data set.
     * @return root mean squared error
     */
    static <T> double test(Regression<T> model, DataFrame data) {
        double[] prediction = model.predict(data);
        double[] y = model.formula().get().response(data).toDoubleArray();
        return RMSE.apply(y, prediction);
    }

    /**
     * Tests a classifier on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param classifier a trained classifier to be tested.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measure the performance measures of classification.
     * @return the test results with the same size of order of measures
     */
    static <T> double test(Classifier<T> classifier, T[] x, int[] y, ClassificationMeasure measure) {
        int n = x.length;
        int[] predictions = new int[n];
        for (int i = 0; i < n; i++) {
            predictions[i] = classifier.predict(x[i]);
        }
        
        return measure.measure(y, predictions);
    }
    
    /**
     * Tests a classifier on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param classifier a trained classifier to be tested.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measures the performance measures of classification.
     * @return the test results with the same size of order of measures
     */
    static <T> double[] test(Classifier<T> classifier, T[] x, int[] y, ClassificationMeasure[] measures) {
        int n = x.length;
        int[] predictions = new int[n];
        for (int i = 0; i < n; i++) {
            predictions[i] = classifier.predict(x[i]);
        }
        
        int m = measures.length;
        double[] results = new double[m];
        for (int i = 0; i < m; i++) {
            results[i] = measures[i].measure(y, predictions);
        }
        
        return results;
    }
    
    /**
     * Tests a regression model on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param regression a trained regression model to be tested.
     * @param x the test data set.
     * @param y the test data response values.
     * @param measure the performance measure of regression.
     * @return the test results with the same size of order of measures
     */
    static <T> double test(Regression<T> regression, T[] x, double[] y, RegressionMeasure measure) {
        int n = x.length;
        double[] predictions = new double[n];
        for (int i = 0; i < n; i++) {
            predictions[i] = regression.predict(x[i]);
        }
        
        return measure.measure(y, predictions);
    }
    
    /**
     * Tests a regression model on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param regression a trained regression model to be tested.
     * @param x the test data set.
     * @param y the test data response values.
     * @param measures the performance measures of regression.
     * @return the test results with the same size of order of measures
     */
    static <T> double[] test(Regression<T> regression, T[] x, double[] y, RegressionMeasure[] measures) {
        int n = x.length;
        double[] predictions = new double[n];
        for (int i = 0; i < n; i++) {
            predictions[i] = regression.predict(x[i]);
        }
        
        int m = measures.length;
        double[] results = new double[m];
        for (int i = 0; i < m; i++) {
            results[i] = measures[i].measure(y, predictions);
        }
        
        return results;
    }
}
