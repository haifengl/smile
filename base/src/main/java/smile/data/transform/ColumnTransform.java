/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data.transform;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.measure.NumericalMeasure;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.math.Function;

/**
 * Column-wise data transformation.
 *
 * @author Haifeng Li
 */
public class ColumnTransform implements Transform {
    /** The name of transformation. */
    private final String name;
    /** The map of column name to transform lambda. */
    private final Map<String, Function> transforms;

    /**
     * Constructor.
     * @param name the name of transformation.
     * @param transforms the map of column name to transform lambda.
     */
    public ColumnTransform(String name, Map<String, Function> transforms) {
        this.name = name;
        this.transforms = transforms;
    }

    @Override
    public Tuple apply(Tuple x) {
        StructType schema = x.schema();
        return new smile.data.AbstractTuple() {
            @Override
            public Object get(int i) {
                Function transform = transforms.get(schema.field(i).name);
                if (transform != null) {
                    return transform.apply(x.getDouble(i));
                } else {
                    return x.get(i);
                }
            }

            @Override
            public StructType schema() {
                return schema;
            }
        };
    }

    @Override
    public DataFrame apply(DataFrame data) {
        StructType schema = data.schema();
        BaseVector<?, ?, ?>[] vectors = new BaseVector[schema.length()];
        IntStream.range(0, schema.length()).parallel().forEach(i -> {
            StructField field = schema.field(i);
            Function transform = transforms.get(field.name);
            if (transform != null) {
                DoubleStream stream = data.stream().mapToDouble(t -> transform.apply(t.getDouble(i)));
                if (field.measure == null || field.measure instanceof NumericalMeasure) {
                    vectors[i] = DoubleVector.of(field, stream);
                } else {
                    vectors[i] = DoubleVector.of(field.name, stream);
                }
            } else {
                vectors[i] = data.column(i);
            }
        });
        return DataFrame.of(vectors);
    }

    @Override
    public String toString() {
        return transforms.values().stream()
                .map(Object::toString)
                .collect(Collectors.joining(",\n  ", name + "(\n  ", "\n)"));
    }
}
