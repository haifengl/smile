/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import smile.studio.StudioConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The set of marketplaces the user has registered, and their cached catalogs.
 *
 * <p>A marketplace is registered once by pointing at a source. Registration
 * resolves the source to a local root (fetching it for a remote source) and reads
 * its manifest. The resolved root is persisted so later catalog reads do not need
 * the network.
 *
 * @author Haifeng Li
 */
public final class MarketplaceRegistry {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MarketplaceRegistry.class);

    /**
     * Error message returned when attempting to register a private or custom marketplace
     * without an active SMILE Agent Studio Commercial License or 30-day Commercial Trial.
     */
    public static final String COMMERCIAL_ENTITLEMENT_REQUIRED =
            "Private & Custom Enterprise Catalogs require a SMILE Agent Studio Commercial License "
            + "or active 30-day Commercial Trial.";

    /** Strategy for resolving a marketplace source to a local root directory (injectable for tests). */
    @FunctionalInterface
    interface RootResolver {
        Path resolve(MarketplaceSource source, Path cacheDir) throws IOException;
    }

    private final PluginHome home;
    private final Fetcher fetcher;
    private final StudioConfig.Plugins policy;
    private final BooleanSupplier commercialEntitled;
    private final RootResolver rootResolver;

    /**
     * Constructor.
     * @param home the plugin home.
     */
    public MarketplaceRegistry(PluginHome home) {
        this(home, StudioConfig.plugins(), () -> true);
    }

    /**
     * Constructor with an explicit commercial entitlement check.
     * @param home the plugin home.
     * @param commercialEntitled supplier indicating whether commercial entitlement is active.
     */
    public MarketplaceRegistry(PluginHome home, BooleanSupplier commercialEntitled) {
        this(home, StudioConfig.plugins(), commercialEntitled);
    }

    /**
     * Constructor with an explicit policy, for tests.
     * @param home the plugin home.
     * @param policy the marketplace policy from {@code studio.json}.
     */
    MarketplaceRegistry(PluginHome home, StudioConfig.Plugins policy) {
        this(home, policy, () -> true);
    }

    /**
     * Constructor with an explicit policy and commercial entitlement predicate.
     * @param home the plugin home.
     * @param policy the marketplace policy from {@code studio.json}.
     * @param commercialEntitled supplier indicating whether commercial entitlement is active.
     */
    MarketplaceRegistry(PluginHome home, StudioConfig.Plugins policy, BooleanSupplier commercialEntitled) {
        this.home = Objects.requireNonNull(home, "home");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.commercialEntitled = Objects.requireNonNull(commercialEntitled, "commercialEntitled");
        this.fetcher = new Fetcher();
        this.rootResolver = fetcher::marketplaceRoot;
    }

    /**
     * Constructor with an explicit root resolver, for offline unit tests.
     * @param home the plugin home.
     * @param policy the marketplace policy from {@code studio.json}.
     * @param commercialEntitled supplier indicating whether commercial entitlement is active.
     * @param rootResolver custom resolver for marketplace sources.
     */
    MarketplaceRegistry(PluginHome home, StudioConfig.Plugins policy,
                        BooleanSupplier commercialEntitled, RootResolver rootResolver) {
        this.home = Objects.requireNonNull(home, "home");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.commercialEntitled = Objects.requireNonNull(commercialEntitled, "commercialEntitled");
        this.fetcher = new Fetcher();
        this.rootResolver = Objects.requireNonNull(rootResolver, "rootResolver");
    }

    /**
     * Returns whether commercial features (such as Private &amp; Custom Enterprise Catalogs)
     * are currently unlocked.
     *
     * @return true if commercial entitlement is active.
     */
    public boolean isCommercialEntitled() {
        return commercialEntitled.getAsBoolean();
    }

    /**
     * One registered marketplace.
     * @param name the marketplace name (from its manifest).
     * @param sourceText the source string it was added by.
     * @param root the resolved local root.
     * @param manifest the parsed catalog.
     */
    public record Registered(String name, String sourceText, Path root, MarketplaceManifest manifest) { }

    /**
     * Registers a marketplace by source string, fetching and reading its manifest.
     *
     * <p>This is the explicit path — the user typed the source — so no allowlist
     * check applies. The implicit first-run seed goes through
     * {@link #addImplicit(String)} instead.
     *
     * @param sourceText the source, in the {@code /plugin marketplace add} shorthand.
     * @return the registered marketplace.
     * @throws IOException if the source cannot be resolved or its manifest is invalid.
     */
    public Registered add(String sourceText) throws IOException {
        return add(sourceText, false);
    }

    /**
     * Registers a marketplace that Studio is adding on the user's behalf, such as
     * the official Anthropic marketplace seeded on first open.
     *
     * <p>Because the fetch happens without an explicit user action, the source must
     * be trusted: it has to be one of the built-in known defaults or one of the
     * sources named by {@code plugins.marketplaces} in {@code studio.json}. Any
     * other source is refused rather than fetched (ADR-008 — no silent network for
     * an arbitrary source).
     *
     * @param sourceText the source to seed.
     * @return the registered marketplace.
     * @throws IOException if the source is not trusted, cannot be resolved, or has
     *                     an invalid manifest.
     */
    public Registered addImplicit(String sourceText) throws IOException {
        return add(sourceText, true);
    }

    private Registered add(String sourceText, boolean implicit) throws IOException {
        MarketplaceSource source = MarketplaceSource.parse(sourceText);
        if (!PluginDefaults.isBuiltIn(sourceText) && !commercialEntitled.getAsBoolean()) {
            throw new IOException(COMMERCIAL_ENTITLEMENT_REQUIRED);
        }
        if (implicit && !PluginDefaults.isTrustedSource(sourceText, policy.marketplaces())) {
            throw new IOException("Marketplace source is not one of plugins.marketplaces: " + sourceText);
        }
        Path root = rootResolver.resolve(source, home.cache());
        MarketplaceManifest manifest = MarketplaceManifest.from(root);

        Map<String, Stored> stored = load();
        stored.put(manifest.name(), new Stored(manifest.name(), source.asText(), root.toString()));
        save(stored);
        logger.info("Registered marketplace {} from {}", manifest.name(), sourceText);
        return new Registered(manifest.name(), source.asText(), root, manifest);
    }

    /**
     * Removes a marketplace from the registry.
     * @param name the marketplace name.
     * @throws IOException if the registry cannot be written.
     */
    public void remove(String name) throws IOException {
        Map<String, Stored> stored = load();
        if (stored.remove(name) != null) {
            save(stored);
        }
    }

    /**
     * Lists every registered marketplace with its current manifest.
     * A marketplace whose manifest can no longer be read is included with a null
     * manifest so the user sees it and can remove it.
     *
     * @return the registered marketplaces.
     */
    public List<Registered> list() {
        List<Registered> result = new ArrayList<>();
        for (Stored stored : load().values()) {
            Path root = Path.of(stored.root());
            MarketplaceManifest manifest = null;
            try {
                manifest = MarketplaceManifest.from(root);
            } catch (IOException ex) {
                logger.warn("Marketplace {} manifest unreadable: {}", stored.name(), ex.getMessage());
            }
            result.add(new Registered(stored.name(), stored.source(), root, manifest));
        }
        return result;
    }

    /**
     * Finds a registered marketplace by name.
     * @param name the marketplace name.
     * @return the marketplace, or empty.
     */
    public Optional<Registered> find(String name) {
        return list().stream().filter(m -> m.name().equals(name)).findFirst();
    }

    /**
     * Returns every plugin entry across every marketplace, paired with its
     * marketplace name. Used to populate the Discover list.
     * @return the entries.
     */
    public List<CatalogEntry> catalog() {
        List<CatalogEntry> entries = new ArrayList<>();
        for (Registered marketplace : list()) {
            if (marketplace.manifest() == null) continue;
            for (PluginEntry entry : marketplace.manifest().plugins()) {
                entries.add(new CatalogEntry(marketplace.name(), marketplace.root(), entry));
            }
        }
        return entries;
    }

    /**
     * One catalog entry together with the marketplace it came from.
     * @param marketplace the marketplace name.
     * @param root the marketplace's resolved local root, for relative plugin paths.
     * @param entry the plugin entry.
     */
    public record CatalogEntry(String marketplace, Path root, PluginEntry entry) {
        /** @return the plugin id for this entry. */
        public PluginId id() {
            return new PluginId(entry.name(), marketplace);
        }

        /** @return the marketplace's local root. */
        public Path marketplaceRoot() {
            return root;
        }
    }

    /**
     * Finds one plugin entry by id.
     * @param id the plugin id.
     * @return the entry, or empty.
     */
    public Optional<CatalogEntry> entry(PluginId id) {
        return list().stream()
                .filter(m -> m.name().equals(id.marketplace()))
                .filter(m -> m.manifest() != null)
                .flatMap(m -> m.manifest().plugins().stream()
                        .filter(e -> e.name().equals(id.name()))
                        .map(e -> new CatalogEntry(id.marketplace(), m.root(), e)))
                .findFirst();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** The persisted form of one marketplace. */
    private record Stored(String name, String source, String root) { }

    private Path file() {
        return home.marketplacesFile();
    }

    private Map<String, Stored> load() {
        Map<String, Stored> map = new LinkedHashMap<>();
        if (!Files.isRegularFile(file())) {
            return map;
        }
        try {
            JsonNode root = Json.read(file());
            for (JsonNode node : Json.array(root, "marketplaces")) {
                String name = Json.text(node, "name");
                if (name != null) {
                    map.put(name, new Stored(name, Json.text(node, "source"), Json.text(node, "root")));
                }
            }
        } catch (IOException ex) {
            logger.warn("Could not read marketplace registry {}: {}", file(), ex.getMessage());
        }
        return map;
    }

    private void save(Map<String, Stored> map) throws IOException {
        ObjectNode root = Json.object();
        ArrayNode array = root.putArray("marketplaces");
        for (Stored stored : map.values()) {
            ObjectNode node = Json.object();
            node.put("name", stored.name());
            node.put("source", stored.source());
            node.put("root", stored.root());
            array.add(node);
        }
        Json.write(root, file());
    }
}
