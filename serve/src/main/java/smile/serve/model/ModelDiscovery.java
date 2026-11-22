/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Unified model artifact discovery for {@code .sml} and {@code .onnx} backends.
 *
 * <p>Supports three complementary layouts under a model directory:
 * <ol>
 *   <li><b>Declarative manifest</b> ({@code models.json} inside the directory, or
 *       an explicit manifest path): explicit slugs, versions, default pins, and aliases.</li>
 *   <li><b>Flat layout</b> ({@code <dir>/<file>.(sml|onnx)}): identity derived from
 *       embedded metadata or file stem, defaulting to version {@code "1"}.</li>
 *   <li><b>Versioned directory layout</b> ({@code <dir>/<slug>/<version>/<file>.(sml|onnx)}):
 *       directory names supply explicit {@code slug} and {@code version}.</li>
 * </ol>
 *
 * <p>Hidden directories, Hugging Face cache directories ({@code huggingface}), and
 * LLM/GenAI directories containing {@code genai_config.json}, {@code config.json}, or
 * {@code params.json} are skipped automatically during recursive discovery.
 *
 * @author Haifeng Li
 */
public final class ModelDiscovery {
    private static final Logger logger = Logger.getLogger(ModelDiscovery.class);

    /** A discovered artifact ready for verification and loading. */
    public record Candidate(ModelSource source, boolean fromManifest) {
        public Path path() {
            return source.path();
        }
    }

    /** A discovery-time failure (invalid manifest, ambiguous versioned directory, IO error). */
    public record DiscoveryError(Path path, Exception error) {
    }

    /** Result of scanning a configured path (and optional manifest) for a given {@link ModelManifest.Kind}. */
    public record Plan(
            List<Candidate> candidates,
            Map<String, String> defaultVersions,
            Map<String, Map<String, String>> aliases,
            List<DiscoveryError> errors) {
    }

    private ModelDiscovery() {
    }

    /**
     * Builds a discovery plan for startup or dynamic directory loading.
     *
     * @param rootPath       file or directory path to scan.
     * @param kind           artifact kind ({@link ModelManifest.Kind#SML} or {@link ModelManifest.Kind#ONNX}).
     * @param manifestPath   optional explicit path to {@code models.json}; when {@code null}, checks {@code <dir>/models.json}.
     * @param servedModelId  optional single-model slug override (ignored with warning when >1 artifact is discovered).
     * @param strictSingleExt when {@code true}, a single regular file must match {@code kind.extension()}.
     * @return the discovery plan.
     */
    public static Plan discover(Path rootPath,
                                ModelManifest.Kind kind,
                                Path manifestPath,
                                String servedModelId,
                                boolean strictSingleExt) {
        Path normalized = rootPath.toAbsolutePath().normalize();
        List<Candidate> candidates = new ArrayList<>();
        Map<String, String> defaults = new LinkedHashMap<>();
        Map<String, Map<String, String>> aliases = new LinkedHashMap<>();
        List<DiscoveryError> errors = new ArrayList<>();

        String validatedServedId = null;
        if (servedModelId != null && !servedModelId.isBlank()) {
            try {
                validatedServedId = ModelRef.validateSlug(servedModelId);
            } catch (IllegalArgumentException ex) {
                errors.add(new DiscoveryError(normalized, ex));
                return new Plan(List.of(), Map.of(), Map.of(), List.copyOf(errors));
            }
        }

        // Resolve manifest (explicit path or <dir>/models.json)
        Path effectiveManifest = null;
        if (manifestPath != null) {
            effectiveManifest = manifestPath.toAbsolutePath().normalize();
        } else if (Files.isDirectory(normalized)) {
            Path dirManifest = normalized.resolve(ModelManifest.DEFAULT_FILE_NAME);
            if (Files.isRegularFile(dirManifest)) {
                effectiveManifest = dirManifest;
            }
        }

        boolean autoDiscover = true;
        Set<Path> manifestPaths = Set.of();

        if (effectiveManifest != null) {
            try {
                ModelManifest manifest = ModelManifest.load(effectiveManifest);
                autoDiscover = manifest.autoDiscover();
                manifestPaths = manifest.referencedPaths(kind);
                for (ModelManifest.EntrySpec spec : manifest.entriesFor(kind)) {
                    for (var verEntry : spec.versions().entrySet()) {
                        candidates.add(new Candidate(
                                new ModelSource(verEntry.getValue(), spec.slug(), verEntry.getKey()),
                                true));
                    }
                    if (spec.defaultVersion() != null) {
                        defaults.put(spec.slug(), spec.defaultVersion());
                    }
                    if (!spec.aliases().isEmpty()) {
                        aliases.put(spec.slug(), spec.aliases());
                    }
                }
            } catch (Exception ex) {
                logger.errorf(ex, "Failed to load model manifest '%s'", effectiveManifest);
                errors.add(new DiscoveryError(effectiveManifest, ex));
                // Do not silently ignore a broken manifest
                return new Plan(List.of(), Map.of(), Map.of(), List.copyOf(errors));
            }
        }

        if (Files.isRegularFile(normalized)) {
            if (!strictSingleExt || hasExtension(normalized, kind)) {
                if (!manifestPaths.contains(normalized)) {
                    candidates.add(new Candidate(new ModelSource(normalized, validatedServedId, null), false));
                }
            }
        } else if (Files.isDirectory(normalized) && autoDiscover) {
            List<Candidate> discovered = new ArrayList<>();
            scanDirectory(normalized, kind, manifestPaths, discovered, errors);

            if (validatedServedId != null && effectiveManifest == null) {
                if (discovered.size() == 1 && !discovered.getFirst().source().hasExplicitOverride()) {
                    Candidate single = discovered.getFirst();
                    discovered.set(0, new Candidate(
                            new ModelSource(single.path(), validatedServedId, null),
                            false));
                } else if (discovered.size() > 1) {
                    logger.warnf("Ignoring served-model-id='%s' because directory '%s' contains %d %s files",
                            validatedServedId, normalized, discovered.size(), kind.extension());
                }
            }
            candidates.addAll(discovered);
        }

        return new Plan(
                List.copyOf(candidates),
                Collections.unmodifiableMap(defaults),
                Collections.unmodifiableMap(aliases),
                List.copyOf(errors));
    }

