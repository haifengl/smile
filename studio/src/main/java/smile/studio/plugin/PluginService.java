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
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The facade the UI and the CLI both drive. Every operation returns text meant for
 * the user, so the Swing panel and {@code smile plugin} share one implementation and
 * cannot diverge in behavior or wording.
 *
 * <p>Command surface, mirroring {@code claude plugin}:
 * <pre>
 * marketplace add &lt;source&gt; | marketplace list | marketplace remove &lt;name&gt;
 * install &lt;name@marketplace&gt; [--scope user|project|local]
 * list
 * enable &lt;id&gt; | disable &lt;id&gt; | uninstall &lt;id&gt;
 * mcp &lt;id&gt; &lt;server&gt; on|off
 * </pre>
 *
 * @author Haifeng Li
 */
public final class PluginService {
    private final PluginHome home;
    private final MarketplaceRegistry registry;
    private final PluginStateStore state;
    private final PluginInstaller installer;
    private final Path cwd;

    /**
     * Constructor.
     * @param cwd the project working directory.
     */
    public PluginService(Path cwd) {
        this(new PluginHome(), cwd, Path.of(System.getProperty("user.home")));
    }

    /**
     * Constructor with an explicit plugin home and state home, for tests.
     * @param home the plugin home.
     * @param cwd the project working directory.
     * @param userHome the user home for the user scope.
     */
    PluginService(PluginHome home, Path cwd, Path userHome) {
        this.cwd = cwd;
        this.home = home;
        this.registry = new MarketplaceRegistry(home);
        this.state = new PluginStateStore(cwd, userHome);
        this.installer = new PluginInstaller(home, registry, state, cwd);
    }

    /**
     * Executes a {@code /plugin} subcommand and returns the text to show.
     *
     * @param args the arguments after {@code /plugin}.
     * @return the output text, never null.
     */
    public String execute(List<String> args) {
        if (args.isEmpty() || args.getFirst().isBlank()) {
            return usage();
        }
        String command = args.getFirst().toLowerCase(Locale.ROOT);
        List<String> rest = args.subList(1, args.size());
        try {
            return switch (command) {
                case "marketplace", "market" -> marketplace(rest);
                case "install" -> install(rest);
                case "list" -> list();
                case "enable" -> setEnabled(rest, true);
                case "disable" -> setEnabled(rest, false);
                case "uninstall", "remove" -> uninstall(rest);
                case "mcp" -> mcp(rest);
                case "help" -> usage();
                default -> "Unknown /plugin subcommand: " + command + "\n\n" + usage();
            };
        } catch (IllegalArgumentException ex) {
            return "Error: " + ex.getMessage();
        } catch (IOException ex) {
            return "Error: " + ex.getMessage();
        }
    }

    /**
     * Lists the catalog across every registered marketplace, for the Discover tab.
     * @return the catalog entries.
     */
    public List<MarketplaceRegistry.CatalogEntry> catalog() {
        return registry.catalog();
    }

    /**
     * Lists registered marketplaces, for the Marketplaces tab.
     * @return the marketplaces.
     */
    public List<MarketplaceRegistry.Registered> marketplaces() {
        return registry.list();
    }

    /**
     * Lists installed plugins, for the Installed tab.
     * @return the installed plugins.
     */
    public List<InstalledPlugin> installed() {
        return List.copyOf(installer.index().load().values());
    }

    /**
     * Returns the state store, for the panel's MCP toggles.
     * @return the state store.
     */
    public PluginStateStore state() {
        return state;
    }

    /**
     * Installs a plugin and returns the result, for the panel's Install action.
     * @param id the plugin id.
     * @param scope the scope.
     * @return the result.
     */
    public InstallResult install(PluginId id, PluginScope scope) {
        return installer.install(id, scope);
    }

