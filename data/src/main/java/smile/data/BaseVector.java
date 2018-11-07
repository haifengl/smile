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

import java.io.Serializable;
import java.util.stream.BaseStream;

/**
 * Base interface for immutable named vectors, which are sequences of elements supporting
 * random access and sequential stream operations.
 *
 * @author Haifeng Li
 */
public interface BaseVector<T, S extends BaseStream<T, S>> extends Serializable {
    /** Return the (optional) name associated with vector. */
    String name();

    /** Returns the element type. */
    Class<?> type();

    /** Number of elements in the vector. */
    int size();

    /**
     * Returns the value at position i, which may be null.
     */
    T get(int i);

    /**
     * Returns the value at position i, which may be null.
     */
    default T apply(int i) {
        return get(i);
    }

    /**
     * Returns a (possibly parallel) Stream with this vector as its source.
     *
     * @return a (possibly parallel) Stream with this vector as its source.
     */
    S stream();
}