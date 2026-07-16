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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.BadRequestException;
import smile.onnx.ElementType;
import smile.onnx.InferenceSession;
import smile.onnx.NodeInfo;
import smile.onnx.OrtValue;
import smile.onnx.TensorInfo;

/**
 * Wraps a loaded ONNX {@link InferenceSession} and exposes a high-level
 * inference API that accepts JSON or CSV input and returns JSON output.
 *
 * <h2>Input format</h2>
 * <p>Each request body (for single-shot inference) is a JSON object whose
 * keys are the model's input names and whose values are flat JSON arrays of
 * numbers. For example:
 * <pre>{@code
 * {
 *   "input": [1.0, 2.0, 3.0, 4.0]
 * }
 * }</pre>
 * The model's declared input shape is used to construct the OrtValue tensor;
 * dynamic dimensions ({@code -1}) are inferred from the supplied array length.
 *
 * <p>For single-input models a CSV line is also accepted: each comma-separated
 * float value is treated as one element of the first input tensor.
 *
 * <h2>Output format</h2>
 * <p>The response is a JSON object whose keys are the model's output names and
 * whose values are flat JSON arrays of numbers, e.g.:
 * <pre>{@code
 * {
 *   "output": [0.1, 0.7, 0.2]
 * }
 * }</pre>
 *
 * @author Haifeng Li
 */
public class OnnxModel implements AutoCloseable {

    /** The model ID (file stem). */
    private final String id;
    /** The model file path. */
    private final Path path;
    /** The loaded ONNX inference session. */
    private final InferenceSession session;
    /** Cached metadata DTO. */
    private final OnnxModelInfo info;

    /**
     * Constructs an {@code OnnxModel} from an open session.
     *
     * @param id      the model ID.
     * @param path    the source file path.
     * @param session the loaded ONNX inference session (this model takes ownership).
     */
    public OnnxModel(String id, Path path, InferenceSession session) {
        this.id = id;
        this.path = path;
        this.session = session;

        // Build the info DTO eagerly
        var onnxMeta = session.metadata();
        var inputs = session.inputInfos().stream()
                .map(OnnxModelInfo.NodeDescriptor::of).toList();
        var outputs = session.outputInfos().stream()
                .map(OnnxModelInfo.NodeDescriptor::of).toList();
        this.info = new OnnxModelInfo(
                id,
                onnxMeta.graphName(),
                onnxMeta.description(),
                onnxMeta.version(),
                inputs,
                outputs,
                onnxMeta.customMetadata());
    }

    /**
     * Returns the model ID.
     *
     * @return the model ID.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the model file path.
     *
     * @return the model file path.
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the model metadata DTO.
     *
     * @return the model info.
     */
    public OnnxModelInfo info() {
        return info;
    }

    // -----------------------------------------------------------------------
    // Inference — JSON object
    // -----------------------------------------------------------------------

    /**
     * Runs inference with JSON-encoded inputs.
     *
     * <p>The JSON object must contain one key per model input whose value is
     * a JSON array of numeric elements. The tensor shape is taken from the
     * model's declared input shape; dynamic dimensions are inferred from the
     * array length.
     *
     * @param request a JSON object mapping input names to flat arrays.
     * @return a JSON object mapping output names to flat arrays.
     * @throws BadRequestException if a required input is missing or the array
     *         size does not match the declared static shape.
     */
    public JsonObject predict(JsonObject request) throws BadRequestException {
        if (request == null) throw new BadRequestException("Request body must not be null");

        List<NodeInfo> inputInfos = session.inputInfos();
        Map<String, OrtValue> inputs = new LinkedHashMap<>(inputInfos.size());

        try {
            for (NodeInfo info : inputInfos) {
                String name = info.name();
                JsonArray arr = request.getJsonArray(name);
                if (arr == null) {
                    throw new BadRequestException("Missing required input: " + name);
                }
                inputs.put(name, jsonArrayToOrtValue(arr, info.tensorInfo()));
            }

            OrtValue[] outputs = session.run(inputs);
            try {
                return ortValuesToJson(session.outputInfos(), outputs);
            } finally {
                for (var v : outputs) v.close();
            }
        } finally {
            for (var v : inputs.values()) v.close();
        }
    }

