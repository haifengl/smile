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
import smile.data.type.StructType;

/**
 * This class provides a skeletal implementation of the bi-function term.
 *
 * @author Haifeng Li
 */
public abstract class AbstractBiFunction extends AbstractTerm implements Term {
    /** The name of function. */
    String name;
    /** The first parameter of function. */
    Term x;
    /** The second parameter of function. */
    Term y;

    /**
     * Constructor.
     *
     * @param name the function name.
     * @param x the first parameter of function.
     * @param y the second parameter of function.
     */
    public AbstractBiFunction(String name, Term x, Term y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return String.format("%s(%s, %s)", name, x, y);
    }

    @Override
    public Set<String> variables() {
        Set<String> vars = new HashSet<>(x.variables());
        vars.addAll(y.variables());
        return vars;
    }

    @Override
    public void bind(StructType schema) {
        x.bind(schema);
        y.bind(schema);
    }
}
