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
import smile.model.Model;

/**
 * Synchronous pre-deserialization verification SPI for SMILE ({@code .sml})
 * and ONNX ({@code .onnx}) model artifacts.
 *
 * <p>Registered CDI beans implementing {@code ModelArtifactVerifier} are
 * invoked by {@link InferenceService} and {@link OnnxService} across startup
 * model discovery, {@code reloadModel}, and dynamic {@code POST /api/v1/models/load}
 * <em>before</em> any payload bytes are deserialized by {@code Model.read(Path)}
 * or native {@code InferenceSession.create(String)}.
 *
 * @author Haifeng Li
 */
public interface ModelArtifactVerifier {
    /**
     * Verifies a model file on disk before it is deserialized.
     *
     * @param namespace    {@link ModelMetricsBinder#SMILE_NAMESPACE} ({@code "smile"})
     *                     or {@link ModelMetricsBinder#ONNX_NAMESPACE} ({@code "onnx"}).
     * @param artifactPath absolute, normalized path to the {@code .sml} or {@code .onnx} file.
     * @throws SecurityException if the artifact is unsigned, tampered, expired, or rejected by policy.
     */
    void verify(String namespace, Path artifactPath) throws SecurityException;

    /**
     * Optional post-deserialization hook for {@code .sml} models so a verifier
     * can cross-check in-memory model metadata ({@link Model#ID},
     * {@link Model#VERSION}, {@link Model#algorithm()}) against signed metadata
     * before the model is registered in the active catalog.
     *
     * @param artifactPath absolute, normalized path to the {@code .sml} file.
     * @param model        the deserialized {@link Model} instance.
     * @throws SecurityException if the in-memory model metadata contradicts the signed claim.
     */
    default void verifyLoadedSmileModel(Path artifactPath, Model model) throws SecurityException {
        // Default no-op
    }

    /**
     * Optional pre-persist hook invoked before an online-updated {@code .sml}
     * model overwrites its backing file on disk.
     *
     * @param artifactPath absolute, normalized path to the {@code .sml} file.
     * @param model        the in-memory {@link Model} instance being persisted.
     * @throws SecurityException if online persistence is disallowed by policy.
     */
    default void beforeModelPersist(Path artifactPath, Model model) throws SecurityException {
        // Default no-op
    }

    /**
     * Optional post-persist hook invoked immediately after an online-updated
     * {@code .sml} model has been atomically written to disk (e.g., to re-sign
     * the updated artifact with the node's local key).
     *
     * @param artifactPath absolute, normalized path to the persisted {@code .sml} file.
     * @param model        the persisted {@link Model} instance.
     * @throws SecurityException if post-persist re-signing fails.
     */
    default void onModelPersisted(Path artifactPath, Model model) throws SecurityException {
        // Default no-op
    }
}