    // -----------------------------------------------------------------------
    // Inference — CSV line (single-input models)
    // -----------------------------------------------------------------------

    /**
     * Runs inference with a CSV-encoded input line.
     *
     * <p>This convenience method is intended for single-input models. The
     * comma-separated floats are treated as the flat element array of the
     * model's first input. For multi-input models use
     * {@link #predict(JsonObject)} instead.
     *
     * @param line a comma-separated line of float values.
     * @return a JSON object mapping output names to flat arrays.
     * @throws BadRequestException if the line is blank, contains non-numeric
     *         tokens, or the element count does not match the static shape.
     */
    public JsonObject predict(String line) throws BadRequestException {
        if (line == null || line.isBlank()) {
            throw new BadRequestException("CSV line must not be blank");
        }

        List<NodeInfo> inputInfos = session.inputInfos();
        if (inputInfos.isEmpty()) {
            throw new BadRequestException("Model has no inputs");
        }

        // Parse the CSV as float values
        String[] tokens = line.split(",", -1);
        float[] data = new float[tokens.length];
        try {
            for (int i = 0; i < tokens.length; i++) {
                data[i] = Float.parseFloat(tokens[i].trim());
            }
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Failed to parse CSV: " + ex.getMessage());
        }

        // Use the first input node only
        NodeInfo firstInput = inputInfos.getFirst();
        TensorInfo ti = firstInput.tensorInfo();
        long[] shape = resolveShape(ti, data.length);

        Map<String, OrtValue> inputs = new LinkedHashMap<>();
        try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {
            inputs.put(firstInput.name(), input);

            // If the model has more than one input, add empty placeholders
            // for any remaining static-shape inputs (advanced use-cases
            // should use the JSON endpoint instead).
            if (inputInfos.size() > 1) {
                throw new BadRequestException(
                        "CSV input is only supported for single-input models. "
                        + "Use the JSON endpoint for multi-input models.");
            }

            OrtValue[] outputs = session.run(inputs);
            try {
                return ortValuesToJson(session.outputInfos(), outputs);
            } finally {
                for (var v : outputs) v.close();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Converts a flat JSON array to an {@link OrtValue} tensor.
     * The element type is taken from the model's declared input type info;
     * defaults to {@link ElementType#FLOAT} if the info is absent.
     */
    private static OrtValue jsonArrayToOrtValue(JsonArray arr, TensorInfo ti) {
        int n = arr.size();
        ElementType elemType = (ti != null) ? ti.elementType() : ElementType.FLOAT;
        long[] shape = (ti != null) ? resolveShape(ti, n) : new long[]{1, n};

        return switch (elemType) {
            case FLOAT -> {
                float[] data = new float[n];
                for (int i = 0; i < n; i++) data[i] = ((Number) arr.getValue(i)).floatValue();
                yield OrtValue.fromFloatArray(data, shape);
            }
            case DOUBLE -> {
                double[] data = new double[n];
                for (int i = 0; i < n; i++) data[i] = ((Number) arr.getValue(i)).doubleValue();
                yield OrtValue.fromDoubleArray(data, shape);
            }
            case INT32 -> {
                int[] data = new int[n];
                for (int i = 0; i < n; i++) data[i] = ((Number) arr.getValue(i)).intValue();
                yield OrtValue.fromIntArray(data, shape);
            }
            case INT64 -> {
                long[] data = new long[n];
                for (int i = 0; i < n; i++) data[i] = ((Number) arr.getValue(i)).longValue();
                yield OrtValue.fromLongArray(data, shape);
            }
            case INT8, UINT8, BOOL -> {
                byte[] data = new byte[n];
                for (int i = 0; i < n; i++) data[i] = ((Number) arr.getValue(i)).byteValue();
                yield OrtValue.fromByteArray(data, shape);
            }
            default -> {
                // Fallback: treat as float
                float[] data = new float[n];
                for (int i = 0; i < n; i++) data[i] = ((Number) arr.getValue(i)).floatValue();
                yield OrtValue.fromFloatArray(data, shape);
            }
        };
    }

    /**
     * Resolves the concrete tensor shape from the declared shape and the
     * actual number of elements provided. Dynamic dimensions ({@code -1})
     * are filled by dividing the element count by the product of all static
     * dimensions.
     *
     * @param ti     the declared tensor info; may be {@code null}.
     * @param nElems the number of elements in the flat input array.
     * @return the concrete shape array.
     * @throws BadRequestException if a static shape does not divide evenly.
     */
    private static long[] resolveShape(TensorInfo ti, int nElems) {
        if (ti == null || ti.shape() == null || ti.shape().length == 0) {
            return new long[]{1, nElems};
        }
        long[] declaredShape = ti.shape();
        // If no dynamic dims, validate and return as-is
        if (!ti.isDynamic()) {
            long expected = ti.elementCount();
            if (expected != nElems) {
                throw new BadRequestException(
                        "Input has %d elements but model expects %d".formatted(nElems, expected));
            }
            return declaredShape.clone();
        }
        // Resolve the single dynamic dimension
        long[] shape = declaredShape.clone();
        int dynamicIdx = -1;
        long staticProduct = 1;
        for (int i = 0; i < shape.length; i++) {
            if (shape[i] < 0) {
                if (dynamicIdx >= 0) {
                    // Multiple dynamic dims: use flat [1, nElems]
                    return new long[]{1, nElems};
                }
                dynamicIdx = i;
            } else {
                staticProduct *= shape[i];
            }
        }
        if (staticProduct == 0) return new long[]{1, nElems};
        shape[dynamicIdx] = nElems / staticProduct;
        return shape;
    }

    /**
     * Converts an array of output {@link OrtValue}s into a JSON object.
     * Each tensor is flattened into a JSON array of numbers.
     */
    private static JsonObject ortValuesToJson(List<NodeInfo> outputInfos,
                                               OrtValue[] outputs) {
        var result = new JsonObject();
        for (int i = 0; i < outputs.length; i++) {
            String name = (i < outputInfos.size()) ? outputInfos.get(i).name() : "output_" + i;
            result.put(name, ortValueToJsonArray(outputs[i]));
        }
        return result;
    }

    /**
     * Flattens a tensor {@link OrtValue} into a {@link JsonArray} of numbers.
     * Supports {@code FLOAT}, {@code DOUBLE}, {@code INT32}, {@code INT64},
     * {@code INT8}, {@code UINT8}, and {@code BOOL} element types.
     */
    private static JsonArray ortValueToJsonArray(OrtValue value) {
        if (!value.isTensor()) {
            return new JsonArray();
        }
        TensorInfo ti = value.tensorInfo();
        var arr = new JsonArray();
        switch (ti.elementType()) {
            case FLOAT -> { for (float v : value.toFloatArray())   arr.add(v); }
            case DOUBLE -> { for (double v : value.toDoubleArray()) arr.add(v); }
            case INT32 -> { for (int v : value.toIntArray())        arr.add(v); }
            case INT64 -> { for (long v : value.toLongArray())      arr.add(v); }
            case INT8  -> { for (byte v : value.toByteArray())      arr.add(v); }
            case UINT8, BOOL -> { for (byte v : value.toByteArray()) arr.add(Byte.toUnsignedInt(v)); }
            default    -> { for (float v : value.toFloatArray())    arr.add(v); }
        }
        return arr;
    }

    @Override
    public void close() {
        session.close();
    }

    @Override
    public String toString() {
        return "OnnxModel{id='" + id + "', path=" + path + "}";
    }
}

