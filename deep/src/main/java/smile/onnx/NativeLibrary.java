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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.onnx;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared resolver and loader for the ONNX Runtime native libraries.
 *
 * <p>Both the classic <a href="https://onnxruntime.ai/">ONNX Runtime</a> bindings
 * ({@code smile.onnx}) and the GenAI bindings ({@code smile.onnx.genai}) need the
 * same {@code onnxruntime} shared library loaded before the jextract-generated
 * foreign bindings perform their lookup. This class is the single place that
 * resolves, guards, and loads it, so the two paths cannot drift apart.
 *
 * <h2>Why an explicit absolute-path load at all</h2>
 * <p>The generated bindings load by bare library name
 * ({@code SymbolLookup.libraryLookup(System.mapLibraryName("onnxruntime"))}),
 * which delegates to the operating system's usual search. On Windows an older
 * {@code onnxruntime.dll} shipped in {@code C:\Windows\System32} wins that
 * search over a pip-installed copy on {@code PATH} — the system directory
 * precedes user directories in the default DLL search order. Loading the
 * intended copy first, by <em>absolute path</em>, binds the process to it and
 * makes the later bare-name lookup resolve to the already-loaded copy. On most
 * Unix-like systems loading the library first is also required because its
 * dependencies are not on the loader's search path.
 *
 * <h2>Resolution order</h2>
 * <p>A library directory is resolved from, in order:
 * <ol>
 *   <li>a JVM system property (e.g. {@code -Donnxruntime.native.path})</li>
 *   <li>an environment variable (e.g. {@code ONNXRUNTIME_NATIVE_PATH})</li>
 *   <li>directories on {@code PATH} / {@code LD_LIBRARY_PATH} /
 *       {@code DYLD_LIBRARY_PATH} that contain the mapped library file</li>
 * </ol>
 *
 * <p>The search-path scan deliberately <em>skips</em> {@code System32} and
 * {@code SysWOW64}, so a stale Windows system copy is never treated as the
 * intended one.
 *
 * @author Haifeng Li
 */
public final class NativeLibrary {
    /**
     * System property naming the directory that contains {@code onnxruntime}
     * (environment variable {@code ONNXRUNTIME_NATIVE_PATH}).
     */
    public static final String ORT_NATIVE_PATH_PROPERTY = "onnxruntime.native.path";
    /**
     * System property naming the directory that contains {@code onnxruntime-genai}
     * (environment variable {@code ONNXRUNTIME_GENAI_NATIVE_PATH}).
     */
    public static final String GENAI_NATIVE_PATH_PROPERTY = "onnxruntime-genai.native.path";

    /** Environment variable counterpart of {@link #ORT_NATIVE_PATH_PROPERTY}. */
    public static final String ORT_NATIVE_PATH_ENV = "ONNXRUNTIME_NATIVE_PATH";
    /** Environment variable counterpart of {@link #GENAI_NATIVE_PATH_PROPERTY}. */
    public static final String GENAI_NATIVE_PATH_ENV = "ONNXRUNTIME_GENAI_NATIVE_PATH";

    /** Bare (unmapped) name of the ONNX Runtime library. */
    public static final String ORT_LIBRARY = "onnxruntime";
    /** Bare (unmapped) name of the ONNX Runtime GenAI library. */
    public static final String GENAI_LIBRARY = "onnxruntime-genai";

    /** System directories that must never satisfy the search-path scan. */
    private static final Set<String> SYSTEM_DIRS = Set.of("system32", "syswow64");

    /** Library stems already loaded by this class (avoids duplicate warnings). */
    private static final Set<String> LOADED = ConcurrentHashMap.newKeySet();

    /** Not instantiable. */
    private NativeLibrary() {}

