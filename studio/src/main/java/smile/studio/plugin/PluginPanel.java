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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * The {@code /plugin} panel: a Swing dialog over {@link PluginService}, mirroring
 * the Claude Code {@code /plugin} surface.
 *
 * <p>Four tabs — <b>Discover</b> (the catalog of every registered marketplace),
 * <b>Installed</b> (enable/disable/uninstall and the per-server MCP opt-in),
 * <b>Marketplaces</b> (add/remove), and <b>Errors</b> (every dropped or degraded
 * component, so nothing is silent). Install runs off the event thread; the panel is
 * a view over the service and never writes state itself.
 *
 * @author Haifeng Li
 */
public final class PluginPanel extends JDialog {
    private static final ResourceBundle bundle =
            ResourceBundle.getBundle("smile.studio.plugin.Plugin", Locale.getDefault());

    private final transient PluginService service;

    private final DefaultListModel<CatalogRow> discoverModel = new DefaultListModel<>();
    private final JList<CatalogRow> discoverList = new JList<>(discoverModel);
    private final DefaultListModel<InstalledRow> installedModel = new DefaultListModel<>();
    private final JList<InstalledRow> installedList = new JList<>(installedModel);
    private final DefaultListModel<String> marketplaceModel = new DefaultListModel<>();
    private final JList<String> marketplaceList = new JList<>(marketplaceModel);
    private final JLabel discoverHint = new JLabel();
    private final JTextArea details = new JTextArea(8, 40);
    private final JTextArea errors = new JTextArea(16, 60);

