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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Properties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * OpenAI-compatible model object returned by {@code GET /models} and
 * {@code GET /models/{id}}, extended with a SMILE {@code kind} discriminator
 * and optional type-specific detail blocks on retrieve.
 *
 * @param id           model identifier referenced by API endpoints.
 * @param created      Unix epoch seconds when the model became available.
 * @param object       always {@code "model"}.
 * @param ownedBy      organization / hub owner.
 * @param shutdownDate optional retirement date; always {@code null} for smile-serve.
 * @param kind         model category: {@link #KIND_LLM}, {@link #KIND_ONNX}, or a
 *                     SMILE algorithm name (e.g. {@code "random-forest"}).
 * @param smile        SMILE {@code .sml} details (retrieve only).
 * @param onnx         ONNX graph details (retrieve only).
 * @param llm          chat LLM architecture details (retrieve only).
 *
 * @author Haifeng Li
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/list">OpenAI List models</a>
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/retrieve">OpenAI Retrieve model</a>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ModelObject(
        String id,
        long created,
        @JsonProperty("object") String object,
        String ownedBy,
        String shutdownDate,
        String kind,
        @JsonInclude(JsonInclude.Include.NON_NULL) SmileModelDetails smile,
        @JsonInclude(JsonInclude.Include.NON_NULL) OnnxModelDetails onnx,
        @JsonInclude(JsonInclude.Include.NON_NULL) LlmModelDetails llm) {

    /** Fallback {@code owned_by} when no owner metadata is available. */
    public static final String UNKNOWN_OWNER = "Unknown";

    /** {@code kind} for chat / completion LLMs. */
    public static final String KIND_LLM = "LLM";

    /** {@code kind} for ONNX Runtime graphs. */
    public static final String KIND_ONNX = "ONNX";

    /**
     * Builds a lean list/catalog model object (no type-specific details).
     *
     * @param id      public model id.
     * @param created load / availability timestamp (Unix seconds).
     * @param ownedBy owner string.
     * @param kind    {@link #KIND_LLM}, {@link #KIND_ONNX}, or a SMILE algorithm name.
     * @return the model object.
     */
    public static ModelObject of(String id, long created, String ownedBy, String kind) {
        return of(id, created, ownedBy, kind, null, null, null);
    }

    /**
     * Builds a model object, optionally with retrieve-only detail blocks.
     *
     * @param id      public model id.
     * @param created load / availability timestamp (Unix seconds).
     * @param ownedBy owner string.
     * @param kind    {@link #KIND_LLM}, {@link #KIND_ONNX}, or a SMILE algorithm name.
     * @param smile   SMILE details, or {@code null}.
     * @param onnx    ONNX details, or {@code null}.
     * @param llm     LLM details, or {@code null}.
     * @return the model object.
     */
    public static ModelObject of(String id, long created, String ownedBy, String kind,
                                 SmileModelDetails smile, OnnxModelDetails onnx, LlmModelDetails llm) {
        String owner = (ownedBy == null || ownedBy.isBlank()) ? UNKNOWN_OWNER : ownedBy;
        String k = (kind == null || kind.isBlank()) ? UNKNOWN_OWNER : kind.trim();
        return new ModelObject(id, created, "model", owner, null, k, smile, onnx, llm);
    }

    /**
     * Resolves {@code owned_by} from SMILE model tags: prefers {@code author},
     * then {@code owner} (case-insensitive). Returns {@link #UNKNOWN_OWNER}
     * when neither is present.
     *
     * @param tags model tags, or {@code null}.
     * @return the owner string.
     */
    public static String ownedByFromTags(Properties tags) {
        if (tags == null || tags.isEmpty()) {
            return UNKNOWN_OWNER;
        }
        String value = firstTag(tags, "author", "owner");
        return (value == null || value.isBlank()) ? UNKNOWN_OWNER : value.trim();
    }

    /**
     * Resolves {@code owned_by} from a string map (e.g. ONNX custom metadata),
     * preferring {@code author} then {@code owner}.
     *
     * @param metadata key/value metadata, or {@code null}.
     * @return the owner string.
     */
    public static String ownedByFromMap(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return UNKNOWN_OWNER;
        }
        for (String key : new String[] {"author", "owner"}) {
            for (var entry : metadata.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)
                        && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue().trim();
                }
            }
        }
        return UNKNOWN_OWNER;
    }

    /**
     * Returns the file's last-modified time as Unix seconds, or "now" on error.
     *
     * @param path the model file path.
     * @return Unix epoch seconds.
     */
    public static long createdFromPath(Path path) {
        try {
            FileTime time = Files.getLastModifiedTime(path);
            return time.toInstant().getEpochSecond();
        } catch (IOException | NullPointerException e) {
            return java.time.Instant.now().getEpochSecond();
        }
    }

    private static String firstTag(Properties tags, String... keys) {
        for (String key : keys) {
            for (String name : tags.stringPropertyNames()) {
                if (name.equalsIgnoreCase(key)) {
                    return tags.getProperty(name);
                }
            }
        }
        return null;
    }
}
