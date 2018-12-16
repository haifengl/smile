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
    /** Returns the name of term. */
    String name();

    /** Binds the term to a schema. */
    void bind(StructType schema);

    /** Returns the list of terms after expanding the hyperterm. */
    List<Term> terms();

    /** Returns the list of variables used in this term. */
    Set<String> variables();
}
