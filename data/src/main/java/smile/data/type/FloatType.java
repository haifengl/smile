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
package smile.data.type;

/**
 * Float data type.
 *
 * @author Haifeng Li
 */
public class FloatType implements DataType {

    /** Singleton instance. */
    static FloatType instance = new FloatType();

    /**
     * Private constructor for singleton design pattern.
     */
    private FloatType() {
    }

    @Override
    public boolean isFloat() {
        return true;
    }

    @Override
    public String name() {
        return "float";
    }

    @Override
    public ID id() {
        return ID.Float;
    }

    @Override
    public String toString() {
        return "float";
    }

    @Override
    public Float valueOf(String s) {
        return Float.valueOf(s);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FloatType;
    }
}
