/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data.formula;

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The term of a / b division expression.
 *
 * @author Haifeng Li
 */
class Div implements Factor {
    /** The numerator factor. */
    private Factor a;
    /** The denominator factor. */
    private Factor b;
    /** The data type of output. */
    private DataType type;
    /** The lambda to get int value with type promotion. */
    private Function<Tuple, Object> f;

    /**
     * Constructor.
     *
     * @param a the numerator factor.
     * @param b the denominator factor.
     */
    public Div(Factor a, Factor b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public String name() {
        return String.format("%s / %s", a.name(), b.name());
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean equals(Object o) {
        return name().equals(o);
    }

    @Override
    public List<? extends Factor> factors() {
        return Collections.singletonList(this);
    }

    @Override
    public Set<String> variables() {
        Set<String> t = new HashSet<>(a.variables());
        t.addAll(b.variables());
        return t;
    }

    @Override
    public Object apply(Tuple o) {
        Object x = a.apply(o);
        Object y = b.apply(o);

        if (x == null || y == null)
            return null;

        return f.apply(o);
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

    @Override
    public DataType type() {
        return type;
    }

    @Override
    public void bind(StructType schema) {
        a.bind(schema);
        b.bind(schema);
        type = DataType.prompt(a.type(), b.type());

        if (type.isInt()) {
            f = (Tuple o) -> a.applyAsInt(o) / b.applyAsInt(o);
        } else if (type.isLong()) {
            f = (Tuple o) -> a.applyAsLong(o) / b.applyAsLong(o);
        } else if (type.isFloat()) {
            f = (Tuple o) -> a.applyAsFloat(o) / b.applyAsFloat(o);
        } else if (type.isDouble()) {
            f = (Tuple o) -> a.applyAsDouble(o) / b.applyAsDouble(o);
        }
    }
}
