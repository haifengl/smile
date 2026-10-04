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
import smile.onnx.NativeLibrary;
import smile.onnx.genai.foreign.ort_genai_c_h;

/**
 * Process-wide ONNX Runtime GenAI controls: library availability, telemetry,
 * logging helpers, and shutdown.
 *
 * <p>Both {@code onnxruntime} and {@code onnxruntime-genai} shared libraries
 * must be on the OS library search path. The JVM also needs
 * {@code --enable-native-access=ALL-UNNAMED}.
 *
 * @author Haifeng Li
 * @see <a href="https://onnxruntime.ai/docs/genai/">ONNX Runtime GenAI</a>
 */
public final class GenAI {
    /**
     * Directory containing {@code onnxruntime} (system property
     * {@code onnxruntime.native.path}).
     */
    public static final String ORT_NATIVE_PATH_PROPERTY = NativeLibrary.ORT_NATIVE_PATH_PROPERTY;
    /**
     * Directory containing {@code onnxruntime-genai} (system property
     * {@code onnxruntime-genai.native.path}).
     */
    public static final String GENAI_NATIVE_PATH_PROPERTY = NativeLibrary.GENAI_NATIVE_PATH_PROPERTY;
    /**
     * Execution-provider preference: {@code auto} (default), {@code cuda},
     * {@code npu}, {@code ryzenai}/{@code hybrid}, {@code openvino}, {@code qnn},
     * {@code dml}/{@code directml}, or {@code cpu}.
     * Environment variable {@code SMILE_ONNX_GENAI_PROVIDER} overrides the system
     * property {@code smile.onnx.genai.provider}.
     *
     * <p>Classical Vitis AI for general ONNX is configured via
     * {@link smile.onnx.SessionOptions#appendVitisAiExecutionProvider()}, not this
     * GenAI preference.
     */
    public static final String PROVIDER_PROPERTY = "smile.onnx.genai.provider";
    /** Environment variable for {@link #PROVIDER_PROPERTY}. */
    public static final String PROVIDER_ENV = "SMILE_ONNX_GENAI_PROVIDER";

    /** Cached availability probe. */
    private static volatile Boolean available;
    /** Whether absolute-path preload has been attempted. */
    private static boolean preloaded;
    /** Resolved ORT native directory (after preload), or {@code null}. */
    private static String ortNativeDir;
    /** Resolved GenAI native directory (after preload), or {@code null}. */
    private static String genaiNativeDir;

    /** Not instantiable. */
    private GenAI() {}

    /**
     * Returns the preferred GenAI execution-provider mode.
     *
     * @return normalized preference: {@code auto}, {@code cuda}, {@code npu},
     *         {@code ryzenai}, {@code openvino}, {@code qnn}, {@code dml},
     *         or {@code cpu}.
     */
    public static String providerPreference() {
        String fromEnv = System.getenv(PROVIDER_ENV);
        String raw = (fromEnv != null && !fromEnv.isBlank())
                ? fromEnv
                : System.getProperty(PROVIDER_PROPERTY, "auto");
        String pref = raw == null ? "auto" : raw.trim().toLowerCase();
        return switch (pref) {
            case "cuda", "gpu" -> "cuda";
            case "npu" -> "npu";
            case "ryzenai", "hybrid" -> "ryzenai";
            case "openvino" -> "openvino";
            case "qnn" -> "qnn";
            case "dml", "directml" -> "dml";
            case "cpu" -> "cpu";
            default -> "auto";
        };
    }

    /**
     * Returns whether a previous {@link Model#open} successfully used the CUDA EP,
     * or {@code null} if CUDA has not been attempted yet in this process.
     *
     * @return {@code true}/{@code false} after a CUDA attempt, else {@code null}.
     */
    public static Boolean cudaProviderUsable() {
        return GenAIProviders.usable("cuda");
    }

    /**
     * Returns whether CUDA-capable GenAI/ORT shared libraries were found on the
     * configured native library path (e.g. pip {@code onnxruntime-genai-cuda}).
     *
     * @return {@code true} when CUDA companion natives are present.
     */
    public static boolean cudaNativesPresent() {
        return GenAIProviders.CUDA.nativesPresent();
    }

