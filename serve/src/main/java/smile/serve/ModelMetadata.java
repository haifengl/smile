/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import smile.model.ClassificationModel;
import smile.model.Model;
import smile.model.RegressionModel;

/**
 * Immutable metadata describing a loaded inference model.
 *
 * @param id           the model identifier ({@code <name>-<version>}).
 * @param algorithm    the learning algorithm name.
 * @param schema       the input feature schema ({@code field → type descriptor}).
 * @param online       whether the model supports online learning updates.
 * @param response     the response variable name for supervised models, or {@code null} for unsupervised models.
 * @param responseType the response variable type ({@code "integer"} or {@code "number"}) for supervised models, or {@code null}.
 * @param tags         arbitrary key/value properties stored with the model.
 * @author Haifeng Li
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelMetadata(String id,
                             String algorithm,
                             Map<String, FieldType> schema,
                             boolean online,
                             String response,
                             String responseType,
                             Properties tags) {

    /**
     * Descriptor for a single input feature field.
     *
     * @param type     the data-type name (e.g. {@code "float"}).
     * @param nullable whether the field accepts {@code null} values.
     */
    public record FieldType(String type, boolean nullable) {}

    /**
     * Constructs metadata without online/response details.
     *
     * @param id        the model identifier.
     * @param algorithm the learning algorithm name.
     * @param schema    the input feature schema.
     * @param tags      model metadata tags.
     */
    public ModelMetadata(String id, String algorithm, Map<String, FieldType> schema, Properties tags) {
        this(id, algorithm, schema, false, null, null, tags);
    }

    /**
     * Constructs metadata from a loaded {@link Model}.
     *
     * @param id    the composite model identifier.
     * @param model the loaded model.
     */
    public ModelMetadata(String id, Model model) {
        this(id, model.algorithm(), buildSchema(model), model.isOnline(),
                extractResponseName(model), extractResponseType(model), model.tags());
    }

    private static String extractResponseName(Model model) {
        if ((model instanceof ClassificationModel || model instanceof RegressionModel)
                && model.formula() != null && model.formula().response() != null) {
            var vars = model.formula().response().variables();
            if (!vars.isEmpty()) {
                return vars.iterator().next();
            }
        }
        return null;
    }

    private static String extractResponseType(Model model) {
        if (model instanceof ClassificationModel) {
            return "integer";
        }
        if (model instanceof RegressionModel) {
            return "double";
        }
        return null;
    }

    private static Map<String, FieldType> buildSchema(Model model) {
        var schema = new TreeMap<String, FieldType>();
        for (var field : model.schema().fields()) {
            schema.put(field.name(), new FieldType(field.dtype().name(), field.dtype().isNullable()));
        }
        return schema;
    }
}