    /**
     * Constructor.
     * @param owner the owning frame.
     * @param cwd the project working directory.
     */
    public PluginPanel(Frame owner, Path cwd) {
        super(owner, bundle.getString("Plugins"), true);
        this.service = new PluginService(cwd);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        errors.setEditable(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(bundle.getString("Discover"), createDiscover());
        tabs.addTab(bundle.getString("Installed"), createInstalled());
        tabs.addTab(bundle.getString("Marketplaces"), createMarketplaces());
        tabs.addTab(bundle.getString("Errors"), new JScrollPane(errors));
        add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton(bundle.getString("Close"));
        close.addActionListener(e -> dispose());
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        refresh();
        setSize(new Dimension(820, 560));
        setLocationRelativeTo(owner);

        // Seed the built-in marketplaces (the official Anthropic catalog by default)
        // on a background thread so the dialog paints immediately. Seeding is
        // one-time and only touches the network if a known marketplace is missing.
        seedDefaultMarketplaces();
    }

    // ------------------------------------------------------------------
    // Discover
    // ------------------------------------------------------------------

    private JPanel createDiscover() {
        discoverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        discoverList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && discoverList.getSelectedValue() != null) {
                showCatalogDetails(discoverList.getSelectedValue());
            }
        });

        JButton install = new JButton(bundle.getString("Install"));
        install.addActionListener(e -> installSelected());

        discoverHint.setForeground(java.awt.Color.GRAY);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(discoverList), new JScrollPane(details));
        split.setResizeWeight(0.5);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(install);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(discoverHint, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Seeds the built-in marketplaces off the event thread. The service add may
     * fetch over the network (a git clone of the Anthropic marketplace on first
     * run), so it must not block the EDT. When something was seeded, the lists are
     * reloaded so the Discover tab fills in without the user reopening the dialog.
     */
    private void seedDefaultMarketplaces() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return service.ensureDefaultMarketplaces();
            }

            @Override
            protected void done() {
                try {
                    refresh();
                } catch (Exception ex) {
                    // refresh() is defensive; nothing actionable here.
                }
            }
        }.execute();
    }

    private void showCatalogDetails(CatalogRow row) {
        var entry = row.entry.entry();
        StringBuilder sb = new StringBuilder();
        sb.append(entry.label()).append('\n');
        sb.append(row.marketplace).append(" / ").append(entry.name()).append('\n');
        if (entry.description() != null) sb.append('\n').append(entry.description()).append('\n');
        if (entry.version() != null) sb.append("\nversion: ").append(entry.version());
        sb.append("\nsource: ").append(entry.source().typeName());
        if (entry.source().isRefused()) {
            sb.append("  [refused: Studio never runs a command source]");
        }
        sb.append("\n\nInstalling adds its skills, subagents, and MCP servers as ioa content.");
        details.setText(sb.toString());
        details.setCaretPosition(0);
    }

    private void installSelected() {
        CatalogRow row = discoverList.getSelectedValue();
        if (row == null) {
            return;
        }
        var entry = row.entry.entry();
        InstallResult result = service.install(row.entry.id(), PluginScope.USER);
        report(entry.label(), result);
        refresh();
    }

    // ------------------------------------------------------------------
    // Installed
    // ------------------------------------------------------------------

    private JPanel createInstalled() {
        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && installedList.getSelectedValue() != null) {
                showInstalledDetails(installedList.getSelectedValue());
            }
        });

        JButton toggle = new JButton(bundle.getString("Enable") + " / " + bundle.getString("Disable"));
        toggle.addActionListener(e -> toggleSelected());
        JButton uninstall = new JButton(bundle.getString("Uninstall"));
        uninstall.addActionListener(e -> uninstallSelected());
        JButton mcpDefault = new JButton(bundle.getString("MCP"));
        mcpDefault.addActionListener(e -> toggleMcpDefault());
        JButton mcpServers = new JButton(bundle.getString("MCPServers"));
        mcpServers.addActionListener(e -> configureMcpServers());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(installedList), new JScrollPane(details));
        split.setResizeWeight(0.4);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(toggle);
        south.add(mcpDefault);
        south.add(mcpServers);
        south.add(uninstall);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void showInstalledDetails(InstalledRow row) {
        var plugin = row.plugin;
        StringBuilder sb = new StringBuilder();
        sb.append(plugin.id()).append("  ").append(plugin.version()).append('\n');
        sb.append(service.state().isEnabled(plugin.id()) ? "enabled" : "disabled").append("\n\n");
        if (!plugin.skills().isEmpty()) sb.append("skills: ").append(String.join(", ", plugin.skills())).append('\n');
        if (!plugin.agents().isEmpty()) {
            sb.append("subagents: ").append(String.join(", ", plugin.agents()))
              .append("  (not discoverable until ioa advertises user-defined subagents)\n");
        }
        if (!plugin.mcpServers().isEmpty()) {
            sb.append("\nMCP servers (opt-in, per server):\n");
            for (String server : plugin.mcpServers()) {
                sb.append("  ").append(server).append(": ")
                  .append(service.state().mcpEnabled(plugin.id(), server) ? "on" : "off").append('\n');
            }
        }
        details.setText(sb.toString());
        details.setCaretPosition(0);
    }

    private void toggleSelected() {
        InstalledRow row = installedList.getSelectedValue();
        if (row == null) return;
        boolean enabled = service.state().isEnabled(row.plugin.id());
        report(row.plugin.id().toString(), service.setEnabled(row.plugin.id(), !enabled, PluginScope.USER));
        refresh();
    }

    private void uninstallSelected() {
        InstalledRow row = installedList.getSelectedValue();
        if (row == null) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Uninstall " + row.plugin.id() + "?", bundle.getString("Uninstall"),
                JOptionPane.OK_CANCEL_OPTION);
        if (choice == JOptionPane.OK_OPTION) {
            report(row.plugin.id().toString(), service.uninstall(row.plugin.id()));
            refresh();
        }
    }

    private void toggleMcpDefault() {
        InstalledRow row = installedList.getSelectedValue();
        if (row == null) return;
        boolean current = service.state().mcpDefault(row.plugin.id());
        report(row.plugin.id().toString(), service.setMcpDefault(row.plugin.id(), !current));
        refresh();
    }

    private void configureMcpServers() {
        InstalledRow row = installedList.getSelectedValue();
        if (row == null || row.plugin.mcpServers().isEmpty()) {
            return;
        }
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        int y = 0;
        for (String server : row.plugin.mcpServers()) {
            JCheckBox box = new JCheckBox(server, service.state().mcpEnabled(row.plugin.id(), server));
            gbc.gridy = y++;
            pane.add(box, gbc);
            box.addActionListener(e ->
                    service.setMcpServer(row.plugin.id(), server, box.isSelected()));
        }
        int choice = JOptionPane.showConfirmDialog(this, pane,
                bundle.getString("MCPServers"), JOptionPane.OK_CANCEL_OPTION);
        if (choice == JOptionPane.OK_OPTION) {
            refresh();
        }
    }

    // ------------------------------------------------------------------
    // Marketplaces
    // ------------------------------------------------------------------

    private JPanel createMarketplaces() {
        JTextField source = new JTextField(30);
        JButton add = new JButton(bundle.getString("AddMarketplace"));
        add.addActionListener(e -> {
            String text = source.getText().trim();
            if (!text.isEmpty()) {
                report(text, service.addMarketplace(text));
                source.setText("");
                refresh();
            }
        });
        JButton remove = new JButton(bundle.getString("Remove"));
        remove.addActionListener(e -> {
            String name = marketplaceList.getSelectedValue();
            if (name != null) {
                report(name, service.removeMarketplace(name));
                refresh();
            }
        });

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel(bundle.getString("Source")));
        north.add(source);
        north.add(add);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(remove);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(north, BorderLayout.NORTH);
        panel.add(new JScrollPane(marketplaceList), BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    // ------------------------------------------------------------------
    // Refresh and reporting
    // ------------------------------------------------------------------

    /** Reloads every tab from the service on the event thread. */
    private void refresh() {
        discoverModel.clear();
        for (var entry : service.catalog()) {
            discoverModel.addElement(new CatalogRow(entry.marketplace(), entry));
        }
        updateDiscoverHint();

        installedModel.clear();
        for (var plugin : service.installed()) {
            installedModel.addElement(new InstalledRow(plugin));
        }

        marketplaceModel.clear();
        for (var marketplace : service.marketplaces()) {
            marketplaceModel.addElement(marketplace.name());
        }

        StringBuilder sb = new StringBuilder();
        for (var plugin : service.installed()) {
            for (ComponentDisposition disposition : plugin.dispositions()) {
                if (disposition.isDropped() || disposition.isDegraded()) {
                    sb.append(plugin.id()).append("  ")
                      .append(disposition.disposition().name().toLowerCase(Locale.ROOT)).append(' ')
                      .append(disposition.kind()).append(" '").append(disposition.name())
                      .append("': ").append(disposition.reason()).append('\n');
                }
            }
        }
        errors.setText(sb.isEmpty() ? "No dropped or degraded components." : sb.toString());
    }

    /**
     * Shows a hint above the Discover list when it is empty, so a first-run user
     * with no marketplace knows how to get one instead of staring at a blank list.
     */
    private void updateDiscoverHint() {
        if (discoverModel.isEmpty()) {
            discoverHint.setText(bundle.getString("DiscoverHint"));
        } else {
            discoverHint.setText(" ");
        }
    }

    /** Shows an operation's result, listing any caveats. */
    private void report(String title, InstallResult result) {
        StringBuilder message = new StringBuilder(result.message());
        for (ComponentDisposition caveat : result.caveats()) {
            message.append('\n').append("  - ").append(caveat.disposition().name().toLowerCase(Locale.ROOT))
                   .append(' ').append(caveat.kind()).append(" '").append(caveat.name())
                   .append("': ").append(caveat.reason());
        }
        JOptionPane.showMessageDialog(this, message.toString(), title,
                result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Installs on a background thread, reporting on the event thread. Kept for
     * callers that already know the id (the CLI path is synchronous and does not
     * use this).
     *
     * @param id the plugin id.
     */
    void installAsync(PluginId id) {
        new SwingWorker<InstallResult, Void>() {
            @Override
            protected InstallResult doInBackground() {
                return service.install(id, PluginScope.USER);
            }

            @Override
            protected void done() {
                try {
                    report(id.toString(), get());
                } catch (Exception ex) {
                    report(id.toString(), InstallResult.failed(ex.getMessage()));
                }
                SwingUtilities.invokeLater(PluginPanel.this::refresh);
            }
        }.execute();
    }

    /** One row in the Discover list: a catalog entry with its marketplace name. */
    private record CatalogRow(String marketplace, MarketplaceRegistry.CatalogEntry entry) {
        @Override
        public String toString() {
            return entry.entry().label() + "  (" + marketplace + ")";
        }
    }

    /** One row in the Installed list. */
    private record InstalledRow(InstalledPlugin plugin) {
        @Override
        public String toString() {
            return plugin.id() + "  " + plugin.version();
        }
    }
}
