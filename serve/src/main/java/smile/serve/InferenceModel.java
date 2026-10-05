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

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.io.Paths;
import smile.model.*;

/**
 * The metadata and runtime wrapper of a loaded SMILE model.
 *
 * @author Haifeng Li
 */
public class InferenceModel {

    /** Lifecycle state of the model. */
    public enum State {
        ACTIVE,
        UNLOADING,
        UNLOADED
    }

    private final String id;
    private final Model model;
    private final Path path;
    private final boolean isSoft;
    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);
    private final ModelMetrics metrics = new ModelMetrics();

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
     * Returns the current lifecycle state of the model.
     * @return model lifecycle state.
     */
    public State state() {
        return state.get();
    }

    /**
     * Returns the operational metrics for this model.
     * @return model metrics.
     */
    public ModelMetrics metrics() {
        return metrics;
    }

    /**
     * Initiates graceful unload of this model, waiting for in-flight requests to complete.
     *
     * @param timeout max duration to wait for in-flight requests.
     * @return {@code true} if all in-flight requests drained before timeout.
     */
    public boolean unload(Duration timeout) {
        state.set(State.UNLOADING);
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (metrics.inFlightRequests() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        boolean drained = (metrics.inFlightRequests() == 0);
        state.set(State.UNLOADED);
        return drained;
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
     * Returns the underlying SMILE {@link Model}.
     *
     * @return the loaded model.
     */
    public Model model() {
        return model;
    }

    /**
     * Returns the model metadata.
     * @return the model metadata.
     */
    public ModelMetadata metadata() {
        return new ModelMetadata(id, model);
    }

    /**
     * Returns true if the underlying model supports SHAP feature attributions.
     *
     * @return true if SHAP is supported.
     */
    public boolean supportsShap() {
        return model.supportsShap();
    }

    /**
     * Calculates SHAP values for an input tuple.
     *
     * @param x the input tuple.
     * @return {@code double[]} for regression, or {@code double[k][p]} for classification.
     * @throws UnsupportedOperationException if SHAP is not supported.
     */
    public Object shap(Tuple x) {
        return model.shap(x);
    }

    /**
     * Performs inference using generic JSON input.
     * @param request the input data as a Map.
     * @return the inference result.
     * @throws BadRequestException if invalid request.
     */
    public Prediction predict(JsonObject request) throws BadRequestException {
        return predict(request, false);
    }

    /**
     * Performs inference using generic JSON input with optional explanations.
     *
     * @param request the input data as a Map.
     * @param explain whether to generate model explanations.
     * @return the inference result.
     * @throws BadRequestException if invalid request.
     */
    public Prediction predict(JsonObject request, boolean explain) throws BadRequestException {
        return predict(json(request), explain);
    }

    /**
     * Performs inference using CSV input.
     * @param request the input data in CSV format.
     * @return the inference result.
     * @throws BadRequestException if invalid request.
     */
    public Prediction predict(String request) throws BadRequestException {
        return predict(request, false);
    }

    /**
     * Performs inference using CSV input with optional explanations.
     *
     * @param request the input data in CSV format.
     * @param explain whether to generate model explanations.
     * @return the inference result.
     * @throws BadRequestException if invalid request.
     */
    public Prediction predict(String request, boolean explain) throws BadRequestException {
        return predict(csv(request), explain);
    }

    /**
     * Performs inference.
     * @param x the input tuple.
     * @return the inference result.
     */
    public Prediction predict(Tuple x) {
        return predict(x, false);
    }

    /**
     * Performs inference with optional explanations.
     *
     * @param x the input tuple.
     * @param explain whether to generate model explanations.
     * @return the inference result.
     */
    public Prediction predict(Tuple x, boolean explain) {
        if (state.get() != State.ACTIVE) {
            throw new ServiceUnavailableException("Model " + id + " is " + state.get().name().toLowerCase());
        }
        long start = metrics.startExecution();
        boolean success = false;
        try {
            Prediction p = model.infer(x, isSoft, explain);
            success = true;
            return p;
        } finally {
            metrics.finishExecution(start, success);
        }
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
