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

import java.util.Set;

/**
 * This class provides a skeletal implementation of the function term.
 *
 * @author Haifeng Li
 */
public abstract class AbstractFunction extends AbstractTerm implements Term {
    /** The name of function. */
    String name;
    /** The operand. */
    Term x;

    /**
     * Constructor.
     *
     * @param name the name of function.
     * @param x the term that the function is applied to.
     */
    public AbstractFunction(String name, Term x) {
        this.name = name;
        this.x = x;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", name, x);
    }

    @Override
    public Set<String> variables() {
        return x.variables();
    }
}
