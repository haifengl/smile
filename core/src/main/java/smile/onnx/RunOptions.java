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
 * Per-run configuration options passed to
 * {@code InferenceSession.run(Map, String[], RunOptions)}.
 *
 * <p>Instances must be closed after the run completes.
 *
 * <pre>{@code
 * try (var runOpts = new RunOptions()) {
 *     runOpts.setLogTag("my-run").setLogSeverityLevel(LoggingLevel.WARNING);
 *     OrtValue[] outputs = session.run(inputMap, outputNames, runOpts);
 * }
 * }</pre>
 *
 * @author Haifeng Li
 */
public class RunOptions implements AutoCloseable {
    /** The ORT API pointer. */
    private final MemorySegment api;
    /** Arena for temporary string allocations. */
    private final Arena arena;
    /** Native OrtRunOptions pointer. */
    final MemorySegment handle;

    /**
     * Creates a new {@code RunOptions} with default settings.
     */
    public RunOptions() {
        this.api = OrtRuntime.api();
        this.arena = Arena.ofConfined();
        MemorySegment pOpts = arena.allocate(onnxruntime_c_api_h.C_POINTER);
        MemorySegment st = OrtApi.CreateRunOptions.invoke(OrtApi.CreateRunOptions(api), pOpts);
        OrtRuntime.checkStatus(api, st);
        this.handle = pOpts.get(onnxruntime_c_api_h.C_POINTER, 0);
    }

    /**
     * Sets the log tag for this run.
     *
     * @param tag a short string to identify this run in log messages.
     * @return this object for chaining.
     */
    public RunOptions setLogTag(String tag) {
        MemorySegment tagSeg = arena.allocateFrom(tag);
        MemorySegment st = OrtApi.RunOptionsSetRunTag.invoke(
                OrtApi.RunOptionsSetRunTag(api), handle, tagSeg);
        OrtRuntime.checkStatus(api, st);
        return this;
    }

    /**
     * Sets the log verbosity level for this run.
     *
     * @param level verbosity level (higher = more verbose).
     * @return this object for chaining.
     */
    public RunOptions setLogVerbosityLevel(int level) {
        MemorySegment st = OrtApi.RunOptionsSetRunLogVerbosityLevel.invoke(
                OrtApi.RunOptionsSetRunLogVerbosityLevel(api), handle, level);
        OrtRuntime.checkStatus(api, st);
        return this;
    }

    /**
     * Sets the minimum log severity for this run.
     *
     * @param level the minimum severity level.
     * @return this object for chaining.
     */
    public RunOptions setLogSeverityLevel(LoggingLevel level) {
        MemorySegment st = OrtApi.RunOptionsSetRunLogSeverityLevel.invoke(
                OrtApi.RunOptionsSetRunLogSeverityLevel(api), handle, level.value());
        OrtRuntime.checkStatus(api, st);
        return this;
    }

    /**
     * Requests termination of the current run.
     * This can be called from another thread to cancel a running inference.
     *
     * @return this object for chaining.
     */
    public RunOptions setTerminate() {
        MemorySegment st = OrtApi.RunOptionsSetTerminate.invoke(
                OrtApi.RunOptionsSetTerminate(api), handle);
        OrtRuntime.checkStatus(api, st);
        return this;
    }

    /**
     * Clears the termination flag set by {@link #setTerminate()}.
     *
     * @return this object for chaining.
     */
    public RunOptions unsetTerminate() {
        MemorySegment st = OrtApi.RunOptionsUnsetTerminate.invoke(
                OrtApi.RunOptionsUnsetTerminate(api), handle);
        OrtRuntime.checkStatus(api, st);
        return this;
    }

    /**
     * Adds a run-level configuration entry.
     *
     * @param key   the configuration key.
     * @param value the configuration value.
     * @return this object for chaining.
     */
    public RunOptions addConfigEntry(String key, String value) {
        MemorySegment keySeg = arena.allocateFrom(key);
        MemorySegment valSeg = arena.allocateFrom(value);
        MemorySegment st = OrtApi.AddRunConfigEntry.invoke(
                OrtApi.AddRunConfigEntry(api), handle, keySeg, valSeg);
        OrtRuntime.checkStatus(api, st);
        return this;
    }

    @Override
    public void close() {
        OrtApi.ReleaseRunOptions.invoke(OrtApi.ReleaseRunOptions(api), handle);
        arena.close();
    }
}

