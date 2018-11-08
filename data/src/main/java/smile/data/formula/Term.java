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

import java.util.List;
import java.util.Set;

/**
 * A model consists of a series of terms. The terms themselves
 * consist of a single factor or can be expanded to multiple
 * factors including the the interaction of multiple factors.
 *
 * While formulae usually involve just variable and factor names,
 * they can also involve arithmetic expressions.
 *
 * @author Haifeng Li
 */
public interface Term {
    /** Returns the list of factors after expanding the term. */
    List<Factor> factors();

    /** Returns the list of variables used in this term. */
    Set<String> variables();
}
