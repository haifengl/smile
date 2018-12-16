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
    /** The name of term. */
    String name;

    /**
     * Constructor.
     *
     * @param name the name of function.
     */
    public AbstractTerm(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean equals(Object o) {
        return name().equals(o);
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }
}