    /**
     * Returns whether candidate {@code id} is known to support FP8 Olive builds.
     *
     * <p>Olive {@code optimize} currently does not accept {@code fp8} in its
     * CLI precision enum; keep this {@code false} until that changes.
     *
     * @param candidateId GenAI cascade id (e.g. {@code cuda}).
     * @return {@code true} when auto precision should try {@code fp8} first.
     */
    public static boolean supportsFp8(String candidateId) {
        return false;
    }

    /**
     * Resolves Olive {@code optimize} {@code --device} / {@code --provider} /
     * default precision from {@link #providerPreference()} and EP natives
     * (same order as {@link Model#open}).
     *
     * @return Olive target; {@code cpu}/{@code int4} when no accelerator is present.
     */
    public static GenAIOliveTarget resolveOliveTarget() {
        String preference = providerPreference();
        if ("cpu".equals(preference)) {
            return cpuOliveTarget();
        }
        for (GenAIProviderCandidate c : GenAIProviders.candidatesFor(preference)) {
            if (c.nativesPresent()) {
                return toOliveTarget(c.id());
            }
        }
        return cpuOliveTarget();
    }

    static GenAIOliveTarget toOliveTarget(String candidateId) {
        String id = candidateId == null ? "cpu" : candidateId.toLowerCase();
        String precision = supportsFp8(id) ? "fp8" : "int4";
        return switch (id) {
            case "cuda" -> new GenAIOliveTarget("cuda", "gpu", "CUDAExecutionProvider", precision);
            case "ryzenai" -> new GenAIOliveTarget("ryzenai", "npu", "DmlExecutionProvider", precision);
            case "openvino" -> new GenAIOliveTarget(
                    "openvino", "npu", "OpenVINOExecutionProvider", precision);
            case "qnn" -> new GenAIOliveTarget("qnn", "npu", "QNNExecutionProvider", precision);
            case "dml" -> new GenAIOliveTarget("dml", "gpu", "DmlExecutionProvider", precision);
            default -> cpuOliveTarget();
        };
    }

    private static GenAIOliveTarget cpuOliveTarget() {
        return new GenAIOliveTarget("cpu", "cpu", "CPUExecutionProvider", "int4");
    }

    /** @return resolved ORT native directory after preload, or {@code null}. */
    static String ortNativeDir() {
        preloadNatives();
        return ortNativeDir;
    }

    /** @return resolved GenAI native directory after preload, or {@code null}. */
    static String genaiNativeDir() {
        preloadNatives();
        return genaiNativeDir;
    }

    /**
     * Returns whether {@link System#mapLibraryName(String) mapped} {@code bareName}
     * exists under the ORT/GenAI native dirs or on the OS library path.
     *
     * @param bareName library stem (e.g. {@code onnxruntime_providers_cuda}).
     * @return {@code true} if the file is present (not loaded).
     */
    static boolean libraryFilePresent(String bareName) {
        return findLibraryFile(bareName) != null;
    }

