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
package smile.onnx;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.onnx.foreign.OrtApi;
import smile.onnx.foreign.onnxruntime_c_api_h;

/**
 * Configuration options for an {@link InferenceSession}. Use the fluent
 * builder methods to configure the session before passing this object to
 * {@link InferenceSession#create}.
 *
 * <p>Instances of this class hold a native OrtSessionOptions pointer and
 * must be closed after use (or used within a try-with-resources block).
 *
 * <pre>{@code
 * try (var opts = new SessionOptions()) {
 *     opts.setIntraOpNumThreads(4)
 *         .setGraphOptimizationLevel(GraphOptimizationLevel.ENABLE_ALL);
 *     try (var session = InferenceSession.create("model.onnx", opts)) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @author Haifeng Li
 */
public class SessionOptions implements AutoCloseable {
    /** The ORT API struct pointer. */
    private final MemorySegment api;
    /** Arena for native memory allocations. */
    private final Arena arena;
    /** The native OrtSessionOptions pointer. */
    final MemorySegment handle;

    /**
     * Creates a new {@code SessionOptions} with default settings.
     */
    public SessionOptions() {
        this.api = OrtRuntime.api();
        this.arena = Arena.ofConfined();
        MemorySegment pOpts = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        MemorySegment status = OrtApi.CreateSessionOptions.invoke(
                OrtApi.CreateSessionOptions(api), pOpts);
        OrtRuntime.checkStatus(api, status);
        this.handle = pOpts.get(onnxruntime_c_api_h.C_POINTER, 0);
    }

