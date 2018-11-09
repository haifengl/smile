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
import smile.data.type.DataType;

/**
 * Remove a factor from the formula.
 *
 * @author Haifeng Li
 */
public class Remove<T, R> implements Factor<T, R> {
    /** The factor to remove. */
    private Factor<T, R> factor;

    /**
     * Constructor.
     *
     * @param factor the factor to remove.
     */
    public Remove(Factor<T, R> factor) {
        this.factor = factor;
    }

    @Override
    public String toString() {
        return String.format("- %s", factor);
    }

    @Override
    public List<Factor> factors() {
        return Collections.singletonList(factor);
    }

    @Override
    public Set<String> variables() {
        return factor.variables();
    }

    @Override
    public R apply(T o) {
        throw new UnsupportedOperationException("Remove.apply() should never be called");
    }

    @Override
    public DataType type() {
        throw new UnsupportedOperationException("Remove.type() should never be called");
    }
}
