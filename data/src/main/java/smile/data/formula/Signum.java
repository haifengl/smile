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
 * The term of signum function.
 *
 * @author Haifeng Li
 */
public class Signum implements Factor {
    /** The operand factor of signum expression. */
    private Factor child;

    /**
     * Constructor.
     *
     * @param factor the factor that signum function is applied to.
     */
    public Signum(Factor factor) {
        this.child = factor;
    }

    @Override
    public String name() {
        return String.format("signum(%s)", child.name());
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

        if (x instanceof Double) return Math.signum((Double) x);
        else if (x instanceof Float) return Math.signum((Float) x);
        else throw new IllegalArgumentException("Invalid argument for abs(): " + x);
    }

    @Override
    public DataType type() {
        return child.type();
    }

    @Override
    public void bind(StructType schema) {
        child.bind(schema);

        if (child.type() != DataTypes.DoubleType &&
            child.type() != DataTypes.FloatType &&
            child.type() != DataTypes.object(Double.class) &&
            child.type() != DataTypes.object(Float.class)) {
            throw new IllegalStateException(String.format("Invalid expression: signum(%s)", child.type()));
        }
    }

    @Override
    public float applyAsFloat(Tuple o) {
        return Math.signum(child.applyAsFloat(o));
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return Math.signum(child.applyAsDouble(o));
    }
}
