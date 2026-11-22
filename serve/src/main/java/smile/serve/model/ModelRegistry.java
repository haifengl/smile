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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Thread-safe registry of versioned models grouped by slug, with mutable aliases
 * ({@code default}, custom aliases) and automatic {@code latest} resolution.
 *
 * <p>Versions within each slug are ordered by {@link VersionComparator}.
 * Unloaded versions retain their {@link ModelSource} so they can be restored
 * via reload.
 *
 * @param <T> runtime model wrapper type ({@code InferenceModel} or {@code OnnxModel}).
 * @author Haifeng Li
 */
public final class ModelRegistry<T> {

    /**
     * An active registered model version.
     *
     * @param ref    validated {@code (slug, version)} identity.
     * @param model  live model instance.
     * @param source backing file and identity overrides.
     * @param <T>    model type.
     */
    public record Entry<T>(ModelRef ref, T model, ModelSource source) {
        public Entry {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(source, "source");
        }
    }

    /**
     * Immutable snapshot of versions and aliases for a single slug.
     *
     * @param slug           the model slug.
     * @param defaultVersion the currently effective default version.
     * @param latestVersion  the highest loaded version according to {@link VersionComparator}.
     * @param versions       all loaded versions in ascending version order.
     * @param aliases        user-defined aliases plus {@code default} when explicitly pinned.
     */
    public record SlugVersions(
            String slug,
            String defaultVersion,
            String latestVersion,
            List<String> versions,
            Map<String, String> aliases) {
    }

    /** Exception thrown when registering or migrating a model conflicts with an existing active version. */
    public static final class ConflictException extends RuntimeException {
        private final ModelRef ref;

        public ConflictException(ModelRef ref, String message) {
            super(message);
            this.ref = ref;
        }

        public ModelRef ref() {
            return ref;
        }
    }

    private final Object lock = new Object();
    private final Map<String, NavigableMap<String, Entry<T>>> active = new TreeMap<>();
    private final Map<String, String> pinnedDefaults = new HashMap<>();
    private final Map<String, Map<String, String>> customAliases = new HashMap<>();
    private final Map<ModelRef, ModelSource> knownSources = new TreeMap<>();
    private final Map<String, String> lastDefaultVersion = new HashMap<>();

    /**
     * Returns {@code true} if the exact {@code (slug, version)} is currently active.
     */
    public boolean contains(ModelRef ref) {
        synchronized (lock) {
            NavigableMap<String, Entry<T>> versions = active.get(ref.slug());
            return versions != null && versions.containsKey(ref.version());
        }
    }

