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
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;

/**
 * Regression on data frame trait.
 *
 * @author Haifeng Li
 */
public interface DataFrameRegression extends Serializable {
    /** Returns the formula associated with the model. */
    Formula formula();

    /**
     * Predicts the dependent variables of a tuple.
     *
     * @param x the data sample.
     * @return the predicted values.
     */
    double predict(Tuple x);

    /**
     * Predicts the dependent variables of a data frame.
     *
     * @param data the data frame.
     * @return the predicted values.
     */
    default double[] predict(DataFrame data) {
        return data.stream().mapToDouble(x -> predict(x)).toArray();
    }
}