    /**
     * Absolute path to a mapped library file if found.
     *
     * @param bareName library stem (e.g. {@code QnnHtp}).
     * @return absolute path, or {@code null}.
     */
    static String findLibraryFile(String bareName) {
        return NativeLibrary.findLibraryFile(bareName);
    }
    /**
     * Returns whether the {@code onnxruntime-genai} native library can be loaded.
     *
     * @return {@code true} when the FFM bindings initialize successfully.
     */
    public static boolean available() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        synchronized (GenAI.class) {
            if (available != null) {
                return available;
            }
            try {
                preloadNatives();
                GenAIRuntime.ensureLoaded();
                available = true;
            } catch (Throwable t) {
                available = false;
            }
            return available;
        }
    }

    /**
     * Forces native library load. Prefer calling this early so missing natives
     * fail fast with a clear exception.
     *
     * <p>On Windows, if an older {@code onnxruntime.dll} lives in
     * {@code System32}, set {@value #ORT_NATIVE_PATH_PROPERTY} and
     * {@value #GENAI_NATIVE_PATH_PROPERTY} to the pip package directories so the
     * correct DLLs are {@link System#load(String) System.load}ed first.
     *
     * @throws GenAIException if the native library cannot be loaded.
     */
    public static void init() {
        try {
            preloadNatives();
            GenAIRuntime.ensureLoaded();
            available = true;
        } catch (GenAIException e) {
            available = false;
            throw e;
        } catch (Throwable t) {
            available = false;
            throw new GenAIException(
                    "Failed to load onnxruntime-genai. Install onnxruntime and "
                            + "onnxruntime-genai and put "
                            + System.mapLibraryName("onnxruntime-genai")
                            + " on the OS library search path (or set "
                            + ORT_NATIVE_PATH_PROPERTY + " / "
                            + GENAI_NATIVE_PATH_PROPERTY + "). "
                            + "JVM also needs --enable-native-access=ALL-UNNAMED.",
                    t);
        }
    }

    /**
     * Optionally {@link System#load(String) System.load}s absolute native
     * library paths before FFM lookup. Safe to call multiple times.
     *
     * <p>Resolution order for each library directory:
     * <ol>
     *   <li>system property ({@value #ORT_NATIVE_PATH_PROPERTY} /
     *       {@value #GENAI_NATIVE_PATH_PROPERTY})</li>
     *   <li>environment variable ({@code ONNXRUNTIME_NATIVE_PATH} /
     *       {@code ONNXRUNTIME_GENAI_NATIVE_PATH})</li>
     *   <li>directories on {@code PATH} / {@code LD_LIBRARY_PATH} /
     *       {@code DYLD_LIBRARY_PATH} that contain the shared library</li>
     * </ol>
     *
     * <p>Absolute {@code System.load} is required on Windows when an older
     * {@code onnxruntime.dll} in {@code System32} would otherwise win the
     * DLL search order over a pip-installed copy on {@code PATH}.
     */
    private static void preloadNatives() {
        if (preloaded) {
            return;
        }
        preloaded = true;
        // Resolve the directories, then load ORT before GenAI so GenAI's own
        // dependency resolves to the same copy. Do not System.load CUDA/NPU EP
        // DLLs here — presence checks gate Model.open.
        ortNativeDir = NativeLibrary.resolveDir(
                ORT_NATIVE_PATH_PROPERTY, NativeLibrary.ORT_NATIVE_PATH_ENV,
                NativeLibrary.ORT_LIBRARY);
        genaiNativeDir = NativeLibrary.resolveDir(
                GENAI_NATIVE_PATH_PROPERTY, NativeLibrary.GENAI_NATIVE_PATH_ENV,
                NativeLibrary.GENAI_LIBRARY);
        NativeLibrary.ensureLoaded(
                ORT_NATIVE_PATH_PROPERTY, NativeLibrary.ORT_NATIVE_PATH_ENV,
                NativeLibrary.ORT_LIBRARY);
        NativeLibrary.ensureLoaded(
                GENAI_NATIVE_PATH_PROPERTY, NativeLibrary.GENAI_NATIVE_PATH_ENV,
                NativeLibrary.GENAI_LIBRARY);
    }

    /**
     * Enables or disables non-essential GenAI telemetry events.
     *
     * @param enabled {@code true} to enable telemetry.
     */
    public static void setTelemetry(boolean enabled) {
        init();
        ort_genai_c_h.OgaSetTelemetryEnabled(enabled);
    }

    /**
     * Sets a boolean logging option (e.g. {@code "enabled"}, {@code "append"}).
     *
     * @param name  option name.
     * @param value option value.
     */
    public static void setLogBool(String name, boolean value) {
        init();
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(
                    ort_genai_c_h.OgaSetLogBool(arena.allocateFrom(name), value));
        }
    }

    /**
     * Sets a string logging option (e.g. {@code "filename"}, {@code "level"}).
     *
     * @param name  option name.
     * @param value option value.
     */
    public static void setLogString(String name, String value) {
        init();
        try (Arena arena = Arena.ofConfined()) {
            GenAIRuntime.checkResult(ort_genai_c_h.OgaSetLogString(
                    arena.allocateFrom(name),
                    arena.allocateFrom(value)));
        }
    }

    /**
     * Returns whether the GenAI library has been initialized in this process.
     *
     * @return {@code true} if initialized, {@code false} otherwise.
     */
    public static boolean isInitialized() {
        return Boolean.TRUE.equals(available);
    }

    /**
     * Tears down GenAI globals. Destroy every GenAI object before calling this.
     * Safe to call multiple times or when GenAI was never initialized.
     *
     * <p>After shutdown, the next GenAI API call re-initializes the library.
     */
    public static synchronized void shutdown() {
        if (!Boolean.TRUE.equals(available)) {
            return;
        }
        try {
            ort_genai_c_h.OgaShutdown.makeInvoker().apply();
        } finally {
            available = null;
        }
    }
}