    /**
     * Returns {@code true} if at least one version is currently active for {@code slug}.
     */
    public boolean containsSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }
        synchronized (lock) {
            NavigableMap<String, Entry<T>> versions = active.get(slug);
            return versions != null && !versions.isEmpty();
        }
    }

    /**
     * Registers a new model version. Rejects with {@link ConflictException} if
     * the same {@code (slug, version)} is already active.
     *
     * @param entry the model entry to register.
     * @throws ConflictException if {@code entry.ref()} is already active.
     */
    public void register(Entry<T> entry) {
        synchronized (lock) {
            ModelRef ref = entry.ref();
            NavigableMap<String, Entry<T>> versions = active.get(ref.slug());
            if (versions != null && versions.containsKey(ref.version())) {
                throw new ConflictException(ref,
                        "Model '" + ref.slug() + "' version '" + ref.version() + "' is already loaded");
            }
            active.computeIfAbsent(ref.slug(), k -> new TreeMap<>(VersionComparator.INSTANCE))
                  .put(ref.version(), entry);
            knownSources.put(ref, entry.source());
        }
    }

    /**
     * Atomically validates and registers a batch of model entries along with optional
     * default version pins and aliases. Either all entries are registered or none are.
     *
     * @param entries         entries to add.
     * @param defaults        optional map of {@code slug -> defaultVersion}.
     * @param aliases         optional map of {@code slug -> (alias -> version)}.
     * @param pinExistingDefaultSlugs slugs whose current default should be pinned before adding new versions
     *                                (used when {@code set_default=false} on dynamic load).
     * @throws ConflictException if any entry conflicts with an active version or another entry in the batch.
     * @throws IllegalArgumentException if any default or alias target does not exist or uses a reserved alias.
     */
    public void registerBatch(List<Entry<T>> entries,
                              Map<String, String> defaults,
                              Map<String, Map<String, String>> aliases,
                              Set<String> pinExistingDefaultSlugs) {
        synchronized (lock) {
            Set<ModelRef> seen = new HashSet<>();
            Map<String, Set<String>> batchVersionsBySlug = new HashMap<>();
            for (Entry<T> entry : entries) {
                ModelRef ref = entry.ref();
                if (!seen.add(ref)) {
                    throw new ConflictException(ref,
                            "Duplicate model '" + ref.slug() + "' version '" + ref.version() + "' in batch");
                }
                NavigableMap<String, Entry<T>> existing = active.get(ref.slug());
                if (existing != null && existing.containsKey(ref.version())) {
                    throw new ConflictException(ref,
                            "Model '" + ref.slug() + "' version '" + ref.version() + "' is already loaded");
                }
                batchVersionsBySlug.computeIfAbsent(ref.slug(), k -> new HashSet<>()).add(ref.version());
            }

            // Validate defaults
            if (defaults != null) {
                for (var e : defaults.entrySet()) {
                    String slug = ModelRef.validateSlug(e.getKey());
                    String ver = ModelRef.validateVersion(e.getValue());
                    if (!willExist(slug, ver, batchVersionsBySlug)) {
                        throw new IllegalArgumentException(
                                "Default version '" + ver + "' for model '" + slug + "' is not loaded");
                    }
                }
            }

            // Validate aliases
            if (aliases != null) {
                for (var slugEntry : aliases.entrySet()) {
                    String slug = ModelRef.validateSlug(slugEntry.getKey());
                    for (var aliasEntry : slugEntry.getValue().entrySet()) {
                        String alias = ModelRef.validateAlias(aliasEntry.getKey());
                        if (ModelRef.ALIAS_LATEST.equals(alias)) {
                            throw new IllegalArgumentException("Alias 'latest' is reserved and cannot be assigned");
                        }
                        String ver = ModelRef.validateVersion(aliasEntry.getValue());
                        if (!willExist(slug, ver, batchVersionsBySlug)) {
                            throw new IllegalArgumentException(
                                    "Alias '" + alias + "' for model '" + slug
                                            + "' targets unloaded version '" + ver + "'");
                        }
                    }
                }
            }

            // Pin existing defaults when requested (e.g. set_default=false)
            if (pinExistingDefaultSlugs != null) {
                for (String slug : pinExistingDefaultSlugs) {
                    if (!pinnedDefaults.containsKey(slug)) {
                        NavigableMap<String, Entry<T>> existing = active.get(slug);
                        if (existing != null && !existing.isEmpty()) {
                            pinnedDefaults.put(slug, existing.lastKey());
                        }
                    }
                }
            }

            // Commit entries
            for (Entry<T> entry : entries) {
                ModelRef ref = entry.ref();
                active.computeIfAbsent(ref.slug(), k -> new TreeMap<>(VersionComparator.INSTANCE))
                      .put(ref.version(), entry);
                knownSources.put(ref, entry.source());
            }

            // Commit defaults
            if (defaults != null) {
                for (var e : defaults.entrySet()) {
                    pinnedDefaults.put(e.getKey(), e.getValue());
                }
            }

            // Commit aliases
            if (aliases != null) {
                for (var slugEntry : aliases.entrySet()) {
                    String slug = slugEntry.getKey();
                    for (var aliasEntry : slugEntry.getValue().entrySet()) {
                        String alias = aliasEntry.getKey();
                        String ver = aliasEntry.getValue();
                        if (ModelRef.ALIAS_DEFAULT.equals(alias)) {
                            pinnedDefaults.put(slug, ver);
                        } else {
                            customAliases.computeIfAbsent(slug, k -> new TreeMap<>()).put(alias, ver);
                        }
                    }
                }
            }
        }
    }

    private boolean willExist(String slug, String version, Map<String, Set<String>> batchVersionsBySlug) {
        NavigableMap<String, Entry<T>> existing = active.get(slug);
        if (existing != null && existing.containsKey(version)) {
            return true;
        }
        Set<String> added = batchVersionsBySlug.get(slug);
        return added != null && added.contains(version);
    }

    /**
     * Replaces an existing (or previously unloaded) model version with a reloaded entry.
     *
     * <p>If the reloaded model's {@link ModelRef} changed (for example, an un-overridden
     * {@code .sml} file whose embedded {@code Model.VERSION} tag was bumped on disk),
     * verifies that the new {@link ModelRef} is not already occupied by another active
     * model, migrates aliases within the same slug, and removes the old key.
     *
     * @param oldRef   the ref being reloaded.
     * @param newEntry the newly loaded entry.
     * @return the previously active entry, or {@code null} if {@code oldRef} was unloaded.
     * @throws ConflictException if {@code newEntry.ref()} differs from {@code oldRef} and is already active.
     */
    public Entry<T> replace(ModelRef oldRef, Entry<T> newEntry) {
        synchronized (lock) {
            ModelRef newRef = newEntry.ref();
            boolean changed = !oldRef.equals(newRef);
            if (changed) {
                NavigableMap<String, Entry<T>> targetVersions = active.get(newRef.slug());
                if (targetVersions != null && targetVersions.containsKey(newRef.version())) {
                    throw new ConflictException(newRef,
                            "Cannot reload '" + oldRef + "' as '" + newRef + "': target model version is already loaded");
                }
            }

            Entry<T> previous = null;
            NavigableMap<String, Entry<T>> oldVersions = active.get(oldRef.slug());
            if (oldVersions != null) {
                previous = oldVersions.remove(oldRef.version());
                if (oldVersions.isEmpty()) {
                    active.remove(oldRef.slug());
                }
            }
            if (changed) {
                knownSources.remove(oldRef);
                if (oldRef.slug().equals(newRef.slug())) {
                    // Migrate aliases/default pointing to old version within the same slug
                    if (oldRef.version().equals(pinnedDefaults.get(oldRef.slug()))) {
                        pinnedDefaults.put(newRef.slug(), newRef.version());
                    }
                    Map<String, String> slugAliases = customAliases.get(oldRef.slug());
                    if (slugAliases != null) {
                        for (var a : slugAliases.entrySet()) {
                            if (oldRef.version().equals(a.getValue())) {
                                a.setValue(newRef.version());
                            }
                        }
                    }
                } else {
                    cleanupAliasesForVersion(oldRef);
                }
            }

            active.computeIfAbsent(newRef.slug(), k -> new TreeMap<>(VersionComparator.INSTANCE))
                  .put(newRef.version(), newEntry);
            knownSources.put(newRef, newEntry.source());
            return previous;
        }
    }

    /**
     * Resolves the effective default version of {@code slug}.
     *
     * @param slug the model slug.
     * @return the active default entry, or empty if no version is loaded.
     */
    public Optional<Entry<T>> resolve(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        synchronized (lock) {
            NavigableMap<String, Entry<T>> versions = active.get(slug);
            if (versions == null || versions.isEmpty()) {
                return Optional.empty();
            }
            String pinned = pinnedDefaults.get(slug);
            if (pinned != null) {
                Entry<T> entry = versions.get(pinned);
                if (entry != null) {
                    return Optional.of(entry);
                }
            }
            return Optional.of(versions.lastEntry().getValue());
        }
    }

    /**
     * Resolves an exact {@code (slug, version)} pair.
     */
    public Optional<Entry<T>> resolveVersion(String slug, String version) {
        if (slug == null || slug.isBlank() || version == null || version.isBlank()) {
            return Optional.empty();
        }
        synchronized (lock) {
            NavigableMap<String, Entry<T>> versions = active.get(slug);
            if (versions == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(versions.get(version));
        }
    }

    /**
     * Resolves an alias ({@code "default"}, {@code "latest"}, or a custom alias) for {@code slug}.
     */
    public Optional<Entry<T>> resolveAlias(String slug, String alias) {
        if (slug == null || slug.isBlank() || alias == null || alias.isBlank()) {
            return Optional.empty();
        }
        synchronized (lock) {
            NavigableMap<String, Entry<T>> versions = active.get(slug);
            if (versions == null || versions.isEmpty()) {
                return Optional.empty();
            }
            if (ModelRef.ALIAS_LATEST.equals(alias)) {
                return Optional.of(versions.lastEntry().getValue());
            }
            if (ModelRef.ALIAS_DEFAULT.equals(alias)) {
                return resolve(slug);
            }
            Map<String, String> slugAliases = customAliases.get(slug);
            if (slugAliases == null) {
                return Optional.empty();
            }
            String targetVersion = slugAliases.get(alias);
            return targetVersion != null ? Optional.ofNullable(versions.get(targetVersion)) : Optional.empty();
        }
    }

    /**
     * Removes a single active version while keeping its {@link ModelSource} for reload.
     *
     * @param ref the version to remove.
     * @return the removed entry, or empty if it was not active.
     */
    public Optional<Entry<T>> removeVersion(ModelRef ref) {
        synchronized (lock) {
            NavigableMap<String, Entry<T>> versions = active.get(ref.slug());
            if (versions == null) {
                return Optional.empty();
            }
            Entry<T> removed = versions.remove(ref.version());
            if (removed == null) {
                return Optional.empty();
            }
            cleanupAliasesForVersion(ref);
            if (versions.isEmpty()) {
                active.remove(ref.slug());
                lastDefaultVersion.put(ref.slug(), ref.version());
            }
            return Optional.of(removed);
        }
    }

    /**
     * Removes all active versions for {@code slug}, keeping their {@link ModelSource}s for reload.
     *
     * @param slug the model slug.
     * @return all removed entries in version order (empty if slug was not loaded).
     */
    public List<Entry<T>> removeSlug(String slug) {
        synchronized (lock) {
            NavigableMap<String, Entry<T>> versions = active.remove(slug);
            if (versions == null || versions.isEmpty()) {
                return List.of();
            }
            String currentDefault = pinnedDefaults.remove(slug);
            if (currentDefault == null || !versions.containsKey(currentDefault)) {
                currentDefault = versions.lastKey();
            }
            lastDefaultVersion.put(slug, currentDefault);
            customAliases.remove(slug);
            return List.copyOf(versions.values());
        }
    }

    private void cleanupAliasesForVersion(ModelRef ref) {
        if (ref.version().equals(pinnedDefaults.get(ref.slug()))) {
            pinnedDefaults.remove(ref.slug());
        }
        Map<String, String> slugAliases = customAliases.get(ref.slug());
        if (slugAliases != null) {
            slugAliases.values().removeIf(v -> v.equals(ref.version()));
            if (slugAliases.isEmpty()) {
                customAliases.remove(ref.slug());
            }
        }
    }

    /**
     * Assigns or updates an alias for {@code slug}.
     *
     * <p>Setting alias {@code "default"} pins the slug's default version.
     * Setting {@code "latest"} is rejected because {@code latest} is always dynamic.
     *
     * @param slug    the model slug.
     * @param alias   the alias name.
     * @param version the target version (must currently be loaded).
     * @return updated {@link SlugVersions} snapshot.
     * @throws IllegalArgumentException if alias is invalid or reserved, or target version is not loaded.
     */
    public SlugVersions setAlias(String slug, String alias, String version) {
        String validSlug = ModelRef.validateSlug(slug);
        String validAlias = ModelRef.validateAlias(alias);
        String validVersion = ModelRef.validateVersion(version);
        if (ModelRef.ALIAS_LATEST.equals(validAlias)) {
            throw new IllegalArgumentException("Alias 'latest' is reserved and always resolves to the highest version");
        }
        synchronized (lock) {
            NavigableMap<String, Entry<T>> versions = active.get(validSlug);
            if (versions == null || !versions.containsKey(validVersion)) {
                throw new IllegalArgumentException(
                        "Model '" + validSlug + "' version '" + validVersion + "' is not loaded");
            }
            if (ModelRef.ALIAS_DEFAULT.equals(validAlias)) {
                pinnedDefaults.put(validSlug, validVersion);
            } else {
                customAliases.computeIfAbsent(validSlug, k -> new TreeMap<>()).put(validAlias, validVersion);
            }
            return versionsInfoLocked(validSlug);
        }
    }

    /**
     * Deletes a custom alias, or unpins {@code "default"} so it reverts to tracking {@code latest}.
     *
     * @param slug  the model slug.
     * @param alias the alias to delete.
     * @return {@code true} if the alias existed and was removed.
     * @throws IllegalArgumentException if {@code alias} is {@code "latest"} or invalid.
     */
    public boolean deleteAlias(String slug, String alias) {
        String validSlug = ModelRef.validateSlug(slug);
        String validAlias = ModelRef.validateAlias(alias);
        if (ModelRef.ALIAS_LATEST.equals(validAlias)) {
            throw new IllegalArgumentException("Alias 'latest' is reserved and cannot be deleted");
        }
        synchronized (lock) {
            if (ModelRef.ALIAS_DEFAULT.equals(validAlias)) {
                return pinnedDefaults.remove(validSlug) != null;
            }
            Map<String, String> slugAliases = customAliases.get(validSlug);
            if (slugAliases == null) {
                return false;
            }
            boolean removed = slugAliases.remove(validAlias) != null;
            if (slugAliases.isEmpty()) {
                customAliases.remove(validSlug);
            }
            return removed;
        }
    }

    /**
     * Returns version and alias metadata for a loaded slug.
     */
    public Optional<SlugVersions> versionsInfo(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        synchronized (lock) {
            if (!active.containsKey(slug)) {
                return Optional.empty();
            }
            return Optional.of(versionsInfoLocked(slug));
        }
    }

    private SlugVersions versionsInfoLocked(String slug) {
        NavigableMap<String, Entry<T>> versions = active.get(slug);
        String latest = versions.lastKey();
        String pinned = pinnedDefaults.get(slug);
        String effectiveDefault = (pinned != null && versions.containsKey(pinned)) ? pinned : latest;
        Map<String, String> aliasMap = new LinkedHashMap<>();
        if (pinned != null && versions.containsKey(pinned)) {
            aliasMap.put(ModelRef.ALIAS_DEFAULT, pinned);
        }
        Map<String, String> custom = customAliases.get(slug);
        if (custom != null) {
            aliasMap.putAll(custom);
        }
        return new SlugVersions(
                slug,
                effectiveDefault,
                latest,
                List.copyOf(versions.keySet()),
                Collections.unmodifiableMap(aliasMap));
    }

    /**
     * Returns all active slugs in sorted order.
     */
    public List<String> slugs() {
        synchronized (lock) {
            return List.copyOf(active.keySet());
        }
    }

    /**
     * Returns the active default entry for every loaded slug, in slug order.
     */
    public List<Entry<T>> defaultEntries() {
        synchronized (lock) {
            List<Entry<T>> result = new ArrayList<>(active.size());
            for (String slug : active.keySet()) {
                resolve(slug).ifPresent(result::add);
            }
            return List.copyOf(result);
        }
    }

    /**
     * Returns every active {@code (slug, version)} entry across all slugs.
     */
    public List<Entry<T>> allEntries() {
        synchronized (lock) {
            List<Entry<T>> result = new ArrayList<>();
            for (NavigableMap<String, Entry<T>> versions : active.values()) {
                result.addAll(versions.values());
            }
            return List.copyOf(result);
        }
    }

    /**
     * Returns the total count of active model versions across all slugs.
     */
    public int activeVersionCount() {
        synchronized (lock) {
            int total = 0;
            for (NavigableMap<String, Entry<T>> versions : active.values()) {
                total += versions.size();
            }
            return total;
        }
    }

    /**
     * Returns the number of distinct active slugs.
     */
    public int slugCount() {
        synchronized (lock) {
            return active.size();
        }
    }

    /**
     * Returns the retained {@link ModelSource} for a specific {@link ModelRef}
     * (whether currently loaded or unloaded).
     */
    public Optional<ModelSource> knownSource(ModelRef ref) {
        synchronized (lock) {
            return Optional.ofNullable(knownSources.get(ref));
        }
    }

    /**
     * Returns the ref that {@code POST /{slug}/reload} should target:
     * the currently active default ref if loaded, otherwise the last default
     * (or highest known) ref retained from before unload.
     */
    public Optional<ModelRef> reloadTargetForSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        synchronized (lock) {
            Optional<Entry<T>> current = resolve(slug);
            if (current.isPresent()) {
                return Optional.of(current.get().ref());
            }
            String lastVer = lastDefaultVersion.get(slug);
            if (lastVer != null) {
                ModelRef candidate = new ModelRef(slug, lastVer);
                if (knownSources.containsKey(candidate)) {
                    return Optional.of(candidate);
                }
            }
            ModelRef highest = null;
            for (ModelRef ref : knownSources.keySet()) {
                if (ref.slug().equals(slug)) {
                    if (highest == null || VersionComparator.INSTANCE.compare(ref.version(), highest.version()) > 0) {
                        highest = ref;
                    }
                }
            }
            return Optional.ofNullable(highest);
        }
    }
}
