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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.extraction;

import java.util.Arrays;
import java.util.function.Function;
import smile.data.DataFrame;
import smile.data.measure.CategoricalMeasure;
import smile.data.type.StructField;
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.util.SparseArray;

/**
 * Encodes numeric and categorical features into sparse array
 * with on-hot encoding of categorical variables.
 *
 * @author Haifeng Li
 */
public class SparseEncoder implements Function<Tuple, SparseArray> {
    /**
     * The variable attributes.
     */
    private final StructType schema;
    /**
     * The columns of variables to encode.
     */
    private final String[] columns;
    /**
     * Starting index for each nominal attribute.
     */
    private final int[] base;

    /**
     * Constructor.
     * @param schema the data frame schema.
     * @param columns the column names of variables to encode.
     *                If empty, all numeric and categorical columns will be used.
     */
    public SparseEncoder(StructType schema, String... columns) {
        this.schema = schema;
        if (columns == null || columns.length == 0) {
            columns = schema.fields().stream()
                    .filter(field -> field.isNumeric() || field.measure() instanceof CategoricalMeasure)
                    .map(StructField::name)
                    .toArray(String[]::new);
        }

        this.columns = columns;
        base = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            StructField field = schema.field(columns[i]);
            if (field.isNumeric()) {
                if (i < base.length-1) {
                    base[i+1] = base[i] + 1;
                }
            } else if (field.measure() instanceof CategoricalMeasure cat) {
                if (i < base.length-1) {
                    base[i+1] = base[i] + cat.size();
                }
            } else {
                throw new IllegalArgumentException(String.format("Column '%s' is neither numeric or categorical", field.name()));
            }
        }
    }

    /**
     * Generates the sparse representation of given object.
     * @param x an object of interest.
     * @return the sparse feature vector.
     */
    @Override
    public SparseArray apply(Tuple x) {
        SparseArray features = new SparseArray();
        for (int i = 0; i < columns.length; i++) {
            StructField field = schema.field(columns[i]);
            if (field.isNumeric()) {
                features.append(base[i], x.getDouble(columns[i]));
            } else if (field.measure() instanceof CategoricalMeasure) {
                features.append(x.getInt(columns[i]) + base[i], 1);
            } else {
                throw new IllegalArgumentException(String.format("Column '%s' is neither numeric or categorical", field.name()));
            }
        }

        return features;
    }

    /**
     * Generates the sparse representation of a data frame.
     * @param data a data frame.
     * @return the sparse feature vectors.
     */
    public SparseArray[] apply(DataFrame data) {
        return data.stream().map(this).toArray(SparseArray[]::new);
    }
}