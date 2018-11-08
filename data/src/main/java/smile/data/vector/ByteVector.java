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
 * An immutable byte vector.
 *
 * @author Haifeng Li
 */
public interface ByteVector extends BaseVector<Byte, Integer, IntStream> {
    @Override
    default Class<Byte> type() {
        return byte.class;
    }

    /**
     * Returns the value at position i.
     */
    byte getByte(int i);

    /** Creates a named byte vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static ByteVector of(String name, byte[] vector) {
        return new ByteVectorImpl(name, vector);
    }
}