    /**
     * Sets the number of threads used to parallelize execution within a
     * single operator (intra-op parallelism).
     * <p>The default value {@code 0} lets ORT pick an appropriate number.
     *
     * @param numThreads the number of intra-op threads (0 = auto).
     * @return this options object for chaining.
     */
    public SessionOptions setIntraOpNumThreads(int numThreads) {
        MemorySegment status = OrtApi.SetIntraOpNumThreads.invoke(
                OrtApi.SetIntraOpNumThreads(api), handle, numThreads);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Sets the number of threads used to parallelize execution across
     * independent operators (inter-op parallelism).
     * <p>The default value {@code 0} lets ORT pick an appropriate number.
     *
     * @param numThreads the number of inter-op threads (0 = auto).
     * @return this options object for chaining.
     */
    public SessionOptions setInterOpNumThreads(int numThreads) {
        MemorySegment status = OrtApi.SetInterOpNumThreads.invoke(
                OrtApi.SetInterOpNumThreads(api), handle, numThreads);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Sets the graph optimization level.
     *
     * @param level the optimization level.
     * @return this options object for chaining.
     */
    public SessionOptions setGraphOptimizationLevel(GraphOptimizationLevel level) {
        MemorySegment status = OrtApi.SetSessionGraphOptimizationLevel.invoke(
                OrtApi.SetSessionGraphOptimizationLevel(api), handle, level.value());
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Sets the execution mode.
     *
     * @param mode the execution mode.
     * @return this options object for chaining.
     */
    public SessionOptions setExecutionMode(ExecutionMode mode) {
        MemorySegment status = OrtApi.SetSessionExecutionMode.invoke(
                OrtApi.SetSessionExecutionMode(api), handle, mode.value());
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Sets the log identifier for this session's messages.
     *
     * @param logId the log identifier string.
     * @return this options object for chaining.
     */
    public SessionOptions setLogId(String logId) {
        MemorySegment logIdSeg = arena.allocateFrom(logId);
        MemorySegment status = OrtApi.SetSessionLogId.invoke(
                OrtApi.SetSessionLogId(api), handle, logIdSeg);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Sets the verbosity level of session log messages.
     *
     * @param level the logging verbosity level (higher = more verbose).
     * @return this options object for chaining.
     */
    public SessionOptions setLogVerbosityLevel(int level) {
        MemorySegment status = OrtApi.SetSessionLogVerbosityLevel.invoke(
                OrtApi.SetSessionLogVerbosityLevel(api), handle, level);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Sets the minimum severity of log messages emitted by this session.
     *
     * @param level the minimum logging level.
     * @return this options object for chaining.
     */
    public SessionOptions setLogSeverityLevel(LoggingLevel level) {
        MemorySegment status = OrtApi.SetSessionLogSeverityLevel.invoke(
                OrtApi.SetSessionLogSeverityLevel(api), handle, level.value());
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Writes the optimized model to the given file path after session
     * initialization.
     *
     * @param optimizedModelFilePath the output path for the optimized model.
     * @return this options object for chaining.
     */
    public SessionOptions setOptimizedModelFilePath(String optimizedModelFilePath) {
        MemorySegment pathSeg = arena.allocateFrom(optimizedModelFilePath);
        MemorySegment status = OrtApi.SetOptimizedModelFilePath.invoke(
                OrtApi.SetOptimizedModelFilePath(api), handle, pathSeg);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Enables CPU memory arena allocation.
     *
     * @return this options object for chaining.
     */
    public SessionOptions enableCpuMemArena() {
        MemorySegment status = OrtApi.EnableCpuMemArena.invoke(
                OrtApi.EnableCpuMemArena(api), handle);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Disables CPU memory arena allocation.
     *
     * @return this options object for chaining.
     */
    public SessionOptions disableCpuMemArena() {
        MemorySegment status = OrtApi.DisableCpuMemArena.invoke(
                OrtApi.DisableCpuMemArena(api), handle);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Enables memory pattern optimization.
     *
     * @return this options object for chaining.
     */
    public SessionOptions enableMemPattern() {
        MemorySegment status = OrtApi.EnableMemPattern.invoke(
                OrtApi.EnableMemPattern(api), handle);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Disables memory pattern optimization.
     *
     * @return this options object for chaining.
     */
    public SessionOptions disableMemPattern() {
        MemorySegment status = OrtApi.DisableMemPattern.invoke(
                OrtApi.DisableMemPattern(api), handle);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Enables profiling. Profile data is written to the given file prefix.
     *
     * @param profileFilePrefix the file path prefix for profiling output.
     * @return this options object for chaining.
     */
    public SessionOptions enableProfiling(String profileFilePrefix) {
        MemorySegment pathSeg = arena.allocateFrom(profileFilePrefix);
        MemorySegment status = OrtApi.EnableProfiling.invoke(
                OrtApi.EnableProfiling(api), handle, pathSeg);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Disables profiling.
     *
     * @return this options object for chaining.
     */
    public SessionOptions disableProfiling() {
        MemorySegment status = OrtApi.DisableProfiling.invoke(
                OrtApi.DisableProfiling(api), handle);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Appends the CUDA execution provider (GPU device {@code deviceId}).
     *
     * @param deviceId the CUDA device index.
     * @return this options object for chaining.
     */
    public SessionOptions appendCudaExecutionProvider(int deviceId) {
        MemorySegment status = onnxruntime_c_api_h.OrtSessionOptionsAppendExecutionProvider_CUDA(
                handle, deviceId);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Appends the TensorRT execution provider (GPU device {@code deviceId}).
     *
     * @param deviceId the CUDA/TRT device index.
     * @return this options object for chaining.
     */
    public SessionOptions appendTensorRTExecutionProvider(int deviceId) {
        MemorySegment status = onnxruntime_c_api_h.OrtSessionOptionsAppendExecutionProvider_Tensorrt(
                handle, deviceId);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Appends the ROCM execution provider (AMD GPU device {@code deviceId}).
     *
     * @param deviceId the ROCM device index.
     * @return this options object for chaining.
     */
    public SessionOptions appendRocmExecutionProvider(int deviceId) {
        MemorySegment status = onnxruntime_c_api_h.OrtSessionOptionsAppendExecutionProvider_ROCM(
                handle, deviceId);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Appends the DirectML execution provider (Windows GPU, device {@code deviceId}).
     *
     * @param deviceId the DirectML device index.
     * @return this options object for chaining.
     */
    public SessionOptions appendDirectMLExecutionProvider(int deviceId) {
        MemorySegment providerName = arena.allocateFrom("DML");
        MemorySegment status = OrtApi.SessionOptionsAppendExecutionProvider.invoke(
                OrtApi.SessionOptionsAppendExecutionProvider(api), handle, providerName,
                MemorySegment.NULL, MemorySegment.NULL, 0L);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    /**
     * Adds a session configuration entry as a key-value pair.
     *
     * @param key   the configuration key.
     * @param value the configuration value.
     * @return this options object for chaining.
     */
    public SessionOptions addConfigEntry(String key, String value) {
        MemorySegment keySeg = arena.allocateFrom(key);
        MemorySegment valSeg = arena.allocateFrom(value);
        MemorySegment status = OrtApi.AddSessionConfigEntry.invoke(
                OrtApi.AddSessionConfigEntry(api), handle, keySeg, valSeg);
        OrtRuntime.checkStatus(api, status);
        return this;
    }

    @Override
    public void close() {
        OrtApi.ReleaseSessionOptions.invoke(OrtApi.ReleaseSessionOptions(api), handle);
        arena.close();
    }
}

