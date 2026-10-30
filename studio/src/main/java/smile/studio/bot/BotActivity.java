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
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import ioa.llm.client.LLM;
import ioa.llm.tool.AskUserQuestion;
import ioa.llm.tool.TodoWrite;
import ioa.llm.tool.Tool;
import smile.studio.cli.Intent;
import smile.studio.workspace.HubClient;
import smile.studio.workspace.HubManager;
import smile.swing.ScrollablePanel;

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
public class BotActivity extends JPanel {
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

    private static final ObjectMapper TOOL_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DISPLAY_TEXT_LIMIT = 12_000;

    /**
     * A human-readable conversation turn parsed from {@code conversation.jsonl}.
     *
     * @param prompt the user or routine prompt shown in the {@link Intent} header.
     * @param status optional timestamp or status shown in the {@link Intent} footer.
     * @param output the formatted tool call statuses and assistant Markdown response.
     */
    public record TranscriptTurn(String prompt, String status, String output) { }

    private record RawToolCall(String id, String name, JsonNode arguments) {
        static final RawToolCall EMPTY = new RawToolCall("", "", null);
    }

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
    private final JPanel transcriptIntents = new ScrollablePanel();
    private final JScrollPane transcriptScroll = new JScrollPane(transcriptIntents);

    // Tab 3: Bot Pages
    private final DefaultListModel<HubClient.BotPageSummary> pagesListModel = new DefaultListModel<>();
    private final JList<HubClient.BotPageSummary> pagesList = new JList<>(pagesListModel);
    private final JEditorPane pagePreviewPane = new JEditorPane("text/html", "");

