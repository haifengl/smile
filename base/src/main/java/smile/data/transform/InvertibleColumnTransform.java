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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.math.Function;

/**
 * Invertible column-wise transformation.
 *
 * @author Haifeng Li
 */
public class InvertibleColumnTransform extends ColumnTransform implements InvertibleTransform {
    /** The map of column name to inverse transform lambda. */
    private final Map<String, Function> inverses;

    /**
     * Constructor.
     * @param name the name of transformation.
     * @param transforms the map of column name to transform lambda.
     * @param inverses the map of column name to inverse transform lambda.
     */
    public InvertibleColumnTransform(String name, Map<String, Function> transforms, Map<String, Function> inverses) {
        super(name, transforms);
        this.inverses = inverses;
    }

    @Override
    public Tuple invert(Tuple x) {
        StructType schema = x.schema();
        return new smile.data.AbstractTuple() {
            @Override
            public Object get(int i) {
                Function inverse = inverses.get(schema.field(i).name);
                if (inverse != null) {
                    return inverse.apply(x.getDouble(i));
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
    public DataFrame invert(DataFrame data) {
        StructType schema = data.schema();
        BaseVector<?, ?, ?>[] vectors = new BaseVector[schema.length()];
        IntStream.range(0, schema.length()).forEach(i -> {
            StructField field = schema.field(i);
            Function inverse = inverses.get(field.name);
            if (inverse != null) {
                DoubleStream stream = data.stream().parallel().mapToDouble(t -> inverse.apply(t.getDouble(i)));
                vectors[i] = DoubleVector.of(field, stream);
            } else {
                vectors[i] = data.column(i);
            }
        });
        return DataFrame.of(vectors);
    }
}
