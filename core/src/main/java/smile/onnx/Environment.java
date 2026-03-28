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
 * A wrapper around the ONNX Runtime {@code OrtEnv} object. The environment
 * manages thread pools and logging configuration that are shared across all
 * sessions created from the same environment.
 *
 * <p>For most use cases the convenience factory methods on
 * {@link InferenceSession} (which create their own private environment) are
 * sufficient. Use this class when you want to share one environment — and
 * therefore its thread pools — across several sessions.
 *
 * <pre>{@code
 * try (var env = new Environment(LoggingLevel.WARNING, "my-app")) {
 *     try (var opts = new SessionOptions()) {
 *         var session = env.createSession("model.onnx", opts);
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @author Haifeng Li
 */
public class Environment implements AutoCloseable {
    /** The ORT API pointer. */
    private final MemorySegment api;
    /** The native OrtEnv pointer. */
    final MemorySegment handle;

    /**
     * Creates an {@code Environment} with {@link LoggingLevel#WARNING} and
     * the log id {@code "smile-onnx"}.
     */
    public Environment() {
        this(LoggingLevel.WARNING, "smile-onnx");
    }

    /**
     * Creates an {@code Environment} with the given logging level and log
     * identifier.
     *
     * @param loggingLevel minimum severity for log messages.
     * @param logId        short string identifying this environment in logs.
     */
    public Environment(LoggingLevel loggingLevel, String logId) {
        this.api = OrtRuntime.api();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment logIdSeg = arena.allocateFrom(logId);
            MemorySegment pEnv = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.CreateEnv.invoke(
                    OrtApi.CreateEnv(api), loggingLevel.value(), logIdSeg, pEnv);
            OrtRuntime.checkStatus(api, st);
            this.handle = pEnv.get(onnxruntime_c_api_h.C_POINTER, 0);
        }
    }

    /**
     * Returns the ONNX Runtime build information string (e.g. version and
     * commit hash).
     *
     * @return build info string.
     */
    public static String buildInfo() {
        MemorySegment api = OrtRuntime.api();
        MemorySegment ptr = OrtApi.GetBuildInfoString.invoke(OrtApi.GetBuildInfoString(api));
        return OrtRuntime.readString(ptr);
    }

    /**
     * Returns the list of available execution provider names on this
     * machine/installation (e.g. {@code ["CPUExecutionProvider",
     * "CUDAExecutionProvider"]}).
     *
     * @return available provider names.
     */
    public static java.util.List<String> availableProviders() {
        MemorySegment api = OrtRuntime.api();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pProviders = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment pCount = arena.allocate(onnxruntime_c_api_h.C_INT);
            MemorySegment st = OrtApi.GetAvailableProviders.invoke(
                    OrtApi.GetAvailableProviders(api), pProviders, pCount);
            OrtRuntime.checkStatus(api, st);
            int count = pCount.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
            MemorySegment providers = pProviders.get(onnxruntime_c_api_h.C_POINTER, 0)
                    .reinterpret((long) count * onnxruntime_c_api_h.C_POINTER.byteSize());

            java.util.List<String> result = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                MemorySegment namePtr = providers.getAtIndex(onnxruntime_c_api_h.C_POINTER, i);
                result.add(OrtRuntime.readString(namePtr));
            }
            // Release the providers array
            OrtApi.ReleaseAvailableProviders.invoke(
                    OrtApi.ReleaseAvailableProviders(api), providers, count);
            return java.util.List.copyOf(result);
        }
    }

    /**
     * Updates the minimum logging level of this environment.
     *
     * @param level new minimum logging level.
     */
    public void setLoggingLevel(LoggingLevel level) {
        MemorySegment st = OrtApi.UpdateEnvWithCustomLogLevel.invoke(
                OrtApi.UpdateEnvWithCustomLogLevel(api), handle, level.value());
        OrtRuntime.checkStatus(api, st);
    }

    /**
     * Creates an {@code InferenceSession} that shares this environment.
     *
     * @param modelPath      path to the {@code .onnx} model file.
     * @param sessionOptions session configuration.
     * @return the loaded session.
     */
    public InferenceSession createSession(String modelPath, SessionOptions sessionOptions) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathSeg = arena.allocateFrom(modelPath);
            MemorySegment pSession = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.CreateSession.invoke(
                    OrtApi.CreateSession(api), handle, pathSeg,
                    sessionOptions.handle, pSession);
            OrtRuntime.checkStatus(api, st);
            MemorySegment sess = pSession.get(onnxruntime_c_api_h.C_POINTER, 0);
            // Session does NOT own the env — this Environment instance does.
            return InferenceSession.wrap(api, handle, sess);
        }
    }

    /**
     * Creates an {@code InferenceSession} from an in-memory model that shares
     * this environment.
     *
     * @param modelBytes     the serialised ONNX model bytes.
     * @param sessionOptions session configuration.
     * @return the loaded session.
     */
    public InferenceSession createSession(byte[] modelBytes, SessionOptions sessionOptions) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment modelSeg = arena.allocate(modelBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(modelBytes),
                    java.lang.foreign.ValueLayout.JAVA_BYTE, 0L,
                    modelSeg, java.lang.foreign.ValueLayout.JAVA_BYTE, 0L, modelBytes.length);
            MemorySegment pSession = arena.allocate(onnxruntime_c_api_h.C_POINTER);
            MemorySegment st = OrtApi.CreateSessionFromArray.invoke(
                    OrtApi.CreateSessionFromArray(api),
                    handle, modelSeg, modelBytes.length,
                    sessionOptions.handle, pSession);
            OrtRuntime.checkStatus(api, st);
            MemorySegment sess = pSession.get(onnxruntime_c_api_h.C_POINTER, 0);
            return InferenceSession.wrap(api, handle, sess);
        }
    }

    @Override
    public void close() {
        OrtApi.ReleaseEnv.invoke(OrtApi.ReleaseEnv(api), handle);
    }
}

