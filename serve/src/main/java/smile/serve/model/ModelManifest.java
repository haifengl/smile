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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Parser and validator for declarative {@code models.json} manifests.
 *
 * <p>Uses Vert.x {@link JsonObject} / {@link JsonArray} so parsing requires zero
 * reflection metadata in GraalVM native and micro builds.
 *
 * <p>Example manifest:
 * <pre>{@code
 * {
 *   "auto_discover": true,
 *   "models": [
 *     {
 *       "slug": "fraud",
 *       "kind": "sml",
 *       "versions": {
 *         "1": "fraud_rf.sml",
 *         "2": "fraud_gb.sml"
 *       },
 *       "default": "2",
 *       "aliases": {
 *         "stable": "1",
 *         "canary": "2"
 *       }
 *     },
 *     {
 *       "slug": "vision-cls",
 *       "kind": "onnx",
 *       "path": "squeezenet.onnx",
 *       "version": "1.0"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @author Haifeng Li
 */
public final class ModelManifest {

    /** Conventional manifest file name inside a model directory. */
    public static final String DEFAULT_FILE_NAME = "models.json";

    /** Supported artifact kinds in {@code models.json}. */
    public enum Kind {
        SML(".sml"),
        ONNX(".onnx");

        private final String extension;

        Kind(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    /**
     * A single declared model family from {@code models.json}.
     *
     * @param slug           validated model slug.
     * @param kind           artifact kind ({@link Kind#SML} or {@link Kind#ONNX}).
     * @param versions       ordered map of {@code version -> normalized file path}.
     * @param defaultVersion optional pinned default version (present in {@code versions}).
     * @param aliases        optional map of {@code alias -> version} (all targets present in {@code versions}).
     */
    public record EntrySpec(
            String slug,
            Kind kind,
            Map<String, Path> versions,
            String defaultVersion,
            Map<String, String> aliases) {
    }

    private final Path manifestPath;
    private final boolean autoDiscover;
    private final List<EntrySpec> entries;

    private ModelManifest(Path manifestPath, boolean autoDiscover, List<EntrySpec> entries) {
        this.manifestPath = manifestPath;
        this.autoDiscover = autoDiscover;
        this.entries = List.copyOf(entries);
    }

    /**
     * Reads and validates a {@code models.json} manifest from disk.
     *
     * @param manifestPath path to the manifest JSON file.
     * @return parsed manifest.
     * @throws IOException              if the file cannot be read.
     * @throws IllegalArgumentException if the JSON structure or fields are invalid.
     */
    public static ModelManifest load(Path manifestPath) throws IOException {
        Path normalized = manifestPath.toAbsolutePath().normalize();
        Path baseDir = normalized.getParent() != null ? normalized.getParent() : Path.of("").toAbsolutePath();
        String raw = Files.readString(normalized, StandardCharsets.UTF_8);
        JsonObject root;
        try {
            root = new JsonObject(raw);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid JSON in manifest '" + normalized + "': " + ex.getMessage(), ex);
        }

        boolean autoDiscover = true;
        if (root.containsKey("auto_discover")) {
            Object val = root.getValue("auto_discover");
            if (!(val instanceof Boolean b)) {
                throw new IllegalArgumentException("'auto_discover' in '" + normalized + "' must be a boolean");
            }
            autoDiscover = b;
        }

        List<EntrySpec> entries = new ArrayList<>();
        Set<String> seenSlugs = new HashSet<>();
        JsonArray models = root.getJsonArray("models");
        if (models != null) {
            for (int i = 0; i < models.size(); i++) {
                Object elem = models.getValue(i);
                if (!(elem instanceof JsonObject obj)) {
                    throw new IllegalArgumentException("models[" + i + "] in '" + normalized + "' must be a JSON object");
                }
                EntrySpec spec = parseEntry(obj, i, baseDir, normalized);
                if (!seenSlugs.add(spec.slug())) {
                    throw new IllegalArgumentException(
                            "Duplicate slug '" + spec.slug() + "' in manifest '" + normalized + "'");
                }
                entries.add(spec);
            }
        } else if (root.containsKey("models")) {
            throw new IllegalArgumentException("'models' in '" + normalized + "' must be a JSON array");
        }

        return new ModelManifest(normalized, autoDiscover, entries);
    }

    private static EntrySpec parseEntry(JsonObject obj, int index, Path baseDir, Path manifestPath) {
        String rawSlug = obj.getString("slug");
        if (rawSlug == null || rawSlug.isBlank()) {
            throw new IllegalArgumentException("models[" + index + "] in '" + manifestPath + "' is missing required 'slug'");
        }
        String slug = ModelRef.validateSlug(rawSlug);

        Kind declaredKind = parseKind(obj.getString("kind"), slug, manifestPath);

        boolean hasPath = obj.containsKey("path") && obj.getValue("path") != null;
        boolean hasVersions = obj.containsKey("versions") && obj.getValue("versions") != null;
        if (hasPath && hasVersions) {
            throw new IllegalArgumentException(
                    "Model '" + slug + "' in '" + manifestPath + "' must specify either 'path' or 'versions', not both");
        }
        if (!hasPath && !hasVersions) {
            throw new IllegalArgumentException(
                    "Model '" + slug + "' in '" + manifestPath + "' must specify 'path' or 'versions'");
        }

        Map<String, Path> versions = new TreeMap<>(VersionComparator.INSTANCE);
        Kind inferredKind = declaredKind;

        if (hasPath) {
            String rawPath = obj.getString("path");
            if (rawPath == null || rawPath.isBlank()) {
                throw new IllegalArgumentException("Model '" + slug + "' has blank 'path' in '" + manifestPath + "'");
            }
            String rawVer = obj.getString("version");
            String version = (rawVer != null && !rawVer.isBlank())
                    ? ModelRef.validateVersion(rawVer)
                    : ModelRef.DEFAULT_VERSION;
            Path resolved = resolvePath(baseDir, rawPath);
            inferredKind = resolveAndCheckKind(inferredKind, resolved, slug, manifestPath);
            versions.put(version, resolved);
        } else {
            Object rawVersions = obj.getValue("versions");
            if (!(rawVersions instanceof JsonObject verObj) || verObj.isEmpty()) {
                throw new IllegalArgumentException(
                        "Model '" + slug + "' 'versions' in '" + manifestPath + "' must be a non-empty JSON object");
            }
            for (String verKey : verObj.fieldNames()) {
                String version = ModelRef.validateVersion(verKey);
                Object rawPathVal = verObj.getValue(verKey);
                if (!(rawPathVal instanceof String rawPath) || rawPath.isBlank()) {
                    throw new IllegalArgumentException(
                            "Model '" + slug + "' version '" + version + "' path must be a non-blank string");
                }
                Path resolved = resolvePath(baseDir, rawPath);
                inferredKind = resolveAndCheckKind(inferredKind, resolved, slug, manifestPath);
                if (versions.put(version, resolved) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate version '" + version + "' for model '" + slug + "' in '" + manifestPath + "'");
                }
            }
        }

        if (inferredKind == null) {
            throw new IllegalArgumentException(
                    "Cannot infer 'kind' (sml/onnx) for model '" + slug + "' in '" + manifestPath
                            + "'; specify 'kind' explicitly");
        }

        String defaultVersion = null;
        if (obj.containsKey("default") && obj.getValue("default") != null) {
            defaultVersion = ModelRef.validateVersion(obj.getString("default"));
            if (!versions.containsKey(defaultVersion)) {
                throw new IllegalArgumentException(
                        "Model '" + slug + "' default version '" + defaultVersion
                                + "' is not defined in versions " + versions.keySet());
            }
        }

        Map<String, String> aliases = new LinkedHashMap<>();
        if (obj.containsKey("aliases") && obj.getValue("aliases") != null) {
            Object rawAliases = obj.getValue("aliases");
            if (!(rawAliases instanceof JsonObject aliasObj)) {
                throw new IllegalArgumentException(
                        "Model '" + slug + "' 'aliases' in '" + manifestPath + "' must be a JSON object");
            }
            for (String rawAlias : aliasObj.fieldNames()) {
                String alias = ModelRef.validateAlias(rawAlias);
                if (ModelRef.ALIAS_LATEST.equals(alias)) {
                    throw new IllegalArgumentException(
                            "Model '" + slug + "' cannot assign reserved alias 'latest' in '" + manifestPath + "'");
                }
                Object targetVal = aliasObj.getValue(rawAlias);
                if (!(targetVal instanceof String targetStr) || targetStr.isBlank()) {
                    throw new IllegalArgumentException(
                            "Model '" + slug + "' alias '" + alias + "' target must be a non-blank version string");
                }
                String targetVer = ModelRef.validateVersion(targetStr);
                if (!versions.containsKey(targetVer)) {
                    throw new IllegalArgumentException(
                            "Model '" + slug + "' alias '" + alias + "' targets undefined version '" + targetVer + "'");
                }
                if (ModelRef.ALIAS_DEFAULT.equals(alias)) {
                    if (defaultVersion != null && !defaultVersion.equals(targetVer)) {
                        throw new IllegalArgumentException(
                                "Model '" + slug + "' has conflicting 'default' (" + defaultVersion
                                        + ") and aliases.default (" + targetVer + ")");
                    }
                    defaultVersion = targetVer;
                } else {
                    aliases.put(alias, targetVer);
                }
            }
        }

        return new EntrySpec(
                slug,
                inferredKind,
                Collections.unmodifiableMap(versions),
                defaultVersion,
                Collections.unmodifiableMap(aliases));
    }

    private static Kind parseKind(String rawKind, String slug, Path manifestPath) {
        if (rawKind == null || rawKind.isBlank()) {
            return null;
        }
        return switch (rawKind.strip().toLowerCase(Locale.ROOT)) {
            case "sml", "smile" -> Kind.SML;
            case "onnx" -> Kind.ONNX;
            default -> throw new IllegalArgumentException(
                    "Unsupported kind '" + rawKind + "' for model '" + slug + "' in '" + manifestPath
                            + "' (expected 'sml' or 'onnx')");
        };
    }

    private static Kind resolveAndCheckKind(Kind current, Path file, String slug, Path manifestPath) {
        String name = file.getFileName() != null ? file.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        Kind fromExt = null;
        if (name.endsWith(".sml")) {
            fromExt = Kind.SML;
        } else if (name.endsWith(".onnx")) {
            fromExt = Kind.ONNX;
        }
        if (current == null) {
            return fromExt;
        }
        if (fromExt != null && fromExt != current) {
            throw new IllegalArgumentException(
                    "Model '" + slug + "' declares kind '" + current + "' but file '" + file
                            + "' has extension for '" + fromExt + "' in '" + manifestPath + "'");
        }
        return current;
    }

    private static Path resolvePath(Path baseDir, String rawPath) {
        Path p = Path.of(rawPath.strip());
        return (p.isAbsolute() ? p : baseDir.resolve(p)).toAbsolutePath().normalize();
    }

    /** Returns the normalized path to the manifest file. */
    public Path manifestPath() {
        return manifestPath;
    }

    /** Returns whether unreferenced files in the model directory should also be auto-discovered. */
    public boolean autoDiscover() {
        return autoDiscover;
    }

    /** Returns all declared model entries. */
    public List<EntrySpec> entries() {
        return entries;
    }

    /** Returns all declared entries matching {@code kind}. */
    public List<EntrySpec> entriesFor(Kind kind) {
        return entries.stream().filter(e -> e.kind() == kind).toList();
    }

    /** Returns all normalized artifact paths referenced by entries of {@code kind}. */
    public Set<Path> referencedPaths(Kind kind) {
        Set<Path> paths = new LinkedHashSet<>();
        for (EntrySpec e : entries) {
            if (e.kind() == kind) {
                paths.addAll(e.versions().values());
            }
        }
        return Collections.unmodifiableSet(paths);
    }
}
