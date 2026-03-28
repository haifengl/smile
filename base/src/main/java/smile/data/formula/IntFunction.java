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
package smile.data.formula;

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

import java.util.ArrayList;
import java.util.List;

/**
 * The generic term of applying an integer function.
 *
 * @author Haifeng Li
 */
public class IntFunction extends AbstractFunction {
    /** The function on an integer. */
    private final smile.util.function.IntFunction lambda;

    /**
     * Constructor.
     *
     * @param name the name of function.
     * @param x the term that the function is applied to.
     * @param lambda the function/lambda.
     */
    public IntFunction(String name, Term x, smile.util.function.IntFunction lambda) {
        super(name, x);
        this.lambda = lambda;
    }

    @Override
    public List<Feature> bind(StructType schema) {
        List<Feature> features = new ArrayList<>();

        for (Feature feature : x.bind(schema)) {
            StructField xfield = feature.field();
            DataType dtype = xfield.dtype();
            if (!(dtype.isInt() || dtype.isShort() || dtype.isByte())) {
                throw new IllegalStateException(String.format("Invalid expression: %s(%s)", name, dtype));
            }

            features.add(new Feature() {
                final StructField field = new StructField(
                        String.format("%s(%s)", name, xfield.name()),
                        dtype.isNullable() ? DataTypes.NullableIntType : DataTypes.IntType,
                        xfield.measure());

                @Override
                public StructField field() {
                    return field;
                }

                @Override
                public Integer apply(Tuple o) {
                    Object y = feature.apply(o);
                    if (y == null) return null;
                    else return lambda.apply(((Number) y).intValue());
                }

                @Override
                public int applyAsInt(Tuple o) {
                    return lambda.apply(feature.applyAsInt(o));
                }
            });
        }

        return features;
    }
}
