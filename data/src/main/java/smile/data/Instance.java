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

package smile.data;

/**
 * An immutable instance.
 *
 * @param <T> the type of instance.
 *
 * @author Haifeng Li
 */
public interface Instance <T> {
    /**
     * Returns the instance.
     */
    public T x();

    /**
     * Return the (optional) name associated with instance.
     * Note that this is not the class label.
     */
    default public String name() {
        return null;
    }

    /**
     * Return the (optional) weight associated with instance.
     */
    default public double weight() {
        return 1.0;
    }
}
