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
import java.util.Arrays;
import java.util.Optional;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;

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
public interface Regression<T> extends Serializable {
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
        return Arrays.stream(x).mapToDouble(xi -> predict(xi)).toArray();
    }

    /**
     * Predicts the dependent variable of a tuple instance.
     * @param x a tuple instance.
     * @return the predicted value of dependent variable.
     */
    default double predict(Tuple x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Predicts the dependent variables of a data frame.
     *
     * @param data the data frame.
     * @return the predicted values.
     */
    default double[] predict(DataFrame data) {
        return data.stream().mapToDouble(x -> predict(x)).toArray();
    }

    /** Returns the formula associated with the model. */
    default Optional<Formula> formula() {
        return Optional.empty();
    }

    /** Returns the design matrix schema. */
    default Optional<StructType> schema() {
        return Optional.empty();
    }
}
