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

import java.nio.file.Path;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.BadRequestException;
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.io.Paths;
import smile.model.*;

/**
 * The metadata of model.
 *
 * @author Haifeng Li
 */
public class InferenceModel {
    private final String id;
    private final Model model;
    private final Path path;
    private final boolean isSoft;

    /** Constructor. */
    public InferenceModel(Model model, Path path) {
        this.id = model.getTag(Model.ID, Paths.getFileName(path)) + "-"
                + model.getTag(Model.VERSION, "1");
        this.model = model;
        this.path = path;
        if (model instanceof ClassificationModel m) {
            isSoft = m.classifier().isSoft();
        } else {
            isSoft = false;
        }
    }

    /**
     * Returns the model id.
     * @return the model id.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the model file path.
     * @return the model file path.
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the model metadata.
     * @return the model metadata.
     */
    public ModelMetadata metadata() {
        return new ModelMetadata(id, model);
    }

    /**
     * Performs inference using generic JSON input.
     * @param request the input data as a Map.
     * @return the inference result.
     * @throws BadRequestException if invalid request.
     */
    public InferenceResponse predict(JsonObject request) throws BadRequestException {
        return predict(json(request));
    }

    /**
     * Performs inference using CSV input.
     * @param request the input data in CSV format.
     * @return the inference result.
     * @throws BadRequestException if invalid request.
     */
    public InferenceResponse predict(String request) throws BadRequestException {
        return predict(csv(request));
    }

    /**
     * Performs inference.
     * @param x the input tuple.
     * @return the inference result.
     */
    public InferenceResponse predict(Tuple x) {
        double[] probabilities = null;
        Number y = switch (model) {
            case ClassificationModel m -> {
                if (isSoft) {
                    probabilities = new double[m.classifier().numClasses()];
                    yield m.predict(x, probabilities);
                } else {
                    yield m.predict(x);
                }
            }
            case RegressionModel m -> m.predict(x);
            default -> 0;
        };
        return new InferenceResponse(y, probabilities);
    }

    /**
     * Converts a JSON object to a SMILE tuple. Each field in the model
     * schema must be present as a key in {@code values}.
     *
     * @param values the JSON object.
     * @return the tuple.
     * @throws BadRequestException if the request is missing required fields.
     */
    public Tuple json(JsonObject values) throws BadRequestException {
        if (values == null) throw new BadRequestException("Request body must not be null");
        StructType schema = model.schema();
        var row = new Object[schema.length()];
        for (int i = 0; i < row.length; i++) {
            String name = schema.field(i).name();
            if (!values.containsKey(name)) {
                throw new BadRequestException("Missing required field: " + name);
            }
            row[i] = values.getValue(name);
        }
        return Tuple.of(schema, row);
    }

    /**
     * Converts a CSV line to a SMILE tuple. The number of comma-separated
     * tokens must be at least the number of fields in the model schema.
     *
     * @param line the CSV line.
     * @return the tuple.
     * @throws BadRequestException if the line has too few columns or an
     *         unparseable value.
     */
    public Tuple csv(String line) throws BadRequestException {
        if (line == null || line.isBlank()) throw new BadRequestException("CSV line must not be blank");
        var values = line.split(",", -1);   // -1 keeps trailing empty tokens
        StructType schema = model.schema();
        if (values.length < schema.length()) {
            throw new BadRequestException(
                    "Expected at least %d CSV columns but got %d".formatted(schema.length(), values.length));
        }

        try {
            var row = new Object[schema.length()];
            for (int i = 0; i < row.length; i++) {
                row[i] = schema.field(i).valueOf(values[i].trim());
            }
            return Tuple.of(schema, row);
        } catch (Exception ex) {
            throw new BadRequestException("Failed to parse CSV: " + ex.getMessage());
        }
    }
}
