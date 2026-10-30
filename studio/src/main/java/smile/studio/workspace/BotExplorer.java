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
package smile.studio.workspace;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import ioa.agent.Agent;
import ioa.agent.AgentCatalog;
import ioa.agent.bot.BotsConfig;

/**
 * Explorer sidebar tab for Agent Hub Bot personas and scheduled/event-driven Routines.
 *
 * @author Haifeng Li
 */
public class BotExplorer extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BotExplorer.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(KernelExplorer.class.getName(), Locale.getDefault());

    /** The hidden tree root node. */
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(bundle.getString("Root"));

    /** User object for a Bot persona tree node under {@link #root}. */
    public record BotTreeItem(BotsConfig.BotSpec bot, String title) {
        public BotTreeItem(BotsConfig.BotSpec bot) {
            this(bot, resolveBotTitle(bot));
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /**
     * Resolves the display title for a {@link BotsConfig.BotSpec} from the target
     * agent's {@code AGENT.md} specification when available.
     */
    static String resolveBotTitle(BotsConfig.BotSpec bot) {
        String persona = bot.persona();
        if ("*".equals(persona)) {
            return "★ " + bot.name();
        }
        try {
            return Agent.Spec.of(persona).title();
        } catch (Exception ignored) {
            try {
                for (AgentCatalog.Entry entry : AgentCatalog.load()) {
                    if (entry.spec().callName().equalsIgnoreCase(persona)
                            || entry.spec().callName().equalsIgnoreCase(bot.botId())
                            || entry.name().equalsIgnoreCase(bot.botId())) {
                        return entry.spec().title();
                    }
                }
            } catch (Exception ignored2) {
                // fall back to bot.name()
            }
        }
        return bot.name();
    }

    /** User object for a Routine tree node under a {@link BotTreeItem}. */
    public record RoutineTreeItem(BotsConfig.BotSpec bot, BotsConfig.RoutineSpec routine) {
        @Override
        public String toString() {
            String mark = (bot.enabled() && routine.enabled()) ? "● " : "○ ";
            return mark + routine.routineId() + " — " + routine.name();
        }
    }

    private final JTree tree = new JTree(root);
    private final DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();

    /**
     * Constructor.
     */
    public BotExplorer() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(0, 8, 0, 0));
        initTree();
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    private void initTree() {
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        ToolTipManager.sharedInstance().registerComponent(tree);
        refresh();

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object object = node.getUserObject();
                if (object instanceof BotTreeItem botItem) {
                    setIcon(null);
                    setText(botItem.toString());
                    setToolTipText(botItem.bot().description());
                } else if (object instanceof RoutineTreeItem routineItem) {
                    setText(routineItem.toString());
                    setToolTipText(routineItem.routine().trigger().source() + ": " + routineItem.routine().workflow().target());
                }
                return this;
            }
        };
        tree.setCellRenderer(renderer);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    TreePath treePath = tree.getPathForLocation(e.getX(), e.getY());
                    if (treePath == null) {
                        return;
                    }
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                    Object userObj = node.getUserObject();
                    Workspace ws = (Workspace) SwingUtilities.getAncestorOfClass(Workspace.class, BotExplorer.this);
                    if (ws == null) {
                        return;
                    }
                    if (userObj instanceof RoutineTreeItem routineItem) {
                        ws.openBotManager(routineItem.routine().routineId());
                    } else if (e.getClickCount() == 2 && userObj instanceof BotTreeItem) {
                        ws.openBotActivity(0);
                    }
                }
            }
        });
    }

    /**
     * Populates the {@link #root} subtree from {@code ${smile.home}/conf/bots.json}
     * and expands all Bot persona nodes.
     */
    public final void refresh() {
        root.removeAllChildren();
        try {
            BotsConfig cfg = BotsConfig.load();
            for (BotsConfig.BotSpec bot : cfg.bots()) {
                DefaultMutableTreeNode botTreeNode = new DefaultMutableTreeNode(new BotTreeItem(bot));
                for (BotsConfig.RoutineSpec routine : bot.routines()) {
                    botTreeNode.add(new DefaultMutableTreeNode(new RoutineTreeItem(bot, routine)));
                }
                root.add(botTreeNode);
            }
        } catch (Exception ex) {
            logger.debug("Could not populate Agent Hub tree nodes: {}", ex.getMessage());
        }
        treeModel.reload(root);
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void showContextMenu(MouseEvent e) {
        TreePath treePath = tree.getPathForLocation(e.getX(), e.getY());
        if (treePath == null) {
            return;
        }
        tree.setSelectionPath(treePath);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        Object userObj = node.getUserObject();

        JPopupMenu popup = new JPopupMenu();

        if (userObj instanceof BotTreeItem || userObj instanceof RoutineTreeItem) {
            if (userObj instanceof RoutineTreeItem item) {
                JMenuItem runItem = new JMenuItem("▶ Run Now (" + item.routine().routineId() + ")");
                runItem.addActionListener(ev -> Thread.ofPlatform().name("hub-tree-run").daemon(true).start(() -> {
                    try {
                        if (!HubManager.getInstance().isRunning()) {
                            HubManager.getInstance().start();
                        }
                        HubManager.getInstance().client().runRoutineNow(
                                item.bot().botId(), item.routine().routineId());
                    } catch (Exception ex) {
                        showError(ex.getMessage());
                    }
                }));
                popup.add(runItem);

                boolean currentlyEnabled = item.routine().enabled();
                JMenuItem toggleItem = new JMenuItem(currentlyEnabled ? "Disable Routine" : "Enable Routine");
                toggleItem.addActionListener(ev -> Thread.ofPlatform().name("hub-tree-toggle").daemon(true).start(() -> {
                    try {
                        HubManager.getInstance().client().setRoutineEnabled(
                                item.bot().botId(), item.routine().routineId(), !currentlyEnabled);
                        SwingUtilities.invokeLater(this::refresh);
                    } catch (Exception ex) {
                        showError(ex.getMessage());
                    }
                }));
                popup.add(toggleItem);

                JMenuItem editItem = new JMenuItem("Configure Routine…");
                editItem.addActionListener(ev -> {
                    Workspace ws = (Workspace) SwingUtilities.getAncestorOfClass(Workspace.class, BotExplorer.this);
                    if (ws != null) {
                        ws.openBotManager(item.routine().routineId());
                    }
                });
                popup.add(editItem);
                popup.addSeparator();
            }

            JMenuItem manageBotsItem = new JMenuItem("Bot Routines…");
            manageBotsItem.addActionListener(ev -> {
                Workspace ws = (Workspace) SwingUtilities.getAncestorOfClass(Workspace.class, BotExplorer.this);
                if (ws != null) {
                    ws.openBotManager(null);
                }
            });
            popup.add(manageBotsItem);

            JMenuItem activityItem = new JMenuItem("Bot Activities…");
            activityItem.addActionListener(ev -> {
                Workspace ws = (Workspace) SwingUtilities.getAncestorOfClass(Workspace.class, BotExplorer.this);
                if (ws != null) {
                    ws.openBotActivity(0);
                }
            });
            popup.add(activityItem);
            popup.addSeparator();

            boolean running = HubManager.getInstance().isRunning();
            JMenuItem startStopItem = new JMenuItem(running ? "Stop Agent Hub" : "Start Agent Hub");
            startStopItem.addActionListener(ev -> Thread.ofPlatform().name("hub-tree-startstop").daemon(true).start(() -> {
                if (running) {
                    HubManager.getInstance().stop();
                } else {
                    if (!HubManager.getInstance().start()) {
                        showError(bundle.getString("HubStartFailed"));
                    }
                }
                SwingUtilities.invokeLater(this::refresh);
            }));
            popup.add(startStopItem);

            popup.show(tree, e.getX(), e.getY());
        }
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(BotExplorer.this),
                message,
                bundle.getString("Error"),
                JOptionPane.ERROR_MESSAGE));
    }
}
