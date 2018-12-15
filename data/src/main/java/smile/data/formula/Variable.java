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
import smile.data.type.StructType;

/**
 * A variable in the formula. A variable can be regarded as the
 * identity function that always returns the same value that was
 * used as its argument.
 *
 * @author Haifeng Li
 */
final class Variable implements Function {
    /** Variable name. */
    private final String name;
    /** Data type of variable. Only available after calling bind(). */
    private DataType type;
    /** Column index after binding to a schema. */
    private int index = -1;

    /**
     * Constructor.
     *
     * @param name the variable name.
     */
    public Variable(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean isVariable() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return name().equals(o);
    }

    @Override
    public List<Variable> factors() {
        return Collections.singletonList(this);
    }

    @Override
    public Set<String> variables() {
        return Collections.singleton(name);
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

    @Override
    public DataType type() {
        if (type == null)
            throw new IllegalStateException(String.format("Column(%s) is not bound to a schema yet.", name));

        return type;
    }

    @Override
    public void bind(StructType schema) {
        index = schema.fieldIndex(name);
        type = schema.field(index).type;
    }
}