    /**
     * Resolves the directory holding a mapped native library.
     *
     * <p>An explicitly configured system property or environment variable wins
     * outright, even if the named library is not actually there: silently
     * falling back to a different copy found on the search path would be worse
     * than letting the load fail with an honest "not found". The search path is
     * consulted only when neither is set.
     *
     * @param property the JVM system property name (may be {@code null}).
     * @param env      the environment variable name (may be {@code null}).
     * @param bareName the bare library stem, e.g. {@code onnxruntime}.
     * @return the absolute directory path, or {@code null} when not found.
     */
    public static String resolveDir(String property, String env, String bareName) {
        String fromProperty = (property == null) ? null : System.getProperty(property);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Path.of(fromProperty.trim()).toAbsolutePath().toString();
        }
        String fromEnv = (env == null) ? null : System.getenv(env);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Path.of(fromEnv.trim()).toAbsolutePath().toString();
        }
        return findOnLibraryPath(System.mapLibraryName(bareName));
    }

    /**
     * Loads a native library by absolute path, at most once per library stem.
     *
     * <p>Safe to call from several classes' static initializers: the first call
     * performs the load, later calls are no-ops. A failure to load is swallowed
     * — the caller's own native lookup will produce a clearer error than this
     * best-effort preload could.
     *
     * @param property the JVM system property name (may be {@code null}).
     * @param env      the environment variable name (may be {@code null}).
     * @param bareName the bare library stem, e.g. {@code onnxruntime}.
     * @return {@code true} when the library is loaded after this call.
     */
    public static boolean ensureLoaded(String property, String env, String bareName) {
        if (LOADED.contains(bareName)) {
            return true;
        }
        if (!LOADED.add(bareName)) {
            return true;
        }
        String dir = resolveDir(property, env, bareName);
        if (dir == null) {
            LOADED.remove(bareName);
            return false;
        }
        Path path = Path.of(dir, System.mapLibraryName(bareName));
        if (!Files.isRegularFile(path)) {
            LOADED.remove(bareName);
            return false;
        }
        try {
            System.load(path.toAbsolutePath().toString());
            return true;
        } catch (UnsatisfiedLinkError e) {
            // Best effort: leave it to the caller's own lookup to report a
            // clearer error than this preload could.
            LOADED.remove(bareName);
            return false;
        }
    }

    /**
     * Finds a mapped library file under the resolved native directories or on
     * the OS library search path.
     *
     * @param bareName the bare library stem, e.g. {@code QnnHtp}.
     * @return the absolute path, or {@code null} when the file is absent.
     */
    public static String findLibraryFile(String bareName) {
        String mapped = System.mapLibraryName(bareName);
        // Resolve the ORT/GenAI directories by their own library names, then
        // look for this (possibly companion) library inside them.
        String ortDir = resolveDir(ORT_NATIVE_PATH_PROPERTY, ORT_NATIVE_PATH_ENV, ORT_LIBRARY);
        String genaiDir = resolveDir(GENAI_NATIVE_PATH_PROPERTY, GENAI_NATIVE_PATH_ENV, GENAI_LIBRARY);
        for (String dir : new String[]{ortDir, genaiDir}) {
            if (dir == null) {
                continue;
            }
            Path path = Path.of(dir, mapped);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().toString();
            }
        }
        String onPath = findOnLibraryPath(mapped);
        if (onPath != null) {
            Path path = Path.of(onPath, mapped);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().toString();
            }
        }
        return null;
    }

    /**
     * Returns whether a mapped library file is present (loaded or not).
     *
     * @param bareName the bare library stem, e.g. {@code onnxruntime_providers_cuda}.
     * @return {@code true} when the file exists somewhere resolvable.
     */
    public static boolean libraryFilePresent(String bareName) {
        return findLibraryFile(bareName) != null;
    }

    /**
     * Scans the OS library search path for a mapped file, skipping the Windows
     * system directories.
     *
     * <p>All of {@code PATH}, {@code LD_LIBRARY_PATH}, and
     * {@code DYLD_LIBRARY_PATH} are examined — the search does not stop at the
     * first non-empty variable, because a platform may put pip natives on only
     * one of them while another is always set.
     *
     * @param fileName the mapped library file name.
     * @return the absolute directory containing the file, or {@code null}.
     */
    static String findOnLibraryPath(String fileName) {
        for (String pathEnv : new String[]{
                System.getenv("PATH"),
                System.getenv("LD_LIBRARY_PATH"),
                System.getenv("DYLD_LIBRARY_PATH")}) {
            if (pathEnv == null || pathEnv.isBlank()) {
                continue;
            }
            String sep = pathEnv.indexOf(';') >= 0 ? ";" : File.pathSeparator;
            for (String dir : pathEnv.split(java.util.regex.Pattern.quote(sep))) {
                if (dir == null || dir.isBlank()) {
                    continue;
                }
                Path directory = Path.of(dir.trim());
                String name = directory.getFileName() != null
                        ? directory.getFileName().toString()
                        : "";
                if (SYSTEM_DIRS.contains(name.toLowerCase())) {
                    continue;
                }
                if (Files.isRegularFile(directory.resolve(fileName))) {
                    return directory.toAbsolutePath().toString();
                }
            }
        }
        return null;
    }
}