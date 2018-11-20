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
class BooleanVectorImpl implements BooleanVector {
    /** The name of vector. */
    private String name;
    /** The vector data. */
    private boolean[] vector;

    /** Constructor. */
    public BooleanVectorImpl(String name, boolean[] vector) {
        this.name = name;
        this.vector = vector;
    }

    @Override
    public boolean getBoolean(int i) {
        return vector[i];
    }

    @Override
    public byte getByte(int i) {
        return vector[i] ? (byte) 1 : 0;
    }

    @Override
    public short getShort(int i) {
        return vector[i] ? (short) 1 : 0;
    }

    @Override
    public int getInt(int i) {
        return vector[i] ? 1 : 0;
    }

    @Override
    public long getLong(int i) {
        return vector[i] ? 1 : 0;
    }

    @Override
    public float getFloat(int i) {
        return vector[i] ? 1 : 0;
    }

    @Override
    public double getDouble(int i) {
        return vector[i] ? 1 : 0;
    }

    @Override
    public Boolean get(int i) {
        return vector[i];
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public IntStream stream() {
        return IntStream.range(0, vector.length).map(i -> vector[i] ? 1 : 0);
    }

    @Override
    public String toString() {
        return toString(10);
    }
}