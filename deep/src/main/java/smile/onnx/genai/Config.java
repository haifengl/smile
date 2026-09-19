/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.onnx.genai;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import smile.onnx.genai.foreign.ort_genai_c_h;

/**
 * GenAI model configuration: execution providers and overlay JSON.
 *
 * <p>Providers are applied in insertion order. Typical usage:
 * <pre>{@code
 * try (var config = Config.of("models/phi-3")) {
 *     config.clearProviders()
 *           .appendProvider("cuda")
 *           .setProviderOption("cuda", "device_id", "0");
 *     try (var model = Model.of(config)) { ... }
 * }
 * }</pre>
 *
 * @author Haifeng Li
 */
public final class Config implements AutoCloseable {
    /** Confined arena for path / option strings owned by this config. */
    private final Arena arena;
    /** Native {@code OgaConfig*} handle. */
    private MemorySegment handle;

    private Config(Arena arena, MemorySegment handle) {
        this.arena = arena;
        this.handle = handle;
    }

    /**
     * Creates a config from a GenAI model directory.
     *
     * @param modelDir path containing {@code genai_config.json} and model files.
     * @return a new config owned by the caller.
     */
    public static Config of(String modelDir) {
        if (modelDir == null || modelDir.isBlank()) {
            throw new IllegalArgumentException("modelDir must not be blank");
        }
        GenAI.init();
        Arena arena = Arena.ofConfined();
        try {
            MemorySegment handle = GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateConfig(arena.allocateFrom(modelDir), out));
            return new Config(arena, handle);
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Creates a config from a GenAI model directory.
     *
     * @param modelDir path containing {@code genai_config.json} and model files.
     * @return a new config owned by the caller.
     */
    public static Config of(Path modelDir) {
        if (modelDir == null) {
            throw new IllegalArgumentException("modelDir must not be null");
        }
        return of(modelDir.toString());
    }

    /**
     * Clears the execution-provider list.
     *
     * @return {@code this} for chaining.
     */
    public Config clearProviders() {
        GenAIRuntime.requireOpen(handle, "Config");
        GenAIRuntime.checkResult(ort_genai_c_h.OgaConfigClearProviders(handle));
        return this;
    }

    /**
     * Appends an execution provider if it is not already present.
     *
     * @param providerName provider name (e.g. {@code "cpu"}, {@code "cuda"}).
     * @return {@code this} for chaining.
     */
    public Config appendProvider(String providerName) {
        GenAIRuntime.requireOpen(handle, "Config");
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName must not be blank");
        }
        GenAIRuntime.checkResult(ort_genai_c_h.OgaConfigAppendProvider(
                handle, arena.allocateFrom(providerName)));
        return this;
    }

    /**
     * Sets a provider option.
     *
     * @param providerName provider name.
     * @param key          option key.
     * @param value        option value.
     * @return {@code this} for chaining.
     */
    public Config setProviderOption(String providerName, String key, String value) {
        GenAIRuntime.requireOpen(handle, "Config");
        GenAIRuntime.checkResult(ort_genai_c_h.OgaConfigSetProviderOption(
                handle,
                arena.allocateFrom(providerName),
                arena.allocateFrom(key),
                arena.allocateFrom(value)));
        return this;
    }

    /**
     * Overlays a JSON fragment onto the loaded config.
     *
     * @param json JSON string to merge.
     * @return {@code this} for chaining.
     */
    public Config overlay(String json) {
        GenAIRuntime.requireOpen(handle, "Config");
        if (json == null) {
            throw new IllegalArgumentException("json must not be null");
        }
        GenAIRuntime.checkResult(ort_genai_c_h.OgaConfigOverlay(handle, arena.allocateFrom(json)));
        return this;
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "Config");
        return handle;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyConfig(handle);
            handle = MemorySegment.NULL;
        }
        arena.close();
    }
}
