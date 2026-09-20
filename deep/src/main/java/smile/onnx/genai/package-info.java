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

/**
 * Idiomatic Java API for <a href="https://onnxruntime.ai/docs/genai/">ONNX Runtime GenAI</a>.
 *
 * <p>This package wraps the GenAI C API through Panama FFM bindings in
 * {@code smile.onnx.genai.foreign}. Handles implement {@link AutoCloseable}
 * and throw unchecked {@link smile.onnx.genai.GenAIException} on native errors.
 *
 * <h2>Layers</h2>
 * <ul>
 *   <li><b>Core handles</b> — {@link smile.onnx.genai.Config},
 *       {@link smile.onnx.genai.Model}, {@link smile.onnx.genai.Tokenizer},
 *       {@link smile.onnx.genai.Generator}, …</li>
 *   <li><b>Convenience</b> — {@link smile.onnx.genai.SimpleGenAI} for
 *       prompt → stream → text.</li>
 *   <li><b>Serve seam</b> — {@link smile.onnx.genai.GenAiChatModel} implements
 *       {@link smile.llm.LanguageModel} (wire from ChatService in a follow-up).</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * try (var genai = SimpleGenAI.of("models/phi-3-mini-cpu")) {
 *     try (var params = genai.createGeneratorParams()) {
 *         params.setSearchOption("max_length", 256)
 *               .setSearchOption("temperature", 0.8);
 *         String reply = genai.generate(params, "Hello!", System.out::print);
 *     }
 * }
 * }</pre>
 *
 * <h2>Providers</h2>
 * <p>{@link smile.onnx.genai.Model#open(String)} cascades CUDA → RyzenAI →
 * OpenVINO NPU → QNN → DirectML (Windows) → CPU when
 * {@code SMILE_ONNX_GENAI_PROVIDER} is {@code auto} (default). Each accelerator
 * requires matching EP natives.
 * Classical Vitis AI for general ONNX is
 * {@link smile.onnx.SessionOptions#appendVitisAiExecutionProvider()}.
 *
 * <h2>Native libraries</h2>
 * <p>Both {@code onnxruntime} and {@code onnxruntime-genai} must be on the OS
 * library search path. Use {@code --enable-native-access=ALL-UNNAMED}.
 *
 * <p>{@code OgaEngine} continuous batching is not wrapped yet; map it to
 * {@code smile.llm.engine.ModelExecutor} in a later phase.
 */
package smile.onnx.genai;
