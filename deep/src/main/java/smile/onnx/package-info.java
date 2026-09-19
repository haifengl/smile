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
 * Java API for the ONNX Runtime inference engine.
 *
 * <p>This package provides an idiomatic Java interface for loading and running
 * ONNX models via the
 * <a href="https://onnxruntime.ai/">ONNX Runtime</a> native library. It is
 * built on top of the low-level Panama Foreign Function &amp; Memory (FFM)
 * bindings in {@code smile.onnx.foreign} — no JNI is used.
 *
 * <h2>Key classes</h2>
 * <dl>
 *   <dt>{@link smile.onnx.InferenceSession}</dt>
 *   <dd>Loads an ONNX model from a file or byte array and exposes a
 *       {@code run()} method for executing inference.</dd>
 *
 *   <dt>{@link smile.onnx.OrtValue}</dt>
 *   <dd>Wraps an {@code OrtValue} — the fundamental tensor / sequence / map
 *       container. Factory methods create tensors from Java primitive arrays;
 *       extraction methods copy data back.</dd>
 *
 *   <dt>{@link smile.onnx.SessionOptions}</dt>
 *   <dd>Fluent builder for session-level configuration: thread counts,
 *       graph optimisation level, execution providers, profiling, etc.</dd>
 *
 *   <dt>{@link smile.onnx.RunOptions}</dt>
 *   <dd>Per-inference-run options: log tag, severity level, cancellation.</dd>
 *
 *   <dt>{@link smile.onnx.Environment}</dt>
 *   <dd>Wraps an {@code OrtEnv} and owns shared thread pools. Use when
 *       multiple sessions should share resources.</dd>
 *
 *   <dt>{@link smile.onnx.ModelMetadata}</dt>
 *   <dd>Model producer name, graph name, version, and custom key-value
 *       metadata embedded in the model.</dd>
 *
 *   <dt>{@link smile.onnx.NodeInfo}</dt>
 *   <dd>Name, ONNX type, and (for tensors) shape/element-type of a model
 *       input or output.</dd>
 * </dl>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Load a model and run inference
 * try (var session = InferenceSession.create("yolo.onnx")) {
 *
 *     float[] pixels = ...; // pre-processed image data
 *     long[] shape   = { 1, 3, 640, 640 };
 *
 *     try (OrtValue input = OrtValue.fromFloatArray(pixels, shape)) {
 *         OrtValue[] outputs = session.run(Map.of("images", input));
 *         float[] boxes = outputs[0].toFloatArray();
 *         for (OrtValue v : outputs) v.close();
 *     }
 * }
 * }</pre>
 *
 * <h2>Dependencies</h2>
 * <p>The native {@code onnxruntime} shared library must be present on the
 * {@code java.library.path} (or the OS library search path). Pre-built
 * packages are available from the
 * <a href="https://github.com/microsoft/onnxruntime/releases">ORT releases</a>
 * page.
 *
 * <p>Java 22+ with the {@code --enable-native-access=ALL-UNNAMED} JVM flag
 * (or a named module declaration) is required for the Panama FFM layer.
 */
package smile.onnx;

