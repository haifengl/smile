/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.extraction;

import java.util.Arrays;
import java.util.function.Function;
import smile.data.DataFrame;
import smile.data.measure.CategoricalMeasure;
import smile.data.type.StructField;
import smile.data.Tuple;
import smile.data.type.StructType;

/**
 * Encodes categorical features using sparse one-hot scheme. The categorical
 * attributes will be converted to binary dummy variables in a compact
 * representation in which only indices of nonzero elements are stored in
 * an integer array. In Maximum Entropy Classifier, the data are expected
 * to store in this format.
 *
 * @author Haifeng Li
 */
public class BinaryEncoder implements Function<Tuple, int[]> {
    /**
     * The columns of categorical variables.
     */
    private final String[] columns;
    /**
     * Starting index for each categorical attribute.
     */
    private final int[] base;

    /**
     * Constructor.
     * @param schema the data frame schema.
     * @param columns the column names of categorical variables.
     *                If empty, all categorical columns will be used.
     */
    public BinaryEncoder(StructType schema, String... columns) {
        if (columns == null || columns.length == 0) {
            columns = schema.fields().stream()
                    .filter(field -> field.measure() instanceof CategoricalMeasure)
                    .map(StructField::name)
                    .toArray(String[]::new);
        }

        this.columns = columns;
        base = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            StructField field = schema.field(columns[i]);
            if (!(field.measure() instanceof CategoricalMeasure)) {
                throw new IllegalArgumentException("Non-categorical attribute: " + field);
            }

            if (i < base.length-1) {
                base[i+1] = base[i] + ((CategoricalMeasure) field.measure()).size();
            }
        }
    }

    /**
     * Generates the compact representation of sparse binary features for given object.
     * @param x an object of interest.
     * @return an integer array of nonzero binary features.
     */
    @Override
    public int[] apply(Tuple x) {
        int[] features = new int[columns.length];
        for (int i = 0; i < features.length; i++) {
            features[i] = x.getInt(columns[i]) + base[i];
        }

        return features;
    }

    /**
     * Generates the compact representation of sparse binary features for a data frame.
     * @param data a data frame.
     * @return the binary feature vectors.
     */
    public int[][] apply(DataFrame data) {
        return data.stream().map(this).toArray(int[][]::new);
    }
}