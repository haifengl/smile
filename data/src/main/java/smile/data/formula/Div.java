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
 * The term of {@code a / b} expression.
 *
 * @author Haifeng Li
 */
public class Div extends Operator {
    /**
     * Constructor.
     *
     * @param a the first factor.
     * @param b the second factor.
     */
    public Div(Term a, Term b) {
        super("/", a, b);
    }

    @Override
    public List<Feature> bind(StructType schema) {
        List<Feature> features = new ArrayList<>();
        List<Feature> xfeatures = x.bind(schema);
        List<Feature> yfeatures = y.bind(schema);
        if (xfeatures.size() != yfeatures.size()) {
            throw new IllegalStateException(String.format("The features of %s and %s are of different size: %d != %d", x, y, xfeatures.size(), yfeatures.size()));
        }

        for (int i = 0; i < xfeatures.size(); i++) {
            Feature a = xfeatures.get(i);
            StructField xfield = a.field();
            DataType xtype = xfield.type;
            Feature b = yfeatures.get(i);
            StructField yfield = b.field();
            DataType ytype = yfield.type;

            if (!(xtype.isInt() ||  xtype.isLong() ||  xtype.isDouble() || xtype.isFloat() ||
                  ytype.isInt() ||  ytype.isLong() ||  ytype.isDouble() || ytype.isFloat() )) {
                throw new IllegalStateException(String.format("Invalid expression: %s / %s", xtype, ytype));
            }

            features.add(new Feature() {
                final StructField field = new StructField(String.format("%s / %s", xfield.name, yfield.name),
                        DataType.prompt(xfield.type, yfield.type),
                        null);

                final java.util.function.Function<Tuple, Object> lambda =
                        field.type.isInt()    ? (Tuple o) -> a.applyAsInt(o)    / b.applyAsInt(o) :
                        field.type.isLong()   ? (Tuple o) -> a.applyAsLong(o)   / b.applyAsLong(o) :
                        field.type.isFloat()  ? (Tuple o) -> a.applyAsFloat(o)  / b.applyAsFloat(o) :
                        field.type.isDouble() ? (Tuple o) -> a.applyAsDouble(o) / b.applyAsDouble(o) :
                        null;

                @Override
                public StructField field() {
                    return field;
                }

                @Override
                public Object apply(Tuple o) {
                    Object x = a.apply(o);
                    Object y = b.apply(o);
                    if (x == null || y == null) return null;
                    else return lambda.apply(o);
                }

                @Override
                public int applyAsInt(Tuple o) {
                    return a.applyAsInt(o) / b.applyAsInt(o);
                }

                @Override
                public long applyAsLong(Tuple o) {
                    return a.applyAsLong(o) / b.applyAsLong(o);
                }

                @Override
                public float applyAsFloat(Tuple o) {
                    return a.applyAsFloat(o) / b.applyAsFloat(o);
                }

                @Override
                public double applyAsDouble(Tuple o) {
                    return a.applyAsDouble(o) / b.applyAsDouble(o);
                }
            });
        }

        return features;
    }
}