    /**
     * Returns {@code true} if {@code dir} contains at least one model file of {@code kind}
     * in either flat layout, versioned subdirectory layout, or a {@code models.json} manifest.
     */
    public static boolean hasModels(Path dir, ModelManifest.Kind kind) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        Plan plan = discover(dir, kind, null, null, true);
        return !plan.candidates().isEmpty();
    }

    private static void scanDirectory(Path dir,
                                      ModelManifest.Kind kind,
                                      Set<Path> excludePaths,
                                      List<Candidate> out,
                                      List<DiscoveryError> errors) {
        List<Path> children;
        try (Stream<Path> stream = Files.list(dir)) {
            children = stream.sorted().toList();
        } catch (IOException ex) {
            logger.errorf(ex, "Failed to list model directory '%s'", dir);
            errors.add(new DiscoveryError(dir, ex));
            return;
        }

        // 1. Flat files in dir
        for (Path child : children) {
            if (Files.isRegularFile(child) && hasExtension(child, kind)) {
                Path norm = child.toAbsolutePath().normalize();
                if (!excludePaths.contains(norm)) {
                    out.add(new Candidate(ModelSource.of(norm), false));
                }
            }
        }

        // 2. Versioned subdirectories: <dir>/<slug>/<version>/<file>.<ext>
        for (Path slugDir : children) {
            if (!Files.isDirectory(slugDir)) {
                continue;
            }
            String slugName = slugDir.getFileName().toString();
            if (isIgnoredDirectory(slugDir, slugName)) {
                continue;
            }
            if (!ModelRef.isValidSlug(slugName)) {
                continue;
            }

            List<Path> versionDirs;
            try (Stream<Path> stream = Files.list(slugDir)) {
                versionDirs = stream.filter(Files::isDirectory).sorted().toList();
            } catch (IOException ex) {
                logger.warnf(ex, "Failed to list subdirectory '%s'", slugDir);
                continue;
            }

            for (Path verDir : versionDirs) {
                String verName = verDir.getFileName().toString();
                if (isIgnoredDirectory(verDir, verName)) {
                    continue;
                }
                if (!ModelRef.isValidVersion(verName)) {
                    continue;
                }
                List<Path> artifacts;
                try (Stream<Path> stream = Files.list(verDir)) {
                    artifacts = stream
                            .filter(f -> Files.isRegularFile(f) && hasExtension(f, kind))
                            .map(f -> f.toAbsolutePath().normalize())
                            .filter(f -> !excludePaths.contains(f))
                            .sorted()
                            .toList();
                } catch (IOException ex) {
                    logger.warnf(ex, "Failed to list version directory '%s'", verDir);
                    continue;
                }

                if (artifacts.size() == 1) {
                    out.add(new Candidate(new ModelSource(artifacts.getFirst(), slugName, verName), false));
                } else if (artifacts.size() > 1) {
                    errors.add(new DiscoveryError(
                            verDir,
                            new IllegalArgumentException(
                                    "Version directory '" + verDir + "' contains " + artifacts.size()
                                            + " " + kind.extension() + " files; expected exactly 1")));
                }
            }
        }
    }

    private static boolean hasExtension(Path path, ModelManifest.Kind kind) {
        return path.toString().endsWith(kind.extension());
    }

    private static boolean isIgnoredDirectory(Path dir, String name) {
        if (name.startsWith(".") || "huggingface".equalsIgnoreCase(name)) {
            return true;
        }
        return Files.isRegularFile(dir.resolve("genai_config.json"))
                || Files.isRegularFile(dir.resolve("config.json"))
                || Files.isRegularFile(dir.resolve("params.json"));
    }
}
