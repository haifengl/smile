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

import java.util.regex.Pattern;

/**
 * Immutable identity of a versioned model artifact: {@code (slug, version)}.
 *
 * <p>Slugs and versions are restricted to URL-safe single-segment identifiers
 * so every registered model can be addressed unambiguously under
 * {@code /v1/{smile,onnx}/{slug}/versions/{version}} and {@code /v1/models/{slug}}.
 *
 * @param slug    the public model slug (e.g. {@code "fraud"}, {@code "iris_random_forest"}).
 * @param version the model version string (e.g. {@code "1"}, {@code "2.1.0"}).
 * @author Haifeng Li
 */
public record ModelRef(String slug, String version) implements Comparable<ModelRef> {

    /** Default version assigned when no version is declared by config, layout, or metadata. */
    public static final String DEFAULT_VERSION = "1";

    /** Alias pointing to the model's active default version (mutable; falls back to {@link #ALIAS_LATEST}). */
    public static final String ALIAS_DEFAULT = "default";

    /** Reserved dynamic alias always resolving to the highest loaded version. */
    public static final String ALIAS_LATEST = "latest";

    /** Pattern for valid model slugs: starts with alphanumeric, 1-128 URL-safe chars. */
    public static final Pattern SLUG_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$");

    /** Pattern for valid version strings: numeric, SemVer, or build-tagged identifiers. */
    public static final Pattern VERSION_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._+-]{0,63}$");

    /** Pattern for valid alias names: starts with letter, 1-64 URL-safe chars. */
    public static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9._-]{0,63}$");

    /** Compact constructor validating both slug and version. */
    public ModelRef {
        slug = validateSlug(slug);
        version = validateVersion(version);
    }

    /**
     * Returns the canonical {@code <slug>:<version>} composite key used for
     * internal telemetry and lifecycle event correlation.
     *
     * @return canonical {@code slug:version} key.
     */
    public String canonicalId() {
        return slug + ":" + version;
    }

    @Override
    public int compareTo(ModelRef other) {
        int cmp = this.slug.compareTo(other.slug);
        if (cmp != 0) {
            return cmp;
        }
        return VersionComparator.INSTANCE.compare(this.version, other.version);
    }

    /**
     * Validates and strips a model slug.
     *
     * @param raw raw slug string.
     * @return stripped valid slug.
     * @throws IllegalArgumentException if null, blank, contains {@code ".."}, or fails {@link #SLUG_PATTERN}.
     */
    public static String validateSlug(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Model slug must not be null");
        }
        String s = raw.strip();
        if (!isValidSlug(s)) {
            throw new IllegalArgumentException(
                    "Invalid model slug '" + raw + "': must match " + SLUG_PATTERN.pattern()
                            + " and must not contain '..'");
        }
        return s;
    }

    /**
     * Returns {@code true} if {@code slug} is a valid model slug.
     *
     * @param slug candidate slug.
     * @return {@code true} when valid.
     */
    public static boolean isValidSlug(String slug) {
        return slug != null
                && !slug.isEmpty()
                && !slug.contains("..")
                && SLUG_PATTERN.matcher(slug).matches();
    }

    /**
     * Validates and strips a model version string.
     *
     * @param raw raw version string.
     * @return stripped valid version.
     * @throws IllegalArgumentException if null, blank, contains {@code ".."}, or fails {@link #VERSION_PATTERN}.
     */
    public static String validateVersion(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Model version must not be null");
        }
        String v = raw.strip();
        if (!isValidVersion(v)) {
            throw new IllegalArgumentException(
                    "Invalid model version '" + raw + "': must match " + VERSION_PATTERN.pattern()
                            + " and must not contain '..'");
        }
        return v;
    }

    /**
     * Returns {@code true} if {@code version} is a valid model version.
     *
     * @param version candidate version.
     * @return {@code true} when valid.
     */
    public static boolean isValidVersion(String version) {
        return version != null
                && !version.isEmpty()
                && !version.contains("..")
                && VERSION_PATTERN.matcher(version).matches();
    }

    /**
     * Validates and strips a model alias name.
     *
     * @param raw raw alias name.
     * @return stripped valid alias.
     * @throws IllegalArgumentException if null, blank, contains {@code ".."}, or fails {@link #ALIAS_PATTERN}.
     */
    public static String validateAlias(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Model alias must not be null");
        }
        String a = raw.strip();
        if (!isValidAlias(a)) {
            throw new IllegalArgumentException(
                    "Invalid model alias '" + raw + "': must match " + ALIAS_PATTERN.pattern()
                            + " and must not contain '..'");
        }
        return a;
    }

    /**
     * Returns {@code true} if {@code alias} is a valid alias name.
     *
     * @param alias candidate alias.
     * @return {@code true} when valid.
     */
    public static boolean isValidAlias(String alias) {
        return alias != null
                && !alias.isEmpty()
                && !alias.contains("..")
                && ALIAS_PATTERN.matcher(alias).matches();
    }

    @Override
    public String toString() {
        return canonicalId();
    }
}
