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

import java.time.Instant;
import smile.model.Prediction;

/**
 * Immutable event emitted when a model inference, online update, or lifecycle
 * transition occurs in {@link InferenceService} or {@link OnnxService}.
 *
 * @param timestamp    the UTC timestamp when the event was recorded.
 * @param namespace    the model namespace ({@link ModelMetricsBinder#SMILE_NAMESPACE}
 *                     or {@link ModelMetricsBinder#ONNX_NAMESPACE}).
 * @param modelId      the model identifier.
 * @param action       the action kind.
 * @param input        the serialized input payload (JSON or CSV), or {@code null} for lifecycle events.
 * @param prediction   the SMILE {@link Prediction} result when applicable, or {@code null}.
 * @param output       the serialized output payload (JSON) for ONNX or lifecycle metadata, or {@code null}.
 * @param latencyNanos the execution duration in nanoseconds ({@code 0} for lifecycle events).
 * @param explain      whether feature explanations (SHAP) were requested.
 * @param success      whether the operation completed without error.
 * @author Haifeng Li
 */
public record InferenceEvent(
        Instant timestamp,
        String namespace,
        String modelId,
        Action action,
        String input,
        Prediction prediction,
        String output,
        long latencyNanos,
        boolean explain,
        boolean success
) {
    /**
     * Event action kind.
     */
    public enum Action {
        /** Single-request inference. */
        PREDICT,
        /** Streaming line-by-line inference. */
        STREAM_PREDICT,
        /** Online learning model update. */
        ONLINE_UPDATE,
        /** Model loaded into active memory. */
        MODEL_LOADED,
        /** Model reloaded from disk. */
        MODEL_RELOADED,
        /** Model unloaded from active memory. */
        MODEL_UNLOADED
    }

    /**
     * Creates an event for a SMILE model prediction.
     *
     * @param modelId      the model ID.
     * @param action       {@link Action#PREDICT} or {@link Action#STREAM_PREDICT}.
     * @param input        the JSON or CSV input string.
     * @param prediction   the prediction result.
     * @param latencyNanos the execution latency in nanoseconds.
     * @param explain      whether explanations were computed.
     * @return the event instance.
     */
    public static InferenceEvent smilePredict(String modelId, Action action, String input,
                                              Prediction prediction, long latencyNanos, boolean explain) {
        return new InferenceEvent(
                Instant.now(),
                ModelMetricsBinder.SMILE_NAMESPACE,
                modelId,
                action,
                input,
                prediction,
                null,
                Math.max(0L, latencyNanos),
                explain,
                prediction != null
        );
    }

    /**
     * Creates an event for an ONNX model prediction.
     *
     * @param modelId      the model ID.
     * @param action       {@link Action#PREDICT} or {@link Action#STREAM_PREDICT}.
     * @param input        the JSON or CSV input string.
     * @param output       the JSON output string.
     * @param latencyNanos the execution latency in nanoseconds.
     * @return the event instance.
     */
    public static InferenceEvent onnxPredict(String modelId, Action action, String input,
                                             String output, long latencyNanos) {
        return new InferenceEvent(
                Instant.now(),
                ModelMetricsBinder.ONNX_NAMESPACE,
                modelId,
                action,
                input,
                null,
                output,
                Math.max(0L, latencyNanos),
                false,
                output != null
        );
    }

    /**
     * Creates an event for an online model update.
     *
     * @param modelId      the model ID.
     * @param input        the JSON or CSV update payload.
     * @param persisted    whether the model was persisted to disk.
     * @param latencyNanos the execution latency in nanoseconds.
     * @return the event instance.
     */
    public static InferenceEvent onlineUpdate(String modelId, String input, boolean persisted, long latencyNanos) {
        return new InferenceEvent(
                Instant.now(),
                ModelMetricsBinder.SMILE_NAMESPACE,
                modelId,
                Action.ONLINE_UPDATE,
                input,
                null,
                "{\"persisted\":" + persisted + "}",
                Math.max(0L, latencyNanos),
                false,
                true
        );
    }

    /**
     * Creates a model lifecycle event.
     *
     * @param namespace the model namespace.
     * @param modelId   the model ID.
     * @param action    {@link Action#MODEL_LOADED}, {@link Action#MODEL_RELOADED}, or {@link Action#MODEL_UNLOADED}.
     * @param details   optional details (such as file path or drain status).
     * @return the event instance.
     */
    public static InferenceEvent lifecycle(String namespace, String modelId, Action action, String details) {
        return new InferenceEvent(
                Instant.now(),
                namespace,
                modelId,
                action,
                null,
                null,
                details,
                0L,
                false,
                true
        );
    }
}
