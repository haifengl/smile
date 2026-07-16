/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import smile.model.Model;

/**
 * Immutable metadata describing a loaded inference model.
 *
 * @param id        the model identifier ({@code <name>-<version>}).
 * @param algorithm the learning algorithm name.
 * @param schema    the input feature schema ({@code field → type descriptor}).
 * @param tags      arbitrary key/value properties stored with the model.
 * @author Haifeng Li
 */
public record ModelMetadata(String id,
                             String algorithm,
                             Map<String, FieldType> schema,
                             Properties tags) {

    /**
     * Descriptor for a single input feature field.
     *
     * @param type     the data-type name (e.g. {@code "float"}).
     * @param nullable whether the field accepts {@code null} values.
     */
    public record FieldType(String type, boolean nullable) {}

    /**
     * Constructs metadata from a loaded {@link Model}.
     *
     * @param id    the composite model identifier.
     * @param model the loaded model.
     */
    public ModelMetadata(String id, Model model) {
        this(id, model.algorithm(), buildSchema(model), model.tags());
    }

    private static Map<String, FieldType> buildSchema(Model model) {
        var schema = new TreeMap<String, FieldType>();
        for (var field : model.schema().fields()) {
            schema.put(field.name(), new FieldType(field.dtype().name(), field.dtype().isNullable()));
        }
        return schema;
    }
}
