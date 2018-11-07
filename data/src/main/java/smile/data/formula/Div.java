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

import smile.data.Tuple;

/**
 * The term of a / b division expression.
 *
 * @author Haifeng Li
 */
public class Div implements Factor {
    /** The numerator factor. */
    private Factor a;
    /** The denominator factor. */
    private Factor b;

    /**
     * Constructor.
     *
     * @param a the numerator factor.
     * @param b the denominator factor.
     */
    public Div(Factor a, Factor b) {
        this.a = a;
        this.b = b;
    }

    /**
     * Constructor.
     *
     * @param a the numerator variable.
     * @param b the denominator variable.
     */
    public Div(String a, String b) {
        this.a = new Token(a);
        this.b = new Token(b);
    }

    @Override
    public String name() {
        return String.format("%s / %s", a.name(), b.name());
    }

    @Override
    public List<Factor> factors() {
        return Collections.singletonList(this);
    }

    @Override
    public Set<String> tokens() {
        Set<String> t = new HashSet<>(a.tokens());
        t.addAll(b.tokens());
        return t;
    }

    @Override
    public double apply(Tuple tuple) {
        return a.apply(tuple) / b.apply(tuple);
    }
}
