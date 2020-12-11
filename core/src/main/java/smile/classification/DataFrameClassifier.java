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

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;

/**
 * Classification trait on DataFrame.
 *
 * @author Haifeng Li
 */
public interface DataFrameClassifier {

    /**
     * Predicts the class label of an instance.
     * @param x a tuple instance.
     * @return the predicted class label.
     */
    int predict(Tuple x);

    /**
     * Predicts the class labels of a data frame.
     *
     * @param data the data frame.
     * @return the predicted class labels.
     */
    default int[] predict(DataFrame data) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula().bind(data.schema());
        int n = data.size();
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            y[i] = predict(data.get(i));
        }
        return y;
    }

    /**
     * Returns the formula associated with the model.
     * @return the formula associated with the model.
     */
    Formula formula();

    /**
     * Returns the design matrix schema.
     * @return the design matrix schema.
     */
    StructType schema();
}