    /**
     * Creates the Bot Activity, Approvals &amp; Pages workspace panel.
     *
     * @param initialTab initial tab index (`0` = Approvals, `1` = Runs, `2` = Pages).
     */
    public BotActivity(int initialTab) {
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
        JButton configBtn = new JButton("Bot Routines…");
        configBtn.addActionListener(e -> {
            var ws = (smile.studio.workspace.Workspace) SwingUtilities.getAncestorOfClass(
                    smile.studio.workspace.Workspace.class, this);
            if (ws != null) {
                ws.openBotManager(null);
            }
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

        transcriptIntents.setLayout(new BoxLayout(transcriptIntents, BoxLayout.Y_AXIS));
        transcriptScroll.getVerticalScrollBar().setUnitIncrement(18);
        transcriptScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel transcriptPanel = new JPanel(new BorderLayout(4, 4));
        JPanel transcriptTop = new JPanel(new BorderLayout());
        transcriptTop.add(new JLabel("Session Transcript:"), BorderLayout.WEST);
        JButton cancelRunBtn = new JButton("Stop / Cancel Selected Run");
        cancelRunBtn.addActionListener(e -> cancelSelectedRun());
        transcriptTop.add(cancelRunBtn, BorderLayout.EAST);
        transcriptPanel.add(transcriptTop, BorderLayout.NORTH);
        transcriptPanel.add(transcriptScroll, BorderLayout.CENTER);

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
        Long selectedId = null;
        int prevRow = runsTable.getSelectedRow();
        if (prevRow >= 0 && prevRow < currentRuns.size()) {
            selectedId = currentRuns.get(prevRow).id();
        }
        currentRuns = new ArrayList<>(list);
        runsModel.setRowCount(0);
        int selectRow = -1;
        for (int i = 0; i < list.size(); i++) {
            HubClient.BotRunSummary r = list.get(i);
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
            if (selectedId != null && selectedId.equals(r.id())) {
                selectRow = i;
            }
        }
        if (!list.isEmpty()) {
            int targetRow = selectRow >= 0 ? selectRow : 0;
            runsTable.setRowSelectionInterval(targetRow, targetRow);
        } else {
            transcriptIntents.removeAll();
            transcriptIntents.revalidate();
            transcriptIntents.repaint();
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
                String text = readRunTranscript(run);
                SwingUtilities.invokeLater(() -> renderTranscript(text, run));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> renderTranscriptError("Could not load transcript: " + ex.getMessage(), run));
            }
        });
    }

    private static String readRunTranscript(HubClient.BotRunSummary run) throws IOException, InterruptedException {
        HubClient client = HubManager.getInstance().client();
        if (client.isReady()) {
            try {
                return client.getTranscript(run.id());
            } catch (Exception ex) {
                Optional<String> local = readLocalTranscript(run.conversationPath());
                if (local.isPresent()) {
                    return local.get();
                }
                throw ex;
            }
        }
        Optional<String> local = readLocalTranscript(run.conversationPath());
        if (local.isPresent()) {
            return local.get();
        }
        return client.getTranscript(run.id());
    }

    private static Optional<String> readLocalTranscript(String conversationPath) {
        if (conversationPath == null || conversationPath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path p = Path.of(conversationPath);
            if (Files.isRegularFile(p)) {
                return Optional.of(Files.readString(p, StandardCharsets.UTF_8));
            }
            if (Files.isDirectory(p)) {
                Path direct = p.resolve("conversation.jsonl");
                if (Files.isRegularFile(direct)) {
                    return Optional.of(Files.readString(direct, StandardCharsets.UTF_8));
                }
                try (Stream<Path> dirs = Files.list(p)) {
                    Optional<Path> latest = dirs.filter(Files::isDirectory)
                            .map(d -> d.resolve("conversation.jsonl"))
                            .filter(Files::isRegularFile)
                            .max((a, b) -> {
                                try {
                                    return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                                } catch (IOException e) {
                                    return 0;
                                }
                            });
                    if (latest.isPresent()) {
                        return Optional.of(Files.readString(latest.get(), StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    /**
     * Renders a {@code conversation.jsonl} transcript into read-only {@link Intent}
     * widgets inside {@link #transcriptIntents}.
     *
     * @param jsonl the raw {@code conversation.jsonl} content.
     * @param run   optional run metadata for fallback headers.
     */
    public void renderTranscript(String jsonl, HubClient.BotRunSummary run) {
        transcriptIntents.removeAll();
        List<TranscriptTurn> turns = parseTranscript(jsonl);
        if (turns.isEmpty()) {
            String fallbackPrompt = run != null && run.routineId() != null && !run.routineId().isBlank()
                    ? "Routine: " + run.routineId() + (run.persona() != null && !run.persona().isBlank() ? " (@" + run.persona() + ")" : "")
                    : "(No transcript recorded for this run)";
            String fallbackStatus = run != null ? run.status() : "";
            String fallbackOutput = run != null && run.error() != null && !run.error().isBlank()
                    ? "Error: " + run.error()
                    : (run != null && run.summary() != null ? run.summary() : "");
            transcriptIntents.add(createTranscriptIntent(fallbackPrompt, fallbackStatus, fallbackOutput));
        } else {
            for (TranscriptTurn turn : turns) {
                transcriptIntents.add(createTranscriptIntent(turn.prompt(), turn.status(), turn.output()));
            }
        }
        transcriptIntents.add(Box.createVerticalGlue());
        transcriptIntents.revalidate();
        transcriptIntents.repaint();
        SwingUtilities.invokeLater(() -> transcriptScroll.getVerticalScrollBar().setValue(0));
    }

    private void renderTranscriptError(String errorMessage, HubClient.BotRunSummary run) {
        transcriptIntents.removeAll();
        String prompt = run != null && run.routineId() != null && !run.routineId().isBlank()
                ? "Routine: " + run.routineId()
                : "Transcript Error";
        String status = run != null ? run.status() : "";
        transcriptIntents.add(createTranscriptIntent(prompt, status, "Error: " + errorMessage));
        transcriptIntents.add(Box.createVerticalGlue());
        transcriptIntents.revalidate();
        transcriptIntents.repaint();
    }

    /** Returns the rendered {@link Intent} components in the transcript panel. */
    public List<Intent> transcriptIntentList() {
        List<Intent> list = new ArrayList<>();
        for (Component c : transcriptIntents.getComponents()) {
            if (c instanceof Intent intent) {
                list.add(intent);
            }
        }
        return list;
    }

    private static Intent createTranscriptIntent(String prompt, String status, String output) {
        Intent intent = new Intent(null);
        intent.editor().setText(prompt == null ? "" : prompt);
        intent.editor().setCaretPosition(0);
        intent.setEditable(false);
        if (status != null && !status.isBlank()) {
            String msg = status.strip().replaceAll("\\s+", " ");
            intent.status().setText(msg);
            intent.status().setToolTipText(status);
        }
        if (output != null && !output.isBlank()) {
            String shown = forDisplay(stripBlankLines(output));
            intent.output().print(shown);
            intent.output().setText(shown);
            intent.output().setCaretPosition(0);
            intent.output().highlight();
        }
        return intent;
    }

    /**
     * Parses a {@code conversation.jsonl} session transcript into human-readable
     * {@link TranscriptTurn}s, formatting tool calls the same way {@code ioa} streams
     * tool call status updates to {@code AgentCLI} / {@link Intent}.
     *
     * @param jsonl the raw {@code conversation.jsonl} string.
     * @return the ordered list of conversation turns.
     */
    public static List<TranscriptTurn> parseTranscript(String jsonl) {
        if (jsonl == null || jsonl.isBlank()) {
            return List.of();
        }

        List<TranscriptTurn> turns = new ArrayList<>();
        String currentPrompt = null;
        String currentStatus = "";
        StringBuilder currentOutput = new StringBuilder();

        for (String rawLine : jsonl.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            JsonNode node;
            try {
                node = TOOL_MAPPER.readTree(line);
            } catch (Exception ex) {
                if (currentPrompt == null) {
                    currentPrompt = "Session Transcript";
                }
                appendSection(currentOutput, line);
                continue;
            }

            if (node == null || !node.isObject()) {
                continue;
            }

            String role = node.path("role").asText("").trim().toLowerCase();
            if ("system".equals(role)) {
                continue;
            }

            String timestamp = formatTimestamp(node.get("timestamp"));
            if ("user".equals(role)) {
                if (currentPrompt != null) {
                    turns.add(new TranscriptTurn(currentPrompt, currentStatus, stripBlankLines(currentOutput.toString())));
                    currentOutput.setLength(0);
                }
                String prompt = stripSystemReminder(textValue(node.get("content")));
                currentPrompt = prompt.isBlank() ? "(Empty prompt)" : prompt;
                currentStatus = timestamp;
            } else if ("tool".equals(role)) {
                if (currentPrompt == null) {
                    currentPrompt = "Session summary";
                    currentStatus = timestamp;
                } else if (currentStatus.isBlank() && !timestamp.isBlank()) {
                    currentStatus = timestamp;
                }
                JsonNode content = node.get("content");
                if (content != null && content.isTextual()) {
                    appendSection(currentOutput, content.asText());
                } else if (content != null && content.isObject()) {
                    JsonNode message = content.get("message");
                    String spoken = spokenText(message);
                    if (!spoken.isBlank()) {
                        appendSection(currentOutput, spoken.strip());
                    }
                    String toolStatus = formatToolCalls(message, content.get("outputs"));
                    if (!toolStatus.isBlank()) {
                        appendToolLines(currentOutput, toolStatus);
                    }
                }
            } else if ("assistant".equals(role)) {
                String text = textValue(node.get("content"));
                if (text.isBlank() && node.has("summary")) {
                    text = node.path("summary").asText("");
                }
                if (currentPrompt == null) {
                    String routineId = node.path("routineId").asText("");
                    String persona = node.path("persona").asText("");
                    if (!routineId.isBlank()) {
                        currentPrompt = "Routine: " + routineId + (persona.isBlank() ? "" : " (@" + persona + ")");
                    } else {
                        currentPrompt = "Session summary";
                    }
                    String statusField = node.path("status").asText("");
                    currentStatus = !statusField.isBlank() ? statusField : timestamp;
                } else if (currentStatus.isBlank() && !timestamp.isBlank()) {
                    currentStatus = timestamp;
                }
                if (!text.isBlank()) {
                    appendSection(currentOutput, text.strip());
                }
            } else if ("error".equals(role)) {
                String text = textValue(node.get("content"));
                if (currentPrompt == null) {
                    currentPrompt = "Error";
                    currentStatus = timestamp;
                }
                if (!text.isBlank()) {
                    appendSection(currentOutput, "Error: " + text.strip());
                }
            }
        }

        if (currentPrompt != null) {
            turns.add(new TranscriptTurn(currentPrompt, currentStatus, stripBlankLines(currentOutput.toString())));
        }
        return turns;
    }

    private static String stripBlankLines(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replaceFirst("^[\\r\\n]+", "").stripTrailing();
    }

    private static void appendSection(StringBuilder sb, String section) {
        if (section == null || section.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            if (sb.charAt(sb.length() - 1) == '\n') {
                sb.append('\n');
            } else {
                sb.append("\n\n");
            }
        }
        sb.append(section);
    }

    private static void appendToolLines(StringBuilder sb, String toolLines) {
        if (toolLines == null || toolLines.isBlank()) {
            return;
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append(toolLines);
        if (sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    private static String stripSystemReminder(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        return prompt.replaceFirst("(?s)^\\s*<system-reminder>.*?</system-reminder>\\s*", "").strip();
    }

    private static String spokenText(JsonNode message) {
        if (message == null || message.isNull() || message.isMissingNode()) {
            return "";
        }
        if (message.isTextual()) {
            return message.asText();
        }
        if (message.isObject()) {
            String text = textValue(message.get("content"));
            if (!text.isBlank()) {
                return text;
            }
            if (LLM.showThinking()) {
                return textValue(message.get("reasoning"));
            }
            return "";
        }
        if (message.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : message) {
                String type = item.path("type").asText("");
                if ("function_call".equals(type) || "tool_use".equals(type)) {
                    continue;
                }
                String text = textValue(item.get("content"));
                if (text.isBlank()) {
                    text = textValue(item.get("text"));
                }
                if (!text.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return "";
    }

    private static String formatToolCalls(JsonNode message, JsonNode outputs) {
        List<RawToolCall> calls = extractCalls(message);
        List<JsonNode> results = new ArrayList<>();
        if (outputs != null && outputs.isArray()) {
            outputs.forEach(results::add);
        }
        int n = Math.max(calls.size(), results.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            RawToolCall call = i < calls.size() ? calls.get(i) : RawToolCall.EMPTY;
            JsonNode result = findResult(results, call.id(), i);
            String rawName = !call.name().isBlank()
                    ? call.name()
                    : (result != null ? result.path("name").asText("") : "");
            boolean success = result == null || !result.has("success") || result.path("success").asBoolean(true);
            String resultOutput = result == null ? "" : result.path("output").asText("");
            if (rawName.isBlank() && (call.arguments() == null || call.arguments().isNull()) && resultOutput.isBlank()) {
                continue;
            }
            String formatted = formatSingleToolCall(rawName, call.arguments(), success, resultOutput);
            if (!formatted.isBlank()) {
                sb.append(formatted);
                if (!formatted.endsWith("\n")) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static JsonNode findResult(List<JsonNode> results, String callId, int fallbackIndex) {
        if (callId != null && !callId.isBlank()) {
            for (JsonNode r : results) {
                if (callId.equals(r.path("id").asText(""))) {
                    return r;
                }
            }
        }
        return fallbackIndex >= 0 && fallbackIndex < results.size() ? results.get(fallbackIndex) : null;
    }

    private static String formatSingleToolCall(String rawName, JsonNode argsNode, boolean success, String resultOutput) {
        String canonicalName = Tool.canonical(rawName).orElse(rawName == null ? "" : rawName.trim());
        Tool tool = deserializeTool(canonicalName, argsNode);
        if (tool != null) {
            if (tool instanceof TodoWrite) {
                return tool.toString();
            }
            if (tool instanceof AskUserQuestion ask) {
                return formatAskUserQuestion(ask, success, resultOutput);
            }
            String desc = tool.toString();
            if (desc == null || desc.isBlank()) {
                desc = canonicalName.isBlank() ? "Tool" : canonicalName;
            }
            return (success ? "  ✅ " : "  ❌ ") + desc;
        }

        // Fallbacks when arguments were not serialized or tool is an MCP tool
        if ("TodoWrite".equals(canonicalName) && resultOutput != null && resultOutput.contains("📝 Current Tasks:")) {
            return resultOutput.substring(resultOutput.indexOf("📝 Current Tasks:")).strip();
        }
        if ("AskUserQuestion".equals(canonicalName)) {
            return formatAskUserQuestion(null, success, resultOutput);
        }
        if (("Edit".equals(canonicalName) || "Write".equals(canonicalName) || "Append".equals(canonicalName))
                && resultOutput != null && resultOutput.startsWith("File '")) {
            int endQuote = resultOutput.indexOf('\'', 6);
            if (endQuote > 6) {
                return (success ? "  ✅ " : "  ❌ ") + canonicalName + " " + resultOutput.substring(6, endQuote);
            }
        }
        String argsSummary = summarizeArguments(argsNode);
        String label = canonicalName.isBlank() ? "Tool" : canonicalName;
        if (!argsSummary.isBlank()) {
            label = label + " " + argsSummary;
        }
        return (success ? "  ✅ " : "  ❌ ") + label;
    }

    private static Tool deserializeTool(String canonicalName, JsonNode argsNode) {
        if (canonicalName == null || canonicalName.isBlank()
                || argsNode == null || argsNode.isNull() || argsNode.isMissingNode()) {
            return null;
        }
        Optional<Tool.Spec> specOpt = Tool.of(canonicalName);
        if (specOpt.isEmpty()) {
            return null;
        }
        Class<? extends Tool> clazz = specOpt.get().clazz();
        try {
            if (argsNode.isTextual()) {
                String json = argsNode.asText();
                if (json == null || json.isBlank()) {
                    return null;
                }
                return TOOL_MAPPER.readValue(json, clazz);
            }
            if (argsNode.isObject()) {
                if (argsNode.isEmpty()) {
                    return null;
                }
                return TOOL_MAPPER.treeToValue(argsNode, clazz);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String formatAskUserQuestion(AskUserQuestion ask, boolean success, String resultOutput) {
        String prefix = success ? "  ✅ " : "  ❌ ";
        if (ask != null && ask.questions != null && !ask.questions.isEmpty()) {
            String qText = ask.questions.stream()
                    .filter(Objects::nonNull)
                    .map(q -> q.question != null && !q.question.isBlank() ? q.question : q.header)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining("; "));
            if (!qText.isBlank()) {
                return prefix + "AskUserQuestion: " + qText;
            }
        }
        if (resultOutput != null && !resultOutput.isBlank()) {
            String oneLine = resultOutput.replaceFirst("^User has answered your questions:\\s*", "")
                    .replaceFirst("\\s*You can now continue with the user's answers in mind\\.\\s*$", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!oneLine.isBlank()) {
                return prefix + "AskUserQuestion: " + oneLine;
            }
        }
        return prefix + "AskUserQuestion";
    }

    private static String summarizeArguments(JsonNode argsNode) {
        if (argsNode == null || argsNode.isNull() || argsNode.isMissingNode()) {
            return "";
        }
        JsonNode obj = argsNode;
        if (argsNode.isTextual()) {
            String text = argsNode.asText("").trim();
            if (text.isEmpty() || "{}".equals(text)) {
                return "";
            }
            try {
                obj = TOOL_MAPPER.readTree(text);
            } catch (Exception ex) {
                return oneLine(text, 100);
            }
        }
        if (obj != null && obj.isObject()) {
            if (obj.isEmpty()) {
                return "";
            }
            return oneLine(obj.toString(), 100);
        }
        return "";
    }

    private static List<RawToolCall> extractCalls(JsonNode message) {
        if (message == null || message.isNull() || message.isMissingNode()) {
            return List.of();
        }
        List<RawToolCall> calls = new ArrayList<>();
        if (message.isArray()) {
            for (JsonNode item : message) {
                RawToolCall call = toCall(item);
                if (!call.name().isBlank() || call.arguments() != null) {
                    calls.add(call);
                }
            }
            return calls;
        }
        if (message.isObject()) {
            JsonNode toolCalls = message.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode item : toolCalls) {
                    RawToolCall call = toCall(item);
                    if (!call.name().isBlank() || call.arguments() != null) {
                        calls.add(call);
                    }
                }
                return calls;
            }
            RawToolCall call = toCall(message);
            if (!call.name().isBlank() || call.arguments() != null) {
                calls.add(call);
            }
        }
        return calls;
    }

    private static RawToolCall toCall(JsonNode node) {
        if (node == null || !node.isObject()) {
            return RawToolCall.EMPTY;
        }
        JsonNode source = node;
        if (node.get("function") != null && node.get("function").isObject()) {
            source = node.get("function");
        } else if (node.get("tool_use") != null && node.get("tool_use").isObject()) {
            source = node.get("tool_use");
        }
        String name = textValue(source.get("name"));
        String id = firstNonBlank(
                textValue(node.get("id")),
                textValue(node.get("call_id")),
                textValue(source.get("id")),
                textValue(source.get("call_id"))
        );
        JsonNode args = firstPresentNode(
                source.get("arguments"),
                source.get("input"),
                node.get("arguments"),
                node.get("input")
        );
        return new RawToolCall(id, name, args);
    }

    private static JsonNode firstPresentNode(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && !n.isNull() && !n.isMissingNode()) {
                if (n.isTextual() && n.asText("").isBlank()) {
                    continue;
                }
                return n;
            }
        }
        return null;
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                String text = item.isTextual() ? item.asText() : textValue(item.get("text"));
                if (!text.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String formatTimestamp(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return "";
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return "";
        }
        try {
            Instant instant = Instant.parse(raw);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER);
        } catch (Exception ex) {
            return raw;
        }
    }

    private static String oneLine(String text, int limit) {
        String s = text.replaceAll("\\s+", " ").trim();
        if (s.length() <= limit) {
            return s;
        }
        return s.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static String forDisplay(String text) {
        if (text.length() <= DISPLAY_TEXT_LIMIT) {
            return text;
        }
        return text.substring(0, DISPLAY_TEXT_LIMIT)
                + "\n\n… [truncated in the view; the session history file has the full text]";
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
