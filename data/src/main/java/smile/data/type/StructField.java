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
 * A field in a Struct data type.
 *
 * @author Haifeng Li
 */
public class StructField {
    /** Field name. */
    public final String name;
    /** Field data type. */
    public final DataType type;

    /**
     * Constructor with the ISO date formatter that formats
     * or parses a date without an offset, such as '2011-12-03'.
     */
    public StructField(String name, DataType type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("%s : %s", name, type.name());
    }

    /** Returns the string representation of the field with given value. */
    public String toString(Object o) {
        return String.format("%s : %s", name, type.toString(o));
    }
}
