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
import smile.math.Function;

/**
 * The generic term of applying a double function.
 *
 * @author Haifeng Li
 */
class DoubleFunction implements Factor {
    /** The operand factor of ceil expression. */
    private Factor child;
    /** The transform lambda. */
    private Function lambda;
    /** The name of lambda. */
    private String name;

    /**
     * Constructor.
     *
     * @param name the name of function.
     * @param factor the factor that the function is applied to.
     * @param lambda the function/lambda.
     */
    public DoubleFunction(String name, Factor factor, Function lambda) {
        this.name = name;
        this.child = factor;
        this.lambda = lambda;
    }

    @Override
    public String name() {
        return String.format("%s(%s)", name, child.name());
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
        return child.type().id() == DataType.ID.Object ? DataTypes.DoubleObjectType : DataTypes.DoubleType;
    }

    @Override
    public void bind(StructType schema) {
        child.bind(schema);

        if (!(child.type().isDouble() || child.type().isFloat() || child.type().isInt() || child.type().isLong() || child.type().isShort() || child.type().isByte())) {
            throw new IllegalStateException(String.format("Invalid expression: %s(%s)", name, child.type()));
        }
    }

    @Override
    public double applyAsDouble(Tuple o) {
        return lambda.apply(child.applyAsDouble(o));
    }

    @Override
    public Double apply(Tuple o) {
        Object x = child.apply(o);
        if (x == null) return null;
        else return lambda.apply(((Number) x).doubleValue());
    }
}
