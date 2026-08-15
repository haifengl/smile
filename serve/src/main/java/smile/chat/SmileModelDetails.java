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
package smile.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.model.ClassificationModel;
import smile.model.Model;
import smile.model.RegressionModel;
import smile.serve.ModelMetadata;
import smile.validation.ClassificationMetrics;
import smile.validation.RegressionMetrics;

/**
 * SMILE {@code .sml} details returned by {@code GET /models/{id}}.
 *
 * @param formula    model formula string, or {@code null}.
 * @param schema     input feature schema.
 * @param tags       model tags as a string map.
 * @param train      training metrics (finite values only), or {@code null}.
 * @param validation cross-validation metrics, or {@code null}.
 * @param test       held-out test metrics, or {@code null}.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmileModelDetails(
        String formula,
        Map<String, ModelMetadata.FieldType> schema,
        Map<String, String> tags,
        Map<String, Object> train,
        Map<String, Object> validation,
        Map<String, Object> test) {

    /**
     * Builds details from a loaded SMILE {@link Model} and its serve metadata.
     *
     * @param model the loaded model.
     * @param meta  schema-bearing metadata DTO.
     * @return smile details for retrieve responses.
     */
    public static SmileModelDetails of(Model model, ModelMetadata meta) {
        String formula = model.formula() != null ? model.formula().toString() : null;
        Map<String, Object> train = null;
        Map<String, Object> validation = null;
        Map<String, Object> test = null;
        switch (model) {
            case ClassificationModel c -> {
                train = classificationMetrics(c.train());
                validation = classificationMetrics(c.validation());
                test = classificationMetrics(c.test());
            }
            case RegressionModel r -> {
                train = regressionMetrics(r.train());
                validation = regressionMetrics(r.validation());
                test = regressionMetrics(r.test());
            }
            default -> { }
        }
        return new SmileModelDetails(
                formula,
                meta.schema(),
                tagsToMap(model.tags()),
                train,
                validation,
                test);
    }

    private static Map<String, String> tagsToMap(Properties tags) {
        if (tags == null || tags.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new TreeMap<>();
        for (String name : tags.stringPropertyNames()) {
            map.put(name, tags.getProperty(name));
        }
        return map;
    }

    private static Map<String, Object> classificationMetrics(ClassificationMetrics m) {
        if (m == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putFinite(map, "fit_time", m.fitTime());
        putFinite(map, "score_time", m.scoreTime());
        map.put("size", m.size());
        map.put("error", m.error());
        putFinite(map, "accuracy", m.accuracy());
        putFinite(map, "sensitivity", m.sensitivity());
        putFinite(map, "specificity", m.specificity());
        putFinite(map, "precision", m.precision());
        putFinite(map, "f1", m.f1());
        putFinite(map, "mcc", m.mcc());
        putFinite(map, "auc", m.auc());
        putFinite(map, "logloss", m.logloss());
        putFinite(map, "cross_entropy", m.crossEntropy());
        return map;
    }

    private static Map<String, Object> regressionMetrics(RegressionMetrics m) {
        if (m == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putFinite(map, "fit_time", m.fitTime());
        putFinite(map, "score_time", m.scoreTime());
        map.put("size", m.size());
        putFinite(map, "rss", m.rss());
        putFinite(map, "mse", m.mse());
        putFinite(map, "rmse", m.rmse());
        putFinite(map, "mad", m.mad());
        putFinite(map, "r2", m.r2());
        return map;
    }

    private static void putFinite(Map<String, Object> map, String key, double value) {
        if (Double.isFinite(value)) {
            map.put(key, value);
        }
    }
}
