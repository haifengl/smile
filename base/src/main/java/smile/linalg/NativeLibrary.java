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
package smile.linalg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import smile.util.OS;

/**
 * Resolver and loader for SMILE's jextract-generated linear algebra bindings,
 * mirroring {@code smile.onnx.NativeLibrary} for the ONNX Runtime.
 *
 * <p>The generated bindings obtain their symbols through
 * {@code SymbolLookup.libraryLookup(System.mapLibraryName(name))}, which calls
 * {@code dlopen} on a <em>bare</em> name. That lookup consults neither
 * {@code java.library.path} nor {@code LD_LIBRARY_PATH}, and on macOS the system
 * integrity protection (SIP) strips {@code DYLD_*} variables when the JVM is
 * started through the {@code /usr/bin/java} stub. A Homebrew
 * {@code libarpack.dylib} is therefore not found unless the working directory
 * happens to contain it — which used to force a copy or symlink.
 *
 * <p>An explicit absolute-path {@code System.load} does <em>not</em> go through
 * dyld's name search, so SIP cannot interfere with it. Loading the library first
 * binds the image into the process; the binding's
 * {@link java.lang.foreign.SymbolLookup#loaderLookup()} then resolves the
 * symbols from that image.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>a JVM system property (e.g. {@code -Darpack.native.path})</li>
 *   <li>an environment variable (e.g. {@code ARPACK_NATIVE_PATH})</li>
 *   <li>the well-known installation directories for the platform</li>
 *   <li>{@code PATH} / {@code LD_LIBRARY_PATH} / {@code DYLD_LIBRARY_PATH}</li>
 * </ol>
 *
 * <p>The search-path scan deliberately <em>skips</em> {@code System32} and
 * {@code SysWOW64}, so a stale Windows system copy is never treated as the
 * intended one.
 *
 * @author Haifeng Li
 */
