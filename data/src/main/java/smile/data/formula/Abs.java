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
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * The term of abs function.
 *
 * @author Haifeng Li
 */
public class Abs extends AbstractFunction {
    /**
     * Constructor.
     *
     * @param x the term that the function is applied to.
     */
    public Abs(Term x) {
        super("abs", x);
    }

    @Override
    public List<Feature> bind(StructType schema) {
        List<Feature> features = new ArrayList<>();

        for (Feature feature : x.bind(schema)) {
            StructField xfield = feature.field();
            DataType type = xfield.type;
            if (!(type.isInt() ||  type.isLong() ||  type.isDouble() || type.isFloat())) {
                throw new IllegalStateException(String.format("Invalid expression: abs(%s)", type));
            }

            features.add(new Feature() {
                final StructField field = new StructField(String.format("abs(%s)", xfield.name), xfield.type, xfield.measure);

                @Override
                public StructField field() {
                    return field;
                }

                @Override
                public Object apply(Tuple o) {
                    Object y = feature.apply(o);
                    if (y == null) return null;

                    if (y instanceof Double) return Math.abs((double) y);
                    else if (y instanceof Integer) return Math.abs((int) y);
                    else if (y instanceof Float) return Math.abs((float) y);
                    else if (y instanceof Long) return Math.abs((long) y);
                    else throw new IllegalArgumentException("Invalid argument for abs(): " + y);
                }

                @Override
                public int applyAsInt(Tuple o) {
                    return Math.abs(feature.applyAsInt(o));
                }

                @Override
                public long applyAsLong(Tuple o) {
                    return Math.abs(feature.applyAsLong(o));
                }

                @Override
                public float applyAsFloat(Tuple o) {
                    return Math.abs(feature.applyAsFloat(o));
                }

                @Override
                public double applyAsDouble(Tuple o) {
                    return Math.abs(feature.applyAsDouble(o));
                }
            });
        }

        return features;
    }
}
