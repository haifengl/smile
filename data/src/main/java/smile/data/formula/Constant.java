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
import java.util.Set;
import smile.data.type.StructType;

/**
 * A variable in the formula. A variable can be regarded as the
 * identity function that always returns the same value that was
 * used as its argument.
 *
 * @author Haifeng Li
 */
public abstract class Constant implements Term {
    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public Set<String> variables() {
        return Collections.emptySet();
    }

    @Override
    public void bind(StructType schema) {
        // nop
    }
}
