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
 * Remove a factor from the formula.
 *
 * @author Haifeng Li
 */
class Delete extends AbstractTerm {
    /** The term to delete. */
    HyperTerm x;

    /**
     * Constructor.
     *
     * @param x the term to delete.
     */
    public Delete(HyperTerm x) {
        super("delete");
        this.x = x;
    }

    @Override
    public String name() {
        return String.format("delete(%s)", x.name());
    }

    @Override
    public void bind(StructType schema) {
        x.bind(schema);
    }

    @Override
    public List<Term> terms() {
        return x.terms();
    }

    @Override
    public Set<String> variables() {
        return x.variables();
    }
}
