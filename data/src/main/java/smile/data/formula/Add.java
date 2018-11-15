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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

/**
 * The term of a + b add expression.
 *
 * @author Haifeng Li
 */
public class Add implements Factor {
    /** The left factor. */
    private Factor a;
    /** The right factor. */
    private Factor b;
    /** The lambda of apply(). */
    private Function<Tuple, Double> f;

    /**
     * Constructor.
     *
     * @param a the first factor.
     * @param b the second factor.
     */
    public Add(Factor a, Factor b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public String name() {
        return String.format("%s + %s", a.name(), b.name());
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
    public Double apply(Tuple o) {
        return (double) a.apply(o) + (double) b.apply(o);
    }

    @Override
    public DataType type() {
        return DataTypes.DoubleType;
    }

    @Override
    public void bind(StructType schema) {
        a.bind(schema);
        b.bind(schema);
        if (a.type() != DataTypes.DoubleType || b.type() != DataTypes.DoubleType) {
            throw new IllegalStateException(String.format("Invalid expression: %s + %s", a.type(), b.type()));
        }
    }
}
