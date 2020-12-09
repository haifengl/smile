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

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.StructField;
import smile.data.type.StructType;

import java.util.ArrayList;
import java.util.List;

/**
 * The term of round function.
 *
 * @author Haifeng Li
 */
class Round extends AbstractFunction {
    /**
     * Constructor.
     *
     * @param x the term that the function is applied to.
     */
    public Round(Term x) {
        super("round", x);
    }

    @Override
    public List<Feature> bind(StructType schema) {
        List<Feature> features = new ArrayList<>();

        for (Feature feature : x.bind(schema)) {
            StructField xfield = feature.field();
            DataType type = xfield.type;
            if (!(type.isDouble() || type.isFloat())) {
                throw new IllegalStateException(String.format("Invalid expression: round(%s)", type));
            }

            features.add(new Feature() {
                final StructField field = new StructField(String.format("round(%s)", xfield.name), xfield.type, xfield.measure);

                @Override
                public StructField field() {
                    return field;
                }

                @Override
                public Object apply(Tuple o) {
                    Object y = feature.apply(o);
                    if (y == null) return null;

                    if (y instanceof Double) return Math.round((double) y);
                    else if (y instanceof Float) return Math.abs((float) y);
                    else throw new IllegalArgumentException("Invalid argument for abs(): " + y);
                }

                @Override
                public float applyAsFloat(Tuple o) {
                    return Math.round(feature.applyAsFloat(o));
                }

                @Override
                public double applyAsDouble(Tuple o) {
                    return Math.round(feature.applyAsDouble(o));
                }
            });
        }

        return features;
    }
}
