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
 * The metadata of model.
 *
 * @author Haifeng Li
 */
public class ModelMetadata {
    /** Data type. */
    public record Type(String type, boolean nullable) {}
    /** The model id. */
    public String id;
    /** The learning algorithm. */
    public String algorithm;
    /** The data schema. */
    public Map<String, Type> schema;
    /** The model tags. */
    public Properties tags;

    /** Constructor. */
    public ModelMetadata() {
    }

    /** Constructor. */
    public ModelMetadata(String id, Model model) {
        this.id = id;
        this.algorithm = model.algorithm();
        this.schema = new TreeMap<>();
        this.tags = model.tags();
        for (var field : model.schema().fields()) {
            schema.put(field.name(), new Type(field.dtype().name(), field.dtype().isNullable()));
        }
    }
}
