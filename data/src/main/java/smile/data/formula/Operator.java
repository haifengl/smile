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

import java.util.HashSet;
import java.util.Set;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.StructType;

/**
 * The term of a + b add expression.
 *
 * @author Haifeng Li
 */
public abstract class Operator extends AbstractTerm implements Term {
    /** The left operand. */
    Term a;
    /** The right operand. */
    Term b;
    /** The data type of output. */
    DataType type;
    /** The lambda with type promotion. */
    java.util.function.Function<Tuple, Object> lambda;

    /**
     * Constructor.
     *
     * @param name the operator name.
     * @param a the left operand.
     * @param b the right operand.
     */
    public Operator(String name, Term a, Term b) {
        super(name);
        this.a = a;
        this.b = b;
    }

    @Override
    public String name() {
        return String.format("%s %s %s", a.name(), name, b.name());
    }

    @Override
    public Set<String> variables() {
        Set<String> vars = new HashSet<>(a.variables());
        vars.addAll(b.variables());
        return vars;
    }

    @Override
    public Object apply(Tuple o) {
        Object x = a.apply(o);
        Object y = b.apply(o);

        if (x == null || y == null)
            return null;

        return lambda.apply(o);
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
    }
}
