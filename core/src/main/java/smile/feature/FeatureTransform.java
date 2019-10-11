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

package smile.feature;

import java.io.Serializable;
import smile.data.DataFrame;
import smile.data.Tuple;

/**
 * Feature transformation. In general, learning algorithms benefit from
 * standardization of the data set. If some outliers are present in the
 * set, robust transformers are more appropriate.
 *
 * @author Haifeng Li
 */
public interface FeatureTransform extends Serializable {

    /**
     * Transform a feature vector.
     * @param x a feature vector.
     * @return the transformed feature value.
     */
    double[] transform(double[] x);

    /**
     * Transform a data frame.
     * @param data a data frame.
     * @return the transformed data frame.
     */
    default double[][] transform(double[][] data) {
        int n = data.length;
        double[][] y = new double[n][];
        for (int i = 0; i < n; i++) {
            y[i] = transform(data[i]);
        }
        return y;
    }

    /**
     * Transform a feature vector.
     * @param x a feature vector.
     * @return the transformed feature value.
     */
    Tuple transform(Tuple x);

    /**
     * Transform a data frame.
     * @param data a data frame.
     * @return the transformed data frame.
     */
    DataFrame transform(DataFrame data);
}
