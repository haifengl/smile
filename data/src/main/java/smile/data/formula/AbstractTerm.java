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

/**
 * This class provides a skeletal implementation of the Term interface,
 * to minimize the effort required to implement this interface.
 *
 * @author Haifeng Li
 */
public abstract class AbstractTerm implements HyperTerm {
    /**
     * Constructor.
     */
    public AbstractTerm() {

    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o instanceof HyperTerm) {
            return toString().equals(o.toString());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
