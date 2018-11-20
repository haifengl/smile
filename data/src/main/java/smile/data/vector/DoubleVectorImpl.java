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

import smile.data.type.ContinuousMeasure;

import java.util.Arrays;
import java.util.stream.DoubleStream;

/**
 * An immutable double vector.
 *
 * @author Haifeng Li
 */
class DoubleVectorImpl implements DoubleVector {
    /** The name of vector. */
    private String name;
    /** The vector data. */
    private double[] vector;
    /** The scale of measure. */
    private ContinuousMeasure scale;

    /** Constructor. */
    public DoubleVectorImpl(String name, double[] vector) {
        this.name = name;
        this.vector = vector;
    }

    @Override
    public ContinuousMeasure getScale() {
        return scale;
    }

    @Override
    public void setScale(ContinuousMeasure scale) {
        this.scale = scale;
    }

    @Override
    public byte getByte(int i) {
        throw new UnsupportedOperationException("cast double to byte");
    }

    @Override
    public short getShort(int i) {
        throw new UnsupportedOperationException("cast double to short");
    }

    @Override
    public int getInt(int i) {
        throw new UnsupportedOperationException("cast double to int");
    }

    @Override
    public long getLong(int i) {
        throw new UnsupportedOperationException("cast double to long");
    }

    @Override
    public float getFloat(int i) {
        throw new UnsupportedOperationException("cast double to float");
    }

    @Override
    public double getDouble(int i) {
        return vector[i];
    }

    @Override
    public Double get(int i) {
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
    public DoubleStream stream() {
        return Arrays.stream(vector);
    }

    @Override
    public String toString() {
        return toString(10);
    }
}