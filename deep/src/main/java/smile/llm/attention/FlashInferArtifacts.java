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
package smile.llm.attention;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.torch.Native;

/**
 * Optional FlashInfer AOT / jit-cache directory: explicit dir → bundled default → env → cache dir.
 *
 * <p>Nothing loads these modules today: the attention kernels are compiled into {@code libsmile_torch}
 * from the vendored FlashInfer headers, and the GPU images no longer bundle the jit-cache. The directory is
 * only passed to the native library when someone provides one. Runtime download was removed.
 *
 * @author Haifeng Li
 */
public final class FlashInferArtifacts {
    private static final Logger logger = LoggerFactory.getLogger(FlashInferArtifacts.class);

    /** Default install path inside the GPU Docker image. */
    public static final String BUNDLED_AOT_DIR = "/opt/flashinfer/aot";

    private FlashInferArtifacts() {}

    /**
     * Resolves and installs the AOT directory into the native library.
     *
     * @param aotDir        explicit config path (may be blank).
     * @param cacheDir      download / extract cache (may be blank).
     * @param allowDownload ignored (deprecated); a warning is logged when true.
     * @param cudaTag       wheel CUDA tag such as {@code cu134}.
     * @return resolved directory, or empty when none available.
     */
    public static Optional<Path> resolveAndInstall(
            String aotDir, String cacheDir, boolean allowDownload, String cudaTag) {
        Optional<Path> resolved = resolve(aotDir, cacheDir, allowDownload, cudaTag);
        resolved.ifPresent(p -> {
            Native.flashInferSetAotDir(p.toAbsolutePath().toString());
            logger.info("FlashInfer AOT dir: {}", p.toAbsolutePath());
        });
        return resolved;
    }

    static Optional<Path> resolve(
            String aotDir, String cacheDir, boolean allowDownload, String cudaTag) {
        if (aotDir != null && !aotDir.isBlank()) {
            Path p = Path.of(aotDir);
            if (isUsableAot(p)) {
                return Optional.of(p);
            }
            logger.warn("smile.chat.flashinfer-aot-dir not usable: {}", p);
        }
        Path bundled = Path.of(BUNDLED_AOT_DIR);
        if (isUsableAot(bundled)) {
            return Optional.of(bundled);
        }
        String env = firstNonBlank(System.getenv("FLASHINFER_AOT_DIR"),
                System.getenv("SMILE_FLASHINFER_AOT_DIR"));
        if (env != null) {
            Path p = Path.of(env);
            if (isUsableAot(p)) {
                return Optional.of(p);
            }
        }
        if (cacheDir != null && !cacheDir.isBlank()) {
            Path cache = Path.of(cacheDir);
            Path extracted = cache.resolve("aot");
            if (isUsableAot(extracted)) {
                return Optional.of(extracted);
            }
            if (allowDownload) {
                logger.warn("smile.chat.flashinfer-download is ignored: runtime download of the "
                        + "flashinfer-jit-cache was removed (the kernels are compiled into libsmile_torch)");
            }
        }
        return Optional.empty();
    }

    static boolean isUsableAot(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> s = Files.walk(dir, 3)) {
            return s.anyMatch(p -> p.getFileName().toString().endsWith(".so"));
        } catch (IOException e) {
            return false;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
