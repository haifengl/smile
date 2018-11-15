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
import java.util.function.Function;

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

/**
 * The term of exp function.
 *
 * @author Haifeng Li
 */
public class Exp implements Factor {
    /** The operand factor of exp expression. */
    private Factor child;
    /** The lambda of apply(). */
    private Function<Tuple, Double> f;

    /**
     * Constructor.
     *
     * @param factor the factor that exp function is applied to.
     */
    public Exp(Factor factor) {
        this.child = factor;
    }

    @Override
    public String name() {
        return String.format("exp(%s)", child.name());
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
    public DataType type() {
        return DataTypes.DoubleType;
    }

    @Override
    public void bind(StructType schema) {
        child.bind(schema);

        if (child.type() == DataTypes.DoubleType) {
            f = this::applyPrimitive;
        } else if (child.type() == DataTypes.ObjectType) {
            f = this::applyObject;
        } else {
            throw new IllegalStateException(String.format("Invalid expression: ceil(%s)", child.type()));
        }
    }

    @Override
    public Double apply(Tuple o) {
        return f.apply(o);
    }

    /** Apply on double. */
    private Double applyPrimitive(Tuple o) {
        return Math.exp((double) child.apply(o));
    }

    /** Apply on Double. */
    private Double applyObject(Tuple o) {
        Object x = child.apply(o);
        if (x == null) return null;
        else return Math.exp((double) x);
    }
}
