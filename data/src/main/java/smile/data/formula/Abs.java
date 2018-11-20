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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

/**
 * The term of abs function.
 *
 * @author Haifeng Li
 */
class Abs implements Factor {
    /** The operand factor of abs expression. */
    private Factor child;

    /**
     * Constructor.
     *
     * @param factor the factor that abs function is applied to.
     */
    public Abs(Factor factor) {
        this.child = factor;
    }

    @Override
    public String name() {
        return String.format("abs(%s)", child.name());
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
        return child.variables();
    }

    @Override
    public Object apply(Tuple o) {
        Object x = child.apply(o);
        if (x == null) return null;

        if (x instanceof Double) return Math.abs((Double) x);
        else if (x instanceof Integer) return Math.abs((Integer) x);
        else if (x instanceof Float) return Math.abs((Float) x);
        else if (x instanceof Long) return Math.abs((Long) x);
        else throw new IllegalArgumentException("Invalid argument for abs(): " + x);
    }

    @Override
    public int applyAsInt(Tuple o) {
        return Math.abs(child.applyAsInt(o));
    }

    @Override
    public long applyAsLong(Tuple o) {
        return Math.abs(child.applyAsLong(o));
    }

    @Override
    public float applyAsFloat(Tuple o) {
        return Math.abs(child.applyAsFloat(o));
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return Math.abs(child.applyAsDouble(o));
    }

    @Override
    public DataType type() {
        return child.type();
    }

    @Override
    public void bind(StructType schema) {
        child.bind(schema);

        if (!(child.type().isInt() ||
              child.type().isLong() ||
              child.type().isDouble() ||
              child.type().isFloat())) {
            throw new IllegalStateException(String.format("Invalid expression: abs(%s)", child.type()));
        }
    }
}
