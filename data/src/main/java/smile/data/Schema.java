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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import smile.data.type.Measure;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * A schema is the description of the structure of data.
 *
 * A schema is described by StructType.
 *
 * @author Haifeng Li
 */
public class Schema {
    /** Data types of fields. */
    private final StructType struct;
    /** Optional scale of measurement of fields. */
    private final Map<String, Measure> measures;

    /**
     * Constructor.
     * @param struct the data structure.
     */
    public Schema(StructType struct) {
        this.struct = struct;
        measures = new HashMap<>(struct.fields().length * 4 / 3);
    }

    /** Returns the fields. */
    public StructField[] fields() {
        return struct.fields();
    }

    /** Return the field of given name. */
    public StructField field(String name) {
        return struct.field(name);
    }

    /** Returns the index of a field. */
    public int fieldIndex(String field) {
        return struct.fieldIndex(field);
    }

    /** Sets the scale of measurement of a field. */
    public Schema setMeasure(String field, Measure measure) {
        measures.put(field, measure);
        return this;
    }

    /** Gets the scale of measurement of a field. */
    public Optional<Measure> getMeasure(String field) {
        return Optional.ofNullable(measures.get(field));
    }
}
