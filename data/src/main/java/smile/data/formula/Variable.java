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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import smile.data.Tuple;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * A variable in the formula. A variable can be regarded as the
 * identity function that always returns the same value that was
 * used as its argument.
 *
 * @author Haifeng Li
 */
final class Variable implements Term {
    /** The variable name. */
    private final String name;

    /**
     * Constructor.
     *
     * @param name the variable name.
     */
    public Variable(String name) {
        this.name = name;
    }

    /** Returns the variable name. */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o instanceof Variable) {
            return name.equals(((Variable)o).name);
        }

        return false;
    }

    @Override
    public Set<String> variables() {
        return Collections.singleton(name);
    }

    @Override
    public List<Feature> bind(StructType schema) {
        Feature feature = new Feature() {
            /** The column index in the schema. */
            private final int index = schema.indexOf(name);
            /** The struct field. */
            private final StructField field = schema.field(index);

            @Override
            public boolean isVariable() {
                return true;
            }

            @Override
            public StructField field() {
                return field;
            }

            @Override
            public Object apply(Tuple o) {
                return o.get(index);
            }

            @Override
            public int applyAsInt(Tuple o) {
                return o.getInt(index);
            }

            @Override
            public long applyAsLong(Tuple o) {
                return o.getLong(index);
            }

            @Override
            public float applyAsFloat(Tuple o) {
                return o.getFloat(index);
            }

            @Override
            public double applyAsDouble(Tuple o) {
                return o.getDouble(index);
            }
        };

        return Collections.singletonList(feature);
    }
}
