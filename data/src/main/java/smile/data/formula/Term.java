/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data.formula;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import smile.data.type.StructType;

/**
 * An abstract term in the formula. A term is recursively constructed
 * from constant symbols, variables and function symbols. A formula
 * consists of a series of terms. To be concise, we also allow
 * HyperTerms that can be can be expanded to multiple simple terms.
 *
 * @author Haifeng Li
 */
public interface Term extends Serializable {
    /**
     * Binds the term to a schema.
     * @param schema the schema to bind the term with.
     * @return the feature list.
     */
    List<Feature> bind(StructType schema);

    /**
     * Returns the list of variables used in this term.
     * @return the list of variables used in this term.
     */
    Set<String> variables();

    /**
     * Expands the term (e.g. FactorCrossing).
     * @return the expanded terms.
     */
    default List<Term> expand() {
        return Collections.singletonList(this);
    }
}
