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
 * An immutable integer vector.
 *
 * @author Haifeng Li
 */
public interface IntVector extends BaseVector<Integer, Integer, IntStream> {
    @Override
    default Class<Integer> type() {
        return int.class;
    }

    /**
     * Returns the value at position i.
     */
    int getInt(int i);

    /** Creates a named integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static IntVector of(String name, int[] vector) {
        return new IntVectorImpl(name, vector);
    }
}