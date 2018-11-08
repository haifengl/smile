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

package smile.data.vector;

import java.util.stream.IntStream;

/**
 * An immutable boolean vector.
 *
 * @author Haifeng Li
 */
public interface BooleanVector extends BaseVector<Boolean, Integer, IntStream> {
    @Override
    default Class<Boolean> type() {
        return boolean.class;
    }

    /**
     * Returns the value at position i.
     */
    boolean getBoolean(int i);

    /** Creates a named boolean vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static BooleanVector of(String name, boolean[] vector) {
        return new BooleanVectorImpl(name, vector);
    }
}