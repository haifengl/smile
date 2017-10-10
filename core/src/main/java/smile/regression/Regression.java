/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.regression;

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
public interface Regression<T> {
    /**
     * Predicts the dependent variable of an instance.
     * @param x the instance.
     * @return the predicted value of dependent variable.
     */
    public double predict(T x);

    /**
     * Predicts the dependent variables of an array of instances.
     *
     * @param x the instances.
     * @return the predicted values.
     */
    default public double[] predict(T[] x) {
        double[] y = new double[x.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = predict(x[i]);
        }
        return y;
    }
}
