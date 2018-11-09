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

import java.text.ParseException;
import java.util.Arrays;

/**
 * Array of primitive data type.
 *
 * @author Haifeng Li
 */
public class ArrayType implements DataType {
    /** Element data type. */
    private DataType type;

    /**
     * Constructor.
     * @param type element data type.
     */
    public ArrayType(DataType type) {
        this.type = type;
    }

    @Override
    public String name() {
        return String.format("array[%s]", type.name());
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public String toString(Object o) {
        return String.format("[%s]", Arrays.toString((Object[]) o));
    }

    @Override
    public Object[] valueOf(String s) throws ParseException {
        // strip surrounding []
        String[] elements = s.substring(1, s.length() - 1).split(",");
        Object[] array = new Object[elements.length];
        for (int i = 0; i < elements.length; i++) {
            array[i] = type.valueOf(elements[i]);
        }
        return array;
    }
}
