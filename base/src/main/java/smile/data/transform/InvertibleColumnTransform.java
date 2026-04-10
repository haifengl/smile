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
package smile.data.transform;

import java.util.BitSet;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.DoubleVector;
import smile.data.vector.NullableDoubleVector;
import smile.data.vector.ValueVector;
import smile.util.function.Function;

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
        return new smile.data.AbstractTuple(x.schema()) {
            @Override
            public Object get(int i) {
                Function inverse = inverses.get(schema.field(i).name());
                if (inverse != null) {
                    return inverse.apply(x.getDouble(i));
                } else {
                    return x.get(i);
                }
            }
        };
    }

    @Override
    public DataFrame invert(DataFrame data) {
        StructType schema = data.schema();
        ValueVector[] vectors = new ValueVector[schema.length()];
        IntStream.range(0, schema.length()).parallel().forEach(i -> {
            StructField field = schema.field(i);
            Function inverse = inverses.get(field.name());
            if (inverse != null) {
                ValueVector column = data.column(i);
                DoubleStream stream = column.doubleStream().map(inverse::apply);
                if (column.isNullable()) {
                    int n = column.size();
                    BitSet mask = new BitSet(n);
                    for (int j = 0; j < n; j++) mask.set(j, column.isNullAt(j));
                    var prop = new StructField(field.name(), DataTypes.NullableDoubleType, field.measure());
                    vectors[i] = new NullableDoubleVector(prop, stream.toArray(), mask);
                } else {
                    vectors[i] = new DoubleVector(field, stream.toArray());
                }
            } else {
                vectors[i] = data.column(i);
            }
        });
        return new DataFrame(vectors);
    }
}
