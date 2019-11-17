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

package smile.regression;

import java.io.Serializable;
import java.util.function.ToDoubleFunction;

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

    /**
     * Predicts the dependent variables of an array of instances.
     *
     * @param x the instances.
     * @return the predicted values.
     */
    default double[] predict(T[] x) {
        int n = x.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = predict(x[i]);
        }
        return y;
    }

    @Override
    default double applyAsDouble(T x) {
        return predict(x);
    }
}
