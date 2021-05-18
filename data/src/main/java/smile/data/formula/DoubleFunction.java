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

package smile.data.formula;

import java.util.ArrayList;
import java.util.List;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * The generic term of applying a double function.
 *
 * @author Haifeng Li
 */
public class DoubleFunction extends AbstractFunction {
    /** The function on a double. */
    private final smile.math.Function lambda;

    /**
     * Constructor.
     *
     * @param name the name of function.
     * @param x the term that the function is applied to.
     * @param lambda the function/lambda.
     */
    public DoubleFunction(String name, Term x, smile.math.Function lambda) {
        super(name, x);
        this.lambda = lambda;
    }

    @Override
    public List<Feature> bind(StructType schema) {
        List<Feature> features = new ArrayList<>();

        for (Feature feature : x.bind(schema)) {
            StructField xfield = feature.field();
            DataType type = xfield.type;
            if (!(type.isDouble() || type.isFloat() || type.isInt() || type.isLong() || type.isShort() || type.isByte())) {
                throw new IllegalStateException(String.format("Invalid expression: %s(%s)", name, type));
            }

            features.add(new Feature() {
                final StructField field = new StructField(
                        String.format("%s(%s)", name, xfield.name),
                        type.id() == DataType.ID.Object ? DataTypes.DoubleObjectType : DataTypes.DoubleType,
                        xfield.measure);

                @Override
                public StructField field() {
                    return field;
                }

                @Override
                public Double apply(Tuple o) {
                    Object y = feature.apply(o);
                    if (y == null) return null;
                    else return lambda.apply(((Number) y).doubleValue());
                }

                @Override
                public double applyAsDouble(Tuple o) {
                    return lambda.apply(feature.applyAsDouble(o));
                }
            });
        }

        return features;
    }
}
