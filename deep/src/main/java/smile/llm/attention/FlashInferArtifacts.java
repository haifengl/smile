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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.torch.Native;

/**
 * Resolves FlashInfer AOT / jit-cache directory for hybrid packaging:
 * explicit dir → env / bundled default → cache-dir → optional download.
 *
 * <p>Download-only (no nvcc). Artifacts are {@code flashinfer-jit-cache} wheels,
 * not {@code flashinfer-cubin}.
 *
 * @author Haifeng Li
 */
public final class FlashInferArtifacts {
    private static final Logger logger = LoggerFactory.getLogger(FlashInferArtifacts.class);

    /** Default install path inside the GPU Docker image. */
    public static final String BUNDLED_AOT_DIR = "/opt/flashinfer/aot";

    /** Pinned jit-cache release used when {@code smile.chat.flashinfer-download=true}. */
    public static final String DEFAULT_JIT_CACHE_VERSION = "0.6.6";

    private FlashInferArtifacts() {}

    /**
     * Resolves and installs the AOT directory into the native library.
     *
     * @param aotDir        explicit config path (may be blank).
     * @param cacheDir      download / extract cache (may be blank).
     * @param allowDownload when true, fetch jit-cache into {@code cacheDir} if missing.
     * @param cudaTag       wheel CUDA tag such as {@code cu132} (SMILE default).
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
                try {
                    downloadAndExtract(cache, cudaTag == null || cudaTag.isBlank() ? "cu132" : cudaTag);
                    if (isUsableAot(extracted)) {
                        return Optional.of(extracted);
                    }
                } catch (Exception e) {
                    logger.warn("FlashInfer jit-cache download failed: {}", e.toString());
                }
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

    static void downloadAndExtract(Path cacheDir, String cudaTag) throws IOException, InterruptedException {
        Files.createDirectories(cacheDir);
        // Prefer the FlashInfer CUDA-specific wheel index (matches SMILE cu132 builds).
        String ver = DEFAULT_JIT_CACHE_VERSION;
        String tag = cudaTag;
        String wheelFile = "flashinfer_jit_cache-" + ver + "+" + tag
                + "-cp39-abi3-manylinux_2_28_x86_64.whl";
        URI uri = URI.create("https://flashinfer.ai/whl/" + tag + "/" + wheelFile);
        Path wheel = cacheDir.resolve(wheelFile);
        if (!Files.isRegularFile(wheel)) {
            logger.info("Downloading FlashInfer jit-cache from {}", uri);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
            HttpRequest req = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofMinutes(10)).build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                // Fallback: GitHub release asset naming (URL-encoded '+').
                URI gh = URI.create("https://github.com/flashinfer-ai/flashinfer/releases/download/v"
                        + ver + "/flashinfer_jit_cache-" + ver + "%2B" + tag
                        + "-cp39-abi3-manylinux_2_28_x86_64.whl");
                logger.info("Primary index failed (HTTP {}); trying {}", resp.statusCode(), gh);
                req = HttpRequest.newBuilder(gh).GET().timeout(Duration.ofMinutes(10)).build();
                resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + resp.statusCode() + " for " + uri + " and " + gh);
                }
            }
            Files.copy(resp.body(), wheel);
        }
        Path aot = cacheDir.resolve("aot");
        if (Files.exists(aot)) {
            try (Stream<Path> walk = Files.walk(aot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        Files.createDirectories(aot);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(wheel))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                // Wheels nest under flashinfer_jit_cache/.../jit_cache or similar.
                if (e.isDirectory() || !name.endsWith(".so")) {
                    continue;
                }
                int slash = name.lastIndexOf('/');
                String leaf = slash >= 0 ? name.substring(slash + 1) : name;
                // Preserve one parent folder (module URI) when present.
                Path dest;
                if (slash > 0) {
                    String parent = name.substring(0, slash);
                    int pslash = parent.lastIndexOf('/');
                    String uriDir = pslash >= 0 ? parent.substring(pslash + 1) : parent;
                    dest = aot.resolve(uriDir).resolve(leaf);
                } else {
                    dest = aot.resolve(leaf);
                }
                Files.createDirectories(dest.getParent());
                Files.copy(zis, dest);
            }
        }
        logger.info("Extracted FlashInfer AOT modules to {}", aot);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
