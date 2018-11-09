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

import smile.data.type.DataType;
import smile.data.type.DataTypes;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The term of round function.
 *
 * @author Haifeng Li
 */
public class Round<T> implements Factor<T, Long> {
    /** The operand factor of round expression. */
    private Factor<T, Double> child;

    /**
     * Constructor.
     *
     * @param factor the factor that round function is applied to.
     */
    public Round(Factor<T, Double> factor) {
        this.child = factor;
    }

    @Override
    public String toString() {
        return String.format("round(%s)", child);
    }

    @Override
    public List<Factor> factors() {
        return Collections.singletonList(this);
    }

    @Override
    public Set<String> variables() {
        return child.variables();
    }

    @Override
    public Long apply(T o) {
        return Math.round(child.apply(o));
    }

    @Override
    public DataType type() {
        return DataTypes.LongType;
    }
}
