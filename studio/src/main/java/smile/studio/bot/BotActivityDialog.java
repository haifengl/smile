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
package smile.studio.bot;

import java.awt.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import smile.studio.workspace.HubClient;
import smile.studio.workspace.HubManager;

/**
 * Operations &amp; Review workspace panel for Agent Hub:
 * <ol>
 *   <li><b>Approvals &amp; Batch Draft Queue</b>: inspect pending questions, tool confirmations,
 *       staged multi-channel drafts, and Secure Handoffs; view prior-draft diffs; edit inline;
 *       and resolve individually ({@code Allow Once}, {@code Always Allow Rule},
 *       {@code Edit & Approve}, {@code Deny}) or in batch ({@code Approve Selected Batch}).</li>
 *   <li><b>Run History &amp; Transcripts</b>: inspect {@code IoABotRun} execution history and
 *       view full session transcripts.</li>
 *   <li><b>Interactive Bot Pages</b>: preview and launch HTML dashboards generated in
 *       {@code ${smile.home}/bot/pages/}.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class BotActivityDialog extends JPanel {
    private final JLabel hubStatusLabel = new JLabel("Checking Agent Hub…");
    private final JTabbedPane tabs = new JTabbedPane();

    // Tab 1: Approvals & Batch Draft Queue
    private final JComboBox<String> approvalFilterCombo = new JComboBox<>(new String[]{
            "PENDING", "ALL"
    });
    private final DefaultTableModel approvalsModel = new DefaultTableModel(
            new Object[]{"Select", "ID", "Kind", "Persona", "Routine", "Channel", "Recipient / Target", "Subject", "Status", "Expires"}, 0) {
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }
    };
    private final JTable approvalsTable = new JTable(approvalsModel);
    private List<HubClient.BotApprovalItem> currentApprovals = new ArrayList<>();
    private final JTextArea priorDraftArea = new JTextArea(7, 30);
    private final JTextArea editablePayloadArea = new JTextArea(7, 30);
    private final JTextField rulePatternField = new JTextField(20);

    // Tab 2: Run History & Transcripts
    private final DefaultTableModel runsModel = new DefaultTableModel(
            new Object[]{"Run ID", "Bot", "Routine", "Persona", "Model", "Status", "Started At", "Summary / Error"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable runsTable = new JTable(runsModel);
    private List<HubClient.BotRunSummary> currentRuns = new ArrayList<>();
    private final JTextArea transcriptArea = new JTextArea(10, 60);

    // Tab 3: Bot Pages
    private final DefaultListModel<HubClient.BotPageSummary> pagesListModel = new DefaultListModel<>();
    private final JList<HubClient.BotPageSummary> pagesList = new JList<>(pagesListModel);
    private final JEditorPane pagePreviewPane = new JEditorPane("text/html", "");

    /**
     * Creates the Bot Activity, Approvals &amp; Pages workspace panel.
     *
     * @param initialTab initial tab index (`0` = Approvals, `1` = Runs, `2` = Pages).
     */
    public BotActivityDialog(int initialTab) {
        super(new BorderLayout(8, 8));

        initTopBar();
        tabs.addTab("Approvals & Batch Draft Queue", buildApprovalsTab());
        tabs.addTab("Run History & Transcripts", buildRunsTab());
        tabs.addTab("Interactive Bot Pages", buildPagesTab());
        selectTab(initialTab);
        add(tabs, BorderLayout.CENTER);

        refreshAllAsync();
    }

    /**
     * Selects the given sub-tab index if valid.
     *
     * @param tabIndex tab index (`0` = Approvals, `1` = Runs, `2` = Pages).
     */
    public final void selectTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabs.getTabCount()) {
            tabs.setSelectedIndex(tabIndex);
        }
    }

    private void initTopBar() {
        JPanel top = new JPanel(new BorderLayout(8, 4));
        top.setBorder(new EmptyBorder(8, 10, 0, 10));

        hubStatusLabel.setFont(hubStatusLabel.getFont().deriveFont(Font.BOLD));
        top.add(hubStatusLabel, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton startHubBtn = new JButton("Start / Attach Hub");
        startHubBtn.addActionListener(e -> Thread.ofPlatform().name("hub-ui-start").daemon(true).start(() -> {
            HubManager.getInstance().start();
            SwingUtilities.invokeLater(this::refreshAllAsync);
        }));
        JButton configBtn = new JButton("Configure Bot Routines…");
        configBtn.addActionListener(e -> {
            BotManagerDialog dlg = new BotManagerDialog(
                    SwingUtilities.getWindowAncestor(this), null, this::refreshAllAsync);
            dlg.setVisible(true);
        });
        JButton refreshBtn = new JButton("↻ Refresh");
        refreshBtn.addActionListener(e -> refreshAllAsync());

        actions.add(startHubBtn);
        actions.add(configBtn);
        actions.add(refreshBtn);
        top.add(actions, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
    }

    private JComponent buildApprovalsTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.add(new JLabel("Filter Status:"));
        approvalFilterCombo.addActionListener(e -> refreshApprovalsAsync());
        filterBar.add(approvalFilterCombo);

        JButton selectAllDraftsBtn = new JButton("Select All Pending Drafts");
        selectAllDraftsBtn.addActionListener(e -> {
            for (int i = 0; i < approvalsModel.getRowCount(); i++) {
                approvalsModel.setValueAt(Boolean.TRUE, i, 0);
            }
        });
        filterBar.add(selectAllDraftsBtn);
        panel.add(filterBar, BorderLayout.NORTH);

        approvalsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        approvalsTable.getColumnModel().getColumn(0).setMaxWidth(55);
        approvalsTable.getColumnModel().getColumn(1).setMaxWidth(60);
        approvalsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = approvalsTable.getSelectedRow();
                if (row >= 0 && row < currentApprovals.size()) {
                    HubClient.BotApprovalItem item = currentApprovals.get(row);
                    priorDraftArea.setText(item.previousPayloadJson() == null || item.previousPayloadJson().isBlank()
                            ? "(No previous version — initial draft/request)"
                            : item.previousPayloadJson());
                    editablePayloadArea.setText(item.payloadJson() == null ? "" : item.payloadJson());
                    rulePatternField.setText(item.subject() == null ? "*" : item.subject());
                }
            }
        });

        priorDraftArea.setEditable(false);
        priorDraftArea.setLineWrap(true);
        priorDraftArea.setWrapStyleWord(true);
        editablePayloadArea.setLineWrap(true);
        editablePayloadArea.setWrapStyleWord(true);

        JPanel diffLeft = new JPanel(new BorderLayout(4, 4));
        diffLeft.add(new JLabel("Previous Draft / Prior Payload (Diff Baseline):"), BorderLayout.NORTH);
        diffLeft.add(new JScrollPane(priorDraftArea), BorderLayout.CENTER);

        JPanel diffRight = new JPanel(new BorderLayout(4, 4));
        diffRight.add(new JLabel("Current / Editable Draft Payload (JSON or Text):"), BorderLayout.NORTH);
        diffRight.add(new JScrollPane(editablePayloadArea), BorderLayout.CENTER);

        JSplitPane diffSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, diffLeft, diffRight);
        diffSplit.setResizeWeight(0.45);

        JPanel detailAndActions = new JPanel(new BorderLayout(6, 6));
        detailAndActions.add(diffSplit, BorderLayout.CENTER);

        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton allowOnceBtn = new JButton("✓ Allow Once");
        allowOnceBtn.addActionListener(e -> resolveSelectedSingle("ALLOW_ONCE"));

        JButton editApproveBtn = new JButton("✎ Edit & Approve");
        editApproveBtn.addActionListener(e -> resolveSelectedSingle("EDIT_AND_APPROVE"));

        JButton alwaysAllowBtn = new JButton("★ Always Allow Rule");
        alwaysAllowBtn.addActionListener(e -> resolveSelectedSingle("ALWAYS_ALLOW_RULE"));

        JButton denyBtn = new JButton("✕ Deny");
        denyBtn.addActionListener(e -> resolveSelectedSingle("DENY"));

        JButton batchApproveBtn = new JButton("✓✓ Approve Checked Batch");
        batchApproveBtn.addActionListener(e -> batchResolveChecked("ALLOW_ONCE"));

        actionBar.add(allowOnceBtn);
        actionBar.add(editApproveBtn);
        actionBar.add(alwaysAllowBtn);
        actionBar.add(new JLabel("Rule Pattern:"));
        actionBar.add(rulePatternField);
        actionBar.add(denyBtn);
        actionBar.add(batchApproveBtn);
        detailAndActions.add(actionBar, BorderLayout.SOUTH);

        JSplitPane verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(approvalsTable),
                detailAndActions
        );
        verticalSplit.setDividerLocation(260);
        panel.add(verticalSplit, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildRunsTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        runsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = runsTable.getSelectedRow();
                if (row >= 0 && row < currentRuns.size()) {
                    loadRunTranscriptAsync(currentRuns.get(row));
                }
            }
        });

        transcriptArea.setEditable(false);
        transcriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        transcriptArea.setLineWrap(true);
        transcriptArea.setWrapStyleWord(true);

        JPanel transcriptPanel = new JPanel(new BorderLayout(4, 4));
        JPanel transcriptTop = new JPanel(new BorderLayout());
        transcriptTop.add(new JLabel("Session Transcript (conversation.jsonl):"), BorderLayout.WEST);
        JButton cancelRunBtn = new JButton("Stop / Cancel Selected Run");
        cancelRunBtn.addActionListener(e -> cancelSelectedRun());
        transcriptTop.add(cancelRunBtn, BorderLayout.EAST);
        transcriptPanel.add(transcriptTop, BorderLayout.NORTH);
        transcriptPanel.add(new JScrollPane(transcriptArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(runsTable),
                transcriptPanel
        );
        split.setDividerLocation(280);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildPagesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        pagesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pagesList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof HubClient.BotPageSummary p) {
                    setText(p.title() + " (" + p.slug() + ".html)");
                }
                return this;
            }
        });
        pagesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                HubClient.BotPageSummary selected = pagesList.getSelectedValue();
                if (selected != null) {
                    loadPagePreviewAsync(selected);
                }
            }
        });

        pagePreviewPane.setEditable(false);

        JPanel rightPane = new JPanel(new BorderLayout(4, 4));
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton openBrowserBtn = new JButton("Open Page in Browser");
        openBrowserBtn.addActionListener(e -> openSelectedPageInBrowser());
        topBar.add(openBrowserBtn);
        rightPane.add(topBar, BorderLayout.NORTH);
        rightPane.add(new JScrollPane(pagePreviewPane), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(pagesList),
                rightPane
        );
        split.setDividerLocation(280);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    /** Refreshes Hub status, approvals, runs, and pages in a background thread. */
    public final void refreshAllAsync() {
        Thread.ofPlatform().name("bot-activity-refresh").daemon(true).start(() -> {
            HubClient client = HubManager.getInstance().client();
            boolean running = client.isReady();
            String filter = "PENDING".equals(approvalFilterCombo.getSelectedItem()) ? "PENDING" : null;
            List<HubClient.BotApprovalItem> approvals = running ? client.listApprovals(filter, 100) : List.of();
            List<HubClient.BotRunSummary> runs = running ? client.listRuns(null, null, 100) : List.of();
            List<HubClient.BotPageSummary> pages = running ? client.listPages() : loadOfflinePages();

            SwingUtilities.invokeLater(() -> {
                hubStatusLabel.setText(running
                        ? "● Agent Hub Running (" + client.baseUrl() + ") — Pending Approvals: " + approvals.stream().filter(a -> "PENDING".equals(a.status())).count()
                        : "○ Agent Hub Offline (" + client.baseUrl() + ")");
                populateApprovals(approvals);
                populateRuns(runs);
                populatePages(pages);
            });
        });
    }

    private void refreshApprovalsAsync() {
        Thread.ofPlatform().name("bot-approvals-refresh").daemon(true).start(() -> {
            HubClient client = HubManager.getInstance().client();
            String filter = "PENDING".equals(approvalFilterCombo.getSelectedItem()) ? "PENDING" : null;
            List<HubClient.BotApprovalItem> approvals = client.isReady() ? client.listApprovals(filter, 100) : List.of();
            SwingUtilities.invokeLater(() -> populateApprovals(approvals));
        });
    }

    private void populateApprovals(List<HubClient.BotApprovalItem> list) {
        currentApprovals = new ArrayList<>(list);
        approvalsModel.setRowCount(0);
        for (HubClient.BotApprovalItem a : list) {
            approvalsModel.addRow(new Object[]{
                    Boolean.FALSE,
                    a.id(),
                    a.kind(),
                    a.persona(),
                    a.routineId(),
                    a.channel(),
                    a.recipient(),
                    a.subject(),
                    a.status(),
                    a.expiresAt()
            });
        }
        if (!list.isEmpty()) {
            approvalsTable.setRowSelectionInterval(0, 0);
        }
    }

    private void populateRuns(List<HubClient.BotRunSummary> list) {
        currentRuns = new ArrayList<>(list);
        runsModel.setRowCount(0);
        for (HubClient.BotRunSummary r : list) {
            String summaryOrErr = !r.error().isBlank() ? "ERROR: " + r.error() : r.summary();
            runsModel.addRow(new Object[]{
                    r.id(),
                    r.botId(),
                    r.routineId(),
                    r.persona(),
                    r.modelId(),
                    r.status(),
                    r.startedAt(),
                    summaryOrErr
            });
        }
    }

    private void populatePages(List<HubClient.BotPageSummary> list) {
        pagesListModel.clear();
        for (HubClient.BotPageSummary p : list) {
            pagesListModel.addElement(p);
        }
        if (!pagesListModel.isEmpty() && pagesList.getSelectedIndex() < 0) {
            pagesList.setSelectedIndex(0);
        }
    }

    private void resolveSelectedSingle(String decision) {
        int row = approvalsTable.getSelectedRow();
        if (row < 0 || row >= currentApprovals.size()) {
            return;
        }
        HubClient.BotApprovalItem item = currentApprovals.get(row);
        if (item.id() == null) {
            return;
        }
        String edited = editablePayloadArea.getText().trim();
        String pattern = rulePatternField.getText().trim();
        Thread.ofPlatform().name("bot-approval-resolve").daemon(true).start(() -> {
            try {
                HubManager.getInstance().client().resolveApproval(item.id(), decision, edited, pattern);
                SwingUtilities.invokeLater(this::refreshAllAsync);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Failed to resolve approval: " + ex.getMessage(),
                        "Approval Error",
                        JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    private void batchResolveChecked(String decision) {
        List<Long> checkedIds = new ArrayList<>();
        Map<String, String> edits = new HashMap<>();
        for (int i = 0; i < approvalsModel.getRowCount() && i < currentApprovals.size(); i++) {
            Boolean checked = (Boolean) approvalsModel.getValueAt(i, 0);
            if (Boolean.TRUE.equals(checked) && currentApprovals.get(i).id() != null) {
                checkedIds.add(currentApprovals.get(i).id());
            }
        }
        if (checkedIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Check one or more items in the Select column first.",
                    "Batch Approval", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Thread.ofPlatform().name("bot-batch-resolve").daemon(true).start(() -> {
            try {
                HubManager.getInstance().client().batchResolveApprovals(checkedIds, decision, edits);
                SwingUtilities.invokeLater(this::refreshAllAsync);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Batch approval failed: " + ex.getMessage(),
                        "Batch Approval Error",
                        JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    private void loadRunTranscriptAsync(HubClient.BotRunSummary run) {
        if (run == null || run.id() == null) {
            return;
        }
        Thread.ofPlatform().name("bot-transcript-load").daemon(true).start(() -> {
            try {
                String text = HubManager.getInstance().client().getTranscript(run.id());
                SwingUtilities.invokeLater(() -> {
                    transcriptArea.setText(text);
                    transcriptArea.setCaretPosition(0);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> transcriptArea.setText("Could not load transcript: " + ex.getMessage()));
            }
        });
    }

    private void cancelSelectedRun() {
        int row = runsTable.getSelectedRow();
        if (row < 0 || row >= currentRuns.size()) {
            return;
        }
        Long runId = currentRuns.get(row).id();
        if (runId == null) {
            return;
        }
        Thread.ofPlatform().name("bot-run-cancel").daemon(true).start(() -> {
            try {
                HubManager.getInstance().client().cancelRun(runId);
                SwingUtilities.invokeLater(this::refreshAllAsync);
            } catch (Exception ignored) {
            }
        });
    }

    private void loadPagePreviewAsync(HubClient.BotPageSummary page) {
        Thread.ofPlatform().name("bot-page-preview").daemon(true).start(() -> {
            try {
                HubClient client = HubManager.getInstance().client();
                String html;
                if (client.isReady()) {
                    html = client.getPageHtml(page.slug());
                } else if (page.path() != null && !page.path().isBlank() && Files.isRegularFile(Path.of(page.path()))) {
                    html = Files.readString(Path.of(page.path()), StandardCharsets.UTF_8);
                } else {
                    html = "<html><body><h3>Start Agent Hub to generate and view " + page.title() + "</h3></body></html>";
                }
                final String content = html;
                SwingUtilities.invokeLater(() -> {
                    pagePreviewPane.setText(content);
                    pagePreviewPane.setCaretPosition(0);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> pagePreviewPane.setText("<html><body><p>" + ex.getMessage() + "</p></body></html>"));
            }
        });
    }

    private void openSelectedPageInBrowser() {
        HubClient.BotPageSummary selected = pagesList.getSelectedValue();
        if (selected == null) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                return;
            }
            HubClient client = HubManager.getInstance().client();
            if (client.isReady()) {
                Desktop.getDesktop().browse(URI.create(client.baseUrl() + "/v1/pages/" + selected.slug()));
            } else if (selected.path() != null && Files.isRegularFile(Path.of(selected.path()))) {
                Desktop.getDesktop().browse(Path.of(selected.path()).toUri());
            }
        } catch (Exception ignored) {
        }
    }

    private static List<HubClient.BotPageSummary> loadOfflinePages() {
        String home = System.getProperty("smile.home", ".");
        Path pagesDir = Path.of(home, "bot", "pages");
        List<HubClient.BotPageSummary> list = new ArrayList<>();
        list.add(new HubClient.BotPageSummary("executive-brief", "Executive Brief — @frank",
                pagesDir.resolve("executive-brief.html").toString(), ""));
        list.add(new HubClient.BotPageSummary("sales-pipeline", "Sales Pipeline — @mike",
                pagesDir.resolve("sales-pipeline.html").toString(), ""));
        list.add(new HubClient.BotPageSummary("community-radar", "Community Radar — @julien",
                pagesDir.resolve("community-radar.html").toString(), ""));
        list.add(new HubClient.BotPageSummary("token-usage", "Bot Activity & Token Telemetry",
                pagesDir.resolve("token-usage.html").toString(), ""));
        return list;
    }
}
