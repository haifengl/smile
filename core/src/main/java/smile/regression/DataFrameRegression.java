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

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;

/**
 * Regression trait on DataFrame.
 *
 * @author Haifeng Li
 */
public interface DataFrameRegression {
    /**
     * Predicts the dependent variable of a tuple instance.
     * @param x a tuple instance.
     * @return the predicted value of dependent variable.
     */
    double predict(Tuple x);

    /**
     * Predicts the dependent variables of a data frame.
     *
     * @param data the data frame.
     * @return the predicted values.
     */
    default double[] predict(DataFrame data) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula().bind(data.schema());
        int n = data.size();
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = predict(data.get(i));
        }
        return y;
    }

    /**
     * Returns the model formula.
     * @return the model formula.
     */
    Formula formula();

    /**
     * Returns the schema of predictors.
     * @return the schema of predictors.
     */
    StructType schema();
}
