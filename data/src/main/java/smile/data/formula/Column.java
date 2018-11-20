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
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;
import smile.util.ToFloatFunction;

/**
 * A column in a DataFrame.
 *
 * @author Haifeng Li
 */
final class Column implements Factor {
    /** Column name. */
    private final String name;
    /** Data type of column. Only available after calling bind(). */
    private DataType type;
    /** Column index after binding to a schema. */
    private int index = -1;

    /**
     * Constructor.
     *
     * @param name the column name.
     */
    public Column(String name) {
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
    public boolean isColumn() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return name().equals(o);
    }

    @Override
    public List<Column> factors() {
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
        type = schema.fields()[index].type;
    }
}
