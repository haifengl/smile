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

import java.util.function.Consumer;

/**
 * Convenience façade for single-prompt text generation with an optional
 * streaming callback.
 *
 * <pre>{@code
 * try (var genai = SimpleGenAI.of("models/phi-3")) {
 *     try (var params = genai.createGeneratorParams()) {
 *         params.setSearchOption("max_length", 256);
 *         String text = genai.generate(params, "Hello!", System.out::print);
 *     }
 * }
 * }</pre>
 *
 * @author Haifeng Li
 */
public final class SimpleGenAI implements AutoCloseable {
    private Model model;
    private Tokenizer tokenizer;

    private SimpleGenAI(Model model, Tokenizer tokenizer) {
        this.model = model;
        this.tokenizer = tokenizer;
    }

    /**
     * Loads a GenAI model and its tokenizer from a model directory.
     *
     * @param modelDir GenAI model directory.
     * @return façade owned by the caller.
     */
    public static SimpleGenAI of(String modelDir) {
        Model model = Model.of(modelDir);
        try {
            return new SimpleGenAI(model, model.createTokenizer());
        } catch (RuntimeException e) {
            model.close();
            throw e;
        }
    }

    /**
     * Opens a GenAI model preferring CUDA when available (see {@link Model#open(String)}).
     *
     * @param modelDir GenAI model directory.
     * @return façade owned by the caller.
     */
    public static SimpleGenAI open(String modelDir) {
        Model model = Model.open(modelDir);
        try {
            return new SimpleGenAI(model, model.createTokenizer());
        } catch (RuntimeException e) {
            model.close();
            throw e;
        }
    }

    /**
     * Opens a GenAI model preferring CUDA when available.
     *
     * @param modelDir GenAI model directory.
     * @return façade owned by the caller.
     */
    public static SimpleGenAI open(java.nio.file.Path modelDir) {
        if (modelDir == null) {
            throw new IllegalArgumentException("modelDir must not be null");
        }
        return open(modelDir.toString());
    }

    /**
     * Loads a GenAI model from config and creates its tokenizer.
     *
     * @param config GenAI config.
     * @return façade owned by the caller.
     */
    public static SimpleGenAI of(Config config) {
        Model model = Model.of(config);
        try {
            return new SimpleGenAI(model, model.createTokenizer());
        } catch (RuntimeException e) {
            model.close();
            throw e;
        }
    }

    /**
     * Returns the underlying model.
     *
     * @return model.
     */
    public Model model() {
        return model;
    }

    /**
     * Returns the underlying tokenizer.
     *
     * @return tokenizer.
     */
    public Tokenizer tokenizer() {
        return tokenizer;
    }

    /**
     * Creates generator parameters for this model.
     *
     * @return params owned by the caller.
     */
    public GeneratorParams createGeneratorParams() {
        return model.createGeneratorParams();
    }

    /**
     * Generates text for a single prompt.
     *
     * @param params  search options (must outlive the call).
     * @param prompt  prompt text.
     * @param onChunk optional per-token text callback; may be {@code null}.
     * @return full decoded sequence (prompt + completion).
     */
    public String generate(GeneratorParams params, String prompt, Consumer<String> onChunk) {
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        try (Sequences sequences = tokenizer.encode(prompt);
             Generator generator = Generator.of(model, params)) {
            generator.appendSequences(sequences);
            if (onChunk != null) {
                try (TokenizerStream stream = tokenizer.createStream()) {
                    for (int tokenId : generator) {
                        String chunk = stream.decode(tokenId);
                        if (chunk != null && !chunk.isEmpty()) {
                            onChunk.accept(chunk);
                        }
                    }
                }
            } else {
                for (int ignored : generator) {
                    // drain tokens
                }
            }
            return tokenizer.decode(generator.getSequence(0));
        }
    }

    /**
     * Generates text without a streaming callback.
     *
     * @param params search options.
     * @param prompt prompt text.
     * @return full decoded sequence.
     */
    public String generate(GeneratorParams params, String prompt) {
        return generate(params, prompt, null);
    }

    @Override
    public void close() {
        if (tokenizer != null) {
            tokenizer.close();
            tokenizer = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
    }
}
