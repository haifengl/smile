/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import smile.data.measure.Measure;
import smile.data.type.DataType;
import smile.model.Model;

/**
 * The metadata of model.
 *
 * @author Haifeng Li
 */
public class ModelMetadata {
    public record Type(String type, boolean nullable) {}
    public String id;
    public String algorithm;
    public Map<String, Type> schema;
    public Properties tags;

    public ModelMetadata() {
    }

    public ModelMetadata(String id, Model model) {
        this.id = id;
        this.algorithm = model.algorithm();
        this.schema = new TreeMap<>();
        this.tags = model.properties();
        for (var field : model.schema().fields()) {
            schema.put(field.name(), new Type(field.dtype().name(), field.dtype().isNullable()));
        }
    }
}
