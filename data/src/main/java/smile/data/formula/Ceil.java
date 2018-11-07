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

/**
 * The term of ceil function.
 *
 * @author Haifeng Li
 */
public class Ceil implements Factor {
    /** The operand factor of ceil expression. */
    private Factor child;

    /**
     * Constructor.
     *
     * @param factor the factor that ceil function is applied to.
     */
    public Ceil(Factor factor) {
        this.child = factor;
    }

    /**
     * Constructor.
     *
     * @param token the variable that ceil function is applied to.
     */
    public Ceil(String token) {
        this.child = new Token(token);
    }

    @Override
    public String name() {
        return String.format("ceil(%s)", child.name());
    }

    @Override
    public List<Factor> factors() {
        return Collections.singletonList(this);
    }

    @Override
    public Set<String> tokens() {
        return child.tokens();
    }

    @Override
    public double apply(Tuple tuple) {
        return Math.ceil(child.apply(tuple));
    }
}
