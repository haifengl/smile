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

import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;
import smile.data.Tuple;
import smile.data.type.StructType;

/**
 * Encode categorical integer features using sparse one-hot scheme.
 * All variables should be nominal attributes and will be converted to binary
 * dummy variables in a compact representation in which only indices of nonzero
 * elements are stored in an integer array. In Maximum Entropy Classifier,
 * the data are expected to store in this format.
 *
 * @author Haifeng Li
 */
public class SparseOneHotEncoder {
    /**
     * The variable attributes.
     */
    private StructType schema;
    /**
     * Starting index for each nominal attribute.
     */
    private int[] base;

    /**
     * Constructor.
     * @param schema the variable attributes. All of them have to be
     *               nominal attributes.
     */
    public SparseOneHotEncoder(StructType schema) {
        this.schema = schema;
        base = new int[schema.length()];
        for (int i = 0; i < base.length; i++) {
            StructField field = schema.field(i);
            if (field.measure == null || !(field.measure instanceof NominalScale)) {
                throw new IllegalArgumentException("Non-nominal attribute: " + field);
            }

            if (i < base.length-1) {
                base[i+1] = base[i] + ((NominalScale) field.measure).size();
            }
        }
    }

    /**
     * Generates the compact representation of sparse binary features for given object.
     * @param x an object of interest.
     * @return an integer array of nonzero binary features.
     */
    public int[] apply(Tuple x) {
        int[] features = new int[schema.length()];
        for (int i = 0; i < features.length; i++) {
            features[i] = x.getInt(i) + base[i];
        }

        return features;
    }

    /**
     * Generates the compact representation of sparse binary features for a data frame.
     * @param data a data frame.
     */
    public int[][] apply(DataFrame data) {
        return data.stream().map(this::apply).toArray(int[][]::new);
    }
}