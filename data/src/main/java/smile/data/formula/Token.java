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
 * A plain token term refers to a raw variable in the context,
 * e.g. a column name of DataFrame.
 *
 * @author Haifeng Li
 */
public class Token implements Factor {
    /** Raw factor. */
    private String token;

    /**
     * Constructor.
     *
     * @param token the raw variable name.
     */
    public Token(String token) {
        this.token = token;
    }

    @Override
    public String name() {
        return token;
    }

    @Override
    public List<Factor> factors() {
        return Collections.singletonList(this);
    }

    @Override
    public Set<String> tokens() {
        return Collections.singleton(token);
    }

    @Override
    public double apply(Tuple tuple) {
        return tuple.getAs(token);
    }
}
