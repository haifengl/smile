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
 * An ONNX Runtime GenAI model. Outlives tokenizers and generators created from it.
 *
 * @author Haifeng Li
 */
public final class Model implements AutoCloseable {
    /** Model directory leaf name (for display / LanguageModel.name). */
    private final String name;
    /** Native {@code OgaModel*} handle. */
    private MemorySegment handle;
    /** Execution provider used for this instance ({@code cuda}, {@code cpu}, or {@code default}). */
    private final String provider;

    private Model(String name, MemorySegment handle, String provider) {
        this.name = name;
        this.handle = handle;
        this.provider = provider;
    }

    /**
     * Loads a model from a GenAI model directory using providers from
     * {@code genai_config.json}.
     *
     * @param modelDir path containing {@code genai_config.json} and weights.
     * @return a new model owned by the caller.
     */
    public static Model of(String modelDir) {
        if (modelDir == null || modelDir.isBlank()) {
            throw new IllegalArgumentException("modelDir must not be blank");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateModel(arena.allocateFrom(modelDir), out));
            return new Model(leafName(modelDir), handle, "default");
        }
    }

    /**
     * Loads a model from a GenAI model directory using providers from
     * {@code genai_config.json}.
     *
     * @param modelDir path containing {@code genai_config.json} and weights.
     * @return a new model owned by the caller.
     */
    public static Model of(Path modelDir) {
        if (modelDir == null) {
            throw new IllegalArgumentException("modelDir must not be null");
        }
        return of(modelDir.toString());
    }

    /**
     * Opens a model, preferring CUDA when available and falling back to the
     * default/CPU providers on failure.
     *
     * <p>Override with environment variable {@code SMILE_ONNX_GENAI_PROVIDER}:
     * {@code auto} (default), {@code cuda} (require GPU), or {@code cpu}.
     *
     * @param modelDir GenAI model directory.
     * @return a new model owned by the caller.
     */
    public static Model open(Path modelDir) {
        if (modelDir == null) {
            throw new IllegalArgumentException("modelDir must not be null");
        }
        return open(modelDir.toString());
    }

    /**
     * Opens a model, preferring CUDA when available and falling back to the
     * default/CPU providers on failure.
     *
     * @param modelDir GenAI model directory.
     * @return a new model owned by the caller.
     * @see #open(Path)
     */
    public static Model open(String modelDir) {
        if (modelDir == null || modelDir.isBlank()) {
            throw new IllegalArgumentException("modelDir must not be blank");
        }
        String preference = GenAI.providerPreference();
        boolean tryCuda = switch (preference) {
            case "cpu" -> false;
            case "cuda" -> true;
            // auto: try CUDA when companion natives exist (or a prior attempt succeeded).
            default -> {
                Boolean cached = GenAI.cudaProviderUsable();
                if (cached != null) {
                    yield cached;
                }
                yield GenAI.cudaNativesPresent();
            }
        };

        if (tryCuda) {
            Config config = Config.of(modelDir);
            try {
                config.clearProviders().appendProvider("cuda");
                Model model = of(config, leafName(modelDir), "cuda");
                GenAI.noteCudaProviderUsable(true);
                return model;
            } catch (RuntimeException e) {
                GenAI.noteCudaProviderUsable(false);
                if ("cuda".equals(preference)) {
                    throw new GenAIException(
                            "SMILE_ONNX_GENAI_PROVIDER=cuda but CUDA EP failed to load", e);
                }
            } finally {
                config.close();
            }
        }
        return of(modelDir);
    }

    /**
     * Loads a model from an existing {@link Config}.
     *
     * @param config config; may be closed after this returns (settings are copied).
     * @return a new model owned by the caller.
     */
    public static Model of(Config config) {
        return of(config, "genai-model", "config");
    }

    private static Model of(Config config, String name, String provider) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        GenAI.init();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = GenAIRuntime.createHandle(arena,
                    out -> ort_genai_c_h.OgaCreateModelFromConfig(config.handle(), out));
            return new Model(name, handle, provider);
        }
    }

    /**
     * Creates a tokenizer bound to this model.
     *
     * @return a new tokenizer owned by the caller.
     */
    public Tokenizer createTokenizer() {
        return Tokenizer.of(this);
    }

    /**
     * Creates generator parameters for this model.
     *
     * @return a new params object owned by the caller.
     */
    public GeneratorParams createGeneratorParams() {
        return GeneratorParams.of(this);
    }

    /**
     * Returns the model directory leaf name (or a placeholder when loaded from Config).
     *
     * @return display name.
     */
    public String name() {
        return name;
    }

    /**
     * Returns which execution-provider path was selected for this instance.
     *
     * @return {@code cuda}, {@code default}, or {@code config}.
     */
    public String provider() {
        return provider;
    }

    /** @return the native handle (package use). */
    MemorySegment handle() {
        GenAIRuntime.requireOpen(handle, "Model");
        return handle;
    }

    private static String leafName(String modelDir) {
        Path path = Path.of(modelDir);
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : modelDir;
    }

    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            ort_genai_c_h.OgaDestroyModel(handle);
            handle = MemorySegment.NULL;
        }
    }
}
