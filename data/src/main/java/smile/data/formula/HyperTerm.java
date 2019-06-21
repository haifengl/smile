/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.data.formula;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import smile.data.type.StructType;

/**
 * A term is recursively constructed from constant symbols,
 * variables and function symbols. A formula consists of a series
 * of terms. To be concise, we also allow HyperTerms that can be
 * can be expanded to multiple simple terms.
 *
 * @author Haifeng Li
 */
public interface HyperTerm extends Serializable {
    /** Binds the term to a schema. */
    void bind(StructType schema);

    /** Returns the list of terms after expanding the hyperterm. */
    List<? extends Term> terms();

    /** Returns the list of variables used in this term. */
    Set<String> variables();
}