public final class NativeLibrary {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NativeLibrary.class);

    /** System property to select linear algebra backend ("auto", "native", "java"). */
    public static final String BACKEND_PROPERTY = "smile.linalg.backend";
    /** Environment variable counterpart of {@link #BACKEND_PROPERTY}. */
    public static final String BACKEND_ENV = "SMILE_LINALG_BACKEND";

    /** System property naming the directory that contains BLAS. */
    public static final String BLAS_NATIVE_PATH_PROPERTY = "blas.native.path";
    /** Environment variable counterpart of {@link #BLAS_NATIVE_PATH_PROPERTY}. */
    public static final String BLAS_NATIVE_PATH_ENV = "BLAS_NATIVE_PATH";

    /** System property naming the directory that contains LAPACK. */
    public static final String LAPACK_NATIVE_PATH_PROPERTY = "lapack.native.path";
    /** Environment variable counterpart of {@link #LAPACK_NATIVE_PATH_PROPERTY}. */
    public static final String LAPACK_NATIVE_PATH_ENV = "LAPACK_NATIVE_PATH";

    /** System property naming the directory that contains ARPACK. */
    public static final String ARPACK_NATIVE_PATH_PROPERTY = "arpack.native.path";
    /** Environment variable counterpart of {@link #ARPACK_NATIVE_PATH_PROPERTY}. */
    public static final String ARPACK_NATIVE_PATH_ENV = "ARPACK_NATIVE_PATH";

    /** Bare (unmapped) default name of the BLAS library. */
    public static final String BLAS_LIBRARY = OS.isMacOS() ? "blas" : (OS.isWindows() ? "libopenblas" : "openblas");
    /** Bare (unmapped) default name of the LAPACK library. */
    public static final String LAPACK_LIBRARY = OS.isMacOS() ? "lapack" : (OS.isWindows() ? "libopenblas" : "openblas");
    /** Bare (unmapped) name of the ARPACK library. */
    public static final String ARPACK_LIBRARY = "arpack";

    /** System directories that must never satisfy the search-path scan. */
    private static final Set<String> SYSTEM_DIRS = Set.of("system32", "syswow64");

    /** Library stems already loaded by this class (avoids duplicate warnings). */
    private static final Set<String> LOADED = ConcurrentHashMap.newKeySet();

    /** Cached availability of native CBLAS. */
    private static volatile Boolean blasAvailable;
    /** Cached availability of native CLAPACK. */
    private static volatile Boolean lapackAvailable;

    /** Not instantiable. */
    private NativeLibrary() {}

    /**
     * Returns the configured linear algebra backend mode ("auto", "native", or "java").
     *
     * @return the backend mode in lowercase.
     */
    public static String backend() {
        String mode = System.getProperty(BACKEND_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv(BACKEND_ENV);
        }
        return (mode == null || mode.isBlank()) ? "auto" : mode.trim().toLowerCase();
    }

    /**
     * Returns whether native CBLAS is available and enabled.
     *
     * @return {@code true} if native CBLAS should be used; {@code false} to use pure Java.
     */
    public static boolean isBlasAvailable() {
        String mode = backend();
        if ("java".equals(mode)) {
            return false;
        }
        Boolean available = blasAvailable;
        if (available == null) {
            synchronized (NativeLibrary.class) {
                available = blasAvailable;
                if (available == null) {
                    available = probeBlas();
                    blasAvailable = available;
                    if (!available && "auto".equals(mode)) {
                        logger.info("Native BLAS library not found; falling back to pure-Java linear algebra backend.");
                    }
                }
            }
        }
        if (!available && "native".equals(mode)) {
            throw new UnsatisfiedLinkError("Native BLAS library is not available (smile.linalg.backend=native)");
        }
        return available;
    }

    /**
     * Returns whether native CLAPACK is available and enabled.
     *
     * @return {@code true} if native CLAPACK should be used; {@code false} to use pure Java.
     */
    public static boolean isLapackAvailable() {
        String mode = backend();
        if ("java".equals(mode)) {
            return false;
        }
        Boolean available = lapackAvailable;
        if (available == null) {
            synchronized (NativeLibrary.class) {
                available = lapackAvailable;
                if (available == null) {
                    available = probeLapack();
                    lapackAvailable = available;
                    if (!available && "auto".equals(mode)) {
                        logger.info("Native LAPACK library not found; falling back to pure-Java linear algebra backend.");
                    }
                }
            }
        }
        if (!available && "native".equals(mode)) {
            throw new UnsatisfiedLinkError("Native LAPACK library is not available (smile.linalg.backend=native)");
        }
        return available;
    }

    private static boolean probeBlas() {
        try {
            ensureBlasLoaded();
            return smile.linalg.blas.cblas_h.cblas_ddot$address() != null;
        } catch (Throwable t) {
            logger.debug("Native BLAS probe failed: {}", t.getMessage());
            return false;
        }
    }

    private static boolean probeLapack() {
        try {
            ensureLapackLoaded();
            return smile.linalg.lapack.clapack_h.dgetrf_$address() != null;
        } catch (Throwable t) {
            logger.debug("Native LAPACK probe failed: {}", t.getMessage());
            return false;
        }
    }

    /**
     * Returns the well-known installation directories for the platform, most
     * likely first. On macOS these are the two Homebrew prefixes; on Linux the
     * conventional multiarch and {@code /usr/lib} locations; on Windows the
     * release package's {@code bin} directory (relative to {@code smile.home}).
     *
     * @return the candidate directories (they may not all exist).
     */
    static List<Path> defaultDirectories() {
        if (OS.isMacOS()) {
            return List.of(Path.of("/opt/homebrew/lib"), Path.of("/usr/local/lib"));
        }
        if (OS.isWindows()) {
            String home = System.getProperty("smile.home");
            return (home == null || home.isBlank())
                    ? List.of()
                    : List.of(Path.of(home, "bin"));
        }
        return List.of(
                Path.of("/usr/lib/x86_64-linux-gnu"),
                Path.of("/usr/lib/aarch64-linux-gnu"),
                Path.of("/usr/local/lib"),
                Path.of("/usr/lib"));
    }

    /**
     * Resolves the directory holding a mapped native library.
     *
     * <p>An explicitly configured system property or environment variable wins
     * outright, even if the named library is not actually there: silently
     * falling back to a different copy found on the search path would be worse
     * than letting the load fail with an honest "not found". The well-known
     * directories and the search path are consulted only when neither is set.
     *
     * @param property the JVM system property name (may be {@code null}).
     * @param env      the environment variable name (may be {@code null}).
     * @param bareName the bare library stem, e.g. {@code arpack}.
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
        String mapped = System.mapLibraryName(bareName);
        for (Path dir : defaultDirectories()) {
            if (Files.isRegularFile(dir.resolve(mapped))) {
                return dir.toAbsolutePath().toString();
            }
        }
        return findOnLibraryPath(mapped);
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
     * @param bareName the bare library stem, e.g. {@code arpack}.
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
     * Loads the BLAS library by absolute path, if it can be resolved.
     *
     * @return {@code true} when the library is loaded after this call.
     */
    public static boolean ensureBlasLoaded() {
        String lib = System.getProperty("smile.lib.blas", BLAS_LIBRARY);
        return ensureLoaded(BLAS_NATIVE_PATH_PROPERTY, BLAS_NATIVE_PATH_ENV, lib);
    }

    /**
     * Loads the LAPACK library by absolute path, if it can be resolved.
     *
     * @return {@code true} when the library is loaded after this call.
     */
    public static boolean ensureLapackLoaded() {
        String lib = System.getProperty("smile.lib.lapack", LAPACK_LIBRARY);
        return ensureLoaded(LAPACK_NATIVE_PATH_PROPERTY, LAPACK_NATIVE_PATH_ENV, lib);
    }

    /**
     * Loads the ARPACK library by absolute path, if it can be resolved.
     *
     * @return {@code true} when the library is loaded after this call.
     */
    public static boolean ensureArpackLoaded() {
        return ensureLoaded(ARPACK_NATIVE_PATH_PROPERTY, ARPACK_NATIVE_PATH_ENV, ARPACK_LIBRARY);
    }

    /**
     * Finds a mapped library file under the well-known directories or on the OS
     * library search path.
     *
     * @param bareName the bare library stem, e.g. {@code arpack}.
     * @return the absolute path, or {@code null} when the file is absent.
     */
    public static String findLibraryFile(String bareName) {
        String mapped = System.mapLibraryName(bareName);
        for (Path dir : defaultDirectories()) {
            Path path = dir.resolve(mapped);
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
     * @param bareName the bare library stem, e.g. {@code arpack}.
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
     * first non-empty variable, because a platform may put the natives on only
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