    /**
     * Enables or disables a plugin, for the panel.
     * @param id the plugin id.
     * @param enabled the new state.
     * @param scope the scope.
     * @return the result.
     */
    public InstallResult setEnabled(PluginId id, boolean enabled, PluginScope scope) {
        return installer.setEnabled(id, enabled, scope);
    }

    /**
     * Uninstalls a plugin, for the panel.
     * @param id the plugin id.
     * @return the result.
     */
    public InstallResult uninstall(PluginId id) {
        return installer.uninstall(id);
    }

    /**
     * Sets one MCP server's opt-in, for the panel.
     * @param id the plugin id.
     * @param server the server's local name.
     * @param enabled the opt-in value.
     * @return the result.
     */
    public InstallResult setMcpServer(PluginId id, String server, boolean enabled) {
        try {
            PluginScope scope = state.mcpScope(id);
            state.setMcpServerEnabled(id, server, enabled, scope);
            return InstallResult.ok((enabled ? "Enabled" : "Disabled") + " MCP server "
                    + server + " of " + id + " (restart Studio to connect)");
        } catch (IOException ex) {
            return InstallResult.failed("Failed to update MCP opt-in: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Panel operations
    // ------------------------------------------------------------------

    /**
     * Returns the working directory this service resolves project/local scope against.
     * @return the project working directory.
     */
    public Path cwd() {
        return cwd;
    }

    /**
     * Adds a marketplace and returns the raw result, for the panel to refresh on.
     * @param source the source string.
     * @return the result.
     */
    public InstallResult addMarketplace(String source) {
        try {
            var registered = registry.add(source);
            return InstallResult.ok("Added marketplace '" + registered.name() + "' ("
                    + registered.manifest().plugins().size() + " plugins).");
        } catch (IOException ex) {
            return InstallResult.failed("Failed to add marketplace: " + ex.getMessage());
        }
    }

    /**
     * Removes a marketplace and returns the raw result.
     * @param name the marketplace name.
     * @return the result.
     */
    public InstallResult removeMarketplace(String name) {
        try {
            registry.remove(name);
            return InstallResult.ok("Removed marketplace '" + name + "'.");
        } catch (IOException ex) {
            return InstallResult.failed("Failed to remove marketplace: " + ex.getMessage());
        }
    }

    /**
     * Sets a plugin's per-plugin MCP default, for the panel's plugin-level switch.
     * @param id the plugin id.
     * @param enabled the default for servers without an override.
     * @return the result.
     */
    public InstallResult setMcpDefault(PluginId id, boolean enabled) {
        try {
            PluginScope scope = state.mcpScope(id);
            state.setMcpEnabled(id, enabled, scope);
            return InstallResult.ok((enabled ? "Enabled" : "Disabled")
                    + " MCP for " + id + " (restart Studio to connect)");
        } catch (IOException ex) {
            return InstallResult.failed("Failed to update MCP default: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Subcommands
    // ------------------------------------------------------------------

    private String marketplace(List<String> args) throws IOException {
        if (args.isEmpty()) {
            return "Usage: /plugin marketplace [add <source> | list | remove <name>]";
        }
        return switch (args.getFirst().toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.size() < 2) yield "Usage: /plugin marketplace add <source>";
                var registered = registry.add(args.get(1));
                yield "Added marketplace '" + registered.name() + "' ("
                        + registered.manifest().plugins().size() + " plugins).";
            }
            case "list" -> marketplaceList();
            case "remove" -> {
                if (args.size() < 2) yield "Usage: /plugin marketplace remove <name>";
                registry.remove(args.get(1));
                yield "Removed marketplace '" + args.get(1) + "'.";
            }
            default -> "Usage: /plugin marketplace [add <source> | list | remove <name>]";
        };
    }

    private String marketplaceList() {
        var marketplaces = registry.list();
        if (marketplaces.isEmpty()) {
            return "No marketplaces registered. Add one with /plugin marketplace add <source>.";
        }
        StringBuilder sb = new StringBuilder("Registered marketplaces:\n");
        for (var marketplace : marketplaces) {
            sb.append("  ").append(marketplace.name()).append("  (")
              .append(marketplace.sourceText()).append(')');
            if (marketplace.manifest() == null) {
                sb.append("  [manifest unreadable]");
            } else {
                sb.append("  ").append(marketplace.manifest().plugins().size()).append(" plugins");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String install(List<String> args) {
        if (args.isEmpty()) {
            return "Usage: /plugin install <name@marketplace> [--scope user|project|local]";
        }
        PluginId id = PluginId.parse(args.getFirst());
        PluginScope scope = scope(args);
        InstallResult result = installer.install(id, scope);
        return render(result);
    }

    private String list() {
        var installed = installed();
        if (installed.isEmpty()) {
            return "No plugins installed.";
        }
        StringBuilder sb = new StringBuilder("Installed plugins:\n");
        for (InstalledPlugin plugin : installed) {
            boolean enabled = state.isEnabled(plugin.id());
            sb.append("  ").append(plugin.id())
              .append("  ").append(plugin.version())
              .append(enabled ? "  enabled" : "  disabled").append('\n');
        }
        return sb.toString();
    }

    private String setEnabled(List<String> args, boolean enabled) {
        if (args.isEmpty()) {
            return "Usage: /plugin " + (enabled ? "enable" : "disable") + " <id>";
        }
        PluginId id = PluginId.parse(args.getFirst());
        PluginScope scope = scope(args);
        return render(installer.setEnabled(id, enabled, scope));
    }

    private String uninstall(List<String> args) {
        if (args.isEmpty()) {
            return "Usage: /plugin uninstall <id>";
        }
        return render(installer.uninstall(PluginId.parse(args.getFirst())));
    }

    private String mcp(List<String> args) {
        if (args.size() < 3) {
            return "Usage: /plugin mcp <id> <server> on|off";
        }
        PluginId id = PluginId.parse(args.get(0));
        String server = args.get(1);
        String action = args.get(2).toLowerCase(Locale.ROOT);
        boolean enabled = action.equals("on") || action.equals("true");
        return render(setMcpServer(id, server, enabled));
    }

    private PluginScope scope(List<String> args) {
        for (int i = 0; i < args.size() - 1; i++) {
            if (args.get(i).equals("--scope")) {
                return switch (args.get(i + 1).toLowerCase(Locale.ROOT)) {
                    case "user" -> PluginScope.USER;
                    case "project" -> PluginScope.PROJECT;
                    case "local" -> PluginScope.LOCAL;
                    default -> throw new IllegalArgumentException("Unknown scope: " + args.get(i + 1));
                };
            }
        }
        return PluginScope.USER;
    }

    private String render(InstallResult result) {
        StringBuilder sb = new StringBuilder(result.message());
        if (result.success()) {
            for (ComponentDisposition caveat : result.caveats()) {
                sb.append("\n  - ").append(caveat.disposition().name().toLowerCase(Locale.ROOT))
                  .append(' ').append(caveat.kind()).append(" '").append(caveat.name())
                  .append("': ").append(caveat.reason());
            }
            sb.append("\nRestart Studio to activate changes.");
        }
        return sb.toString();
    }

    private String usage() {
        return """
                Plugin commands:
                  /plugin marketplace add <source>      Register a marketplace (path, owner/repo, or URL)
                  /plugin marketplace list              List registered marketplaces
                  /plugin marketplace remove <name>     Remove a marketplace
                  /plugin install <id> [--scope ...]    Install a plugin (user|project|local)
                  /plugin list                          List installed plugins
                  /plugin enable <id>                   Enable an installed plugin
                  /plugin disable <id>                  Disable a plugin
                  /plugin uninstall <id>                Uninstall a plugin
                  /plugin mcp <id> <server> on|off      Opt an MCP server in or out
                A plugin id looks like name@marketplace, e.g. commit-commands@claude-plugins-official.""";
    }
}
