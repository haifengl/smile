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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import com.formdev.flatlaf.util.SystemFileChooser;
import ioa.agent.bot.BotsConfig;
import ioa.agent.bot.ProjectsConfig;
import ioa.agent.email.EmailConfig;
import smile.studio.LlmServices;
import smile.studio.SmileStudio;

/**
 * Visual editor for Bot Personas, Routines, Context Projects, and Auto Review Rules
 * persisted in {@code ${smile.home}/conf/bots.json} and {@code projects.json}.
 *
 * @author Haifeng Li
 */
public class BotManager extends JPanel {
    private static final String DEFAULT_MODEL_CHOICE = "<Default Persona Model>";

    private BotsConfig config;
    private final Runnable onSavedCallback;

    // Routine form fields
    private final JComboBox<String> botIdCombo = new JComboBox<>(new String[]{
            "frank", "all-agents-memory", "julien", "mike"
    });
    private final JTextField personaField = new JTextField("chief-of-staff", 18);
    private final JTextField routineIdField = new JTextField(22);
    private final JTextField routineNameField = new JTextField(26);
    private final JCheckBox routineEnabledCheck = new JCheckBox("Enabled", true);
    private final JComboBox<String> modelCombo = new JComboBox<>();

    // Trigger fields
    private final JComboBox<String> sourceCombo = new JComboBox<>(new String[]{
            "timer", "email", "calendar", "webhook"
    });
    private final JTextField cronField = new JTextField(18);
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 604800, 60));
    private final JComboBox<String> missedFireCombo = new JComboBox<>(new String[]{
            "FIRE_ONCE", "SKIP"
    });
    private final JComboBox<String> endpointCombo = new JComboBox<>();
    private final JTextField quietStartField = new JTextField(8);
    private final JTextField quietEndField = new JTextField(8);
    private final JSpinner settleSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3600, 5));
    private final JSpinner maxTurnsSpinner = new JSpinner(new SpinnerNumberModel(12, 0, 1000, 1));

    // Workflow fields
    private final JComboBox<BotsConfig.WorkflowKind> workflowKindCombo =
            new JComboBox<>(BotsConfig.WorkflowKind.values());
    private final JTextField workflowTargetField = new JTextField(26);
    private final JTextArea instructionsArea = new JTextArea(4, 32);
    private final JCheckBox requireActivityCheck = new JCheckBox("Skip if persona had 0 sessions today");
    private final JCheckBox useContextProjectsCheck = new JCheckBox("Enrich prompt with Studio Context Projects");
    private final JTextField awaitRoutineField = new JTextField(18);

    // Policy fields
    private final JComboBox<BotsConfig.SessionMode> sessionModeCombo =
            new JComboBox<>(BotsConfig.SessionMode.values());
    private final JCheckBox confirmConsequentialCheck = new JCheckBox("Require approval for consequential actions", true);
    private final JSpinner ttlMinutesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10080, 5));

    // Context Projects table
    private final DefaultTableModel projectsTableModel = new DefaultTableModel(
            new Object[]{"Project Name", "Path", "Workspace (.smile)", "Personas"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable projectsTable = new JTable(projectsTableModel);

    // Auto Review Rules table
    private final DefaultTableModel rulesTableModel = new DefaultTableModel(
            new Object[]{"Rule ID", "Category", "Pattern", "Decision"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable rulesTable = new JTable(rulesTableModel);

    /**
     * Creates the Bot Routine Manager panel.
     *
     * @param initialRoutineId optional routine ID to select initially.
     * @param onSavedCallback  optional callback invoked when changes are saved.
     */
    public BotManager(String initialRoutineId, Runnable onSavedCallback) {
        super(new BorderLayout(8, 8));
        this.onSavedCallback = onSavedCallback;
        this.config = HubManager.getInstance().client().getOverview().config();

        populateModelCombo();
        populateEndpointCombo();
        initComponents();
        reloadFromConfig(initialRoutineId);
    }

    /**
     * Reloads configuration from disk / Agent Hub and selects the requested routine.
     *
     * @param routineId optional routine ID to select.
     */
    public void selectRoutine(String routineId) {
        this.config = HubManager.getInstance().client().getOverview().config();
        populateModelCombo();
        populateEndpointCombo();
        reloadFromConfig(routineId);
    }

    /**
     * Reloads configuration from disk / Agent Hub and prepares the form for a new routine.
     *
     * @param bot optional Bot persona to pre-populate in the form.
     */
    public void prepareNewRoutine(BotsConfig.BotSpec bot) {
        this.config = HubManager.getInstance().client().getOverview().config();
        populateModelCombo();
        populateEndpointCombo();
        refreshProjectsTable();
        refreshRulesTable();
        prepareNewRoutineForm(bot);
    }

    private void populateModelCombo() {
        modelCombo.removeAllItems();
        modelCombo.setEditable(true);
        modelCombo.addItem(DEFAULT_MODEL_CHOICE);
        for (LlmServices.AvailableModel m : SmileStudio.llmServices().availableModels()) {
            modelCombo.addItem(m.model().id());
        }
    }

    private void populateEndpointCombo() {
        endpointCombo.removeAllItems();
        endpointCombo.setEditable(true);
        endpointCombo.addItem("inbox");
        for (EmailConfig.AccountSpec acc : EmailConfig.load().accounts()) {
            endpointCombo.addItem(acc.emailAddress());
        }
    }

    private void initComponents() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(8, 8, 8, 8));
        tabs.addTab("Routine Definition", buildRoutineFormPanel());
        tabs.addTab("Context Projects (projects.json)", buildProjectsPanel());
        tabs.addTab("Auto Review Rules", buildRulesPanel());
        add(tabs, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton runNowBtn = new JButton("▶ Run Routine Now");
        runNowBtn.addActionListener(e -> triggerSelectedRoutineNow());
        JButton saveBtn = new JButton("Save to bots.json");
        saveBtn.addActionListener(e -> saveCurrentRoutineAndConfig());

        bottomBar.add(runNowBtn);
        bottomBar.add(saveBtn);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private JComponent buildRoutineFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(10, 12, 10, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        botIdCombo.setEditable(true);
        botIdCombo.addActionListener(e -> {
            Object sel = botIdCombo.getSelectedItem();
            if ("frank".equals(sel)) personaField.setText("chief-of-staff");
            else if ("all-agents-memory".equals(sel)) personaField.setText("*");
            else if ("julien".equals(sel)) personaField.setText("evangelist");
            else if ("mike".equals(sel)) personaField.setText("sales");
        });

        addFormRow(form, gbc, row++, "Bot ID:", botIdCombo, "Persona:", personaField);
        addFormRow(form, gbc, row++, "Routine ID:", routineIdField, "", routineEnabledCheck);
        addSingleRow(form, gbc, row++, "Display Name:", routineNameField);
        addSingleRow(form, gbc, row++, "Model Override:", modelCombo);

        addSectionHeader(form, gbc, row++, "Trigger & Schedule");
        addFormRow(form, gbc, row++, "Source:", sourceCombo, "Missed-Fire Policy:", missedFireCombo);
        addFormRow(form, gbc, row++, "Cron (5-field):", cronField, "Interval (sec):", intervalSpinner);
        addSingleRow(form, gbc, row++, "Mailbox / Feed URL / Path:", endpointCombo);
        addFormRow(form, gbc, row++, "Quiet Start (HH:mm):", quietStartField, "Quiet End (HH:mm):", quietEndField);
        addFormRow(form, gbc, row++, "Settle (sec):", settleSpinner, "Max Turns / Hour:", maxTurnsSpinner);

        addSectionHeader(form, gbc, row++, "Workflow & Cross-Project Enrichment");
        addFormRow(form, gbc, row++, "Workflow Kind:", workflowKindCombo, "Await Routine ID:", awaitRoutineField);
        addSingleRow(form, gbc, row++, "Target (Skill / Prompt):", workflowTargetField);

        instructionsArea.setLineWrap(true);
        instructionsArea.setWrapStyleWord(true);
        addSingleRow(form, gbc, row++, "Instructions:", new JScrollPane(instructionsArea));
        addFormRow(form, gbc, row++, "", requireActivityCheck, "", useContextProjectsCheck);

        addSectionHeader(form, gbc, row++, "Session & Approval Guardrails");
        addFormRow(form, gbc, row++, "Session Mode:", sessionModeCombo, "Approval TTL (min):", ttlMinutesSpinner);
        addSingleRow(form, gbc, row++, "", confirmConsequentialCheck);

        return new JScrollPane(form);
    }

    private JComponent buildProjectsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel info = new JLabel("<html>Tracked SMILE Studio projects in <code>${smile.home}/conf/projects.json</code>. "
                + "Cross-project routines (<code>/daily-log</code> at 19:00 and <code>/daily-recap</code> at 20:00) "
                + "automatically inspect these workspaces.</html>");
        panel.add(info, BorderLayout.NORTH);
        panel.add(new JScrollPane(projectsTable), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addProjBtn = new JButton("+ Add Project Folder…");
        addProjBtn.addActionListener(e -> {
            SystemFileChooser fc = new SystemFileChooser();
            fc.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
                Path selected = fc.getSelectedFile().toPath();
                ProjectsConfig updated = ProjectsConfig.registerProject(selected);
                config = config.withContextProjects(updated.projects());
                refreshProjectsTable();
            }
        });
        JButton removeProjBtn = new JButton("Remove Selected");
        removeProjBtn.addActionListener(e -> {
            int sel = projectsTable.getSelectedRow();
            if (sel >= 0 && sel < config.contextProjects().size()) {
                List<ProjectsConfig.ProjectEntry> next = new ArrayList<>(config.contextProjects());
                next.remove(sel);
                new ProjectsConfig(next).save();
                config = config.withContextProjects(next);
                refreshProjectsTable();
            }
        });
        btns.add(addProjBtn);
        btns.add(removeProjBtn);
        panel.add(btns, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildRulesPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel info = new JLabel("<html>Standing Auto Review Rules in <code>bots.json</code>. "
                + "<b>Precedence invariant:</b> <code>REQUIRE_APPROVAL</code> always wins over <code>ALWAYS_ALLOW</code>.</html>");
        panel.add(info, BorderLayout.NORTH);
        panel.add(new JScrollPane(rulesTable), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addRuleBtn = new JButton("+ Add Rule…");
        addRuleBtn.addActionListener(e -> promptAddRule());
        JButton removeRuleBtn = new JButton("Remove Selected Rule");
        removeRuleBtn.addActionListener(e -> {
            int sel = rulesTable.getSelectedRow();
            if (sel >= 0 && sel < config.autoReviewRules().size()) {
                List<BotsConfig.AutoReviewRule> next = new ArrayList<>(config.autoReviewRules());
                next.remove(sel);
                config = new BotsConfig(config.primaryBot(), config.dailyLogTime(),
                        config.contextProjects(), next, config.bots());
                refreshRulesTable();
            }
        });
        btns.add(addRuleBtn);
        btns.add(removeRuleBtn);
        panel.add(btns, BorderLayout.SOUTH);
        return panel;
    }

    private void promptAddRule() {
        JComboBox<BotsConfig.ActionCategory> catCombo = new JComboBox<>(BotsConfig.ActionCategory.values());
        JTextField patternInput = new JTextField("*", 20);
        JComboBox<BotsConfig.RuleDecision> decCombo = new JComboBox<>(BotsConfig.RuleDecision.values());

        JPanel p = new JPanel(new GridLayout(3, 2, 6, 6));
        p.add(new JLabel("Category:"));
        p.add(catCombo);
        p.add(new JLabel("Pattern:"));
        p.add(patternInput);
        p.add(new JLabel("Decision:"));
        p.add(decCombo);

        if (JOptionPane.showConfirmDialog(this, p, "Add Auto Review Rule",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            BotsConfig.AutoReviewRule rule = new BotsConfig.AutoReviewRule(
                    "rule-" + (config.autoReviewRules().size() + 1),
                    (BotsConfig.ActionCategory) catCombo.getSelectedItem(),
                    patternInput.getText().trim(),
                    (BotsConfig.RuleDecision) decCombo.getSelectedItem()
            );
            config = config.withAddedRule(rule);
            refreshRulesTable();
        }
    }

    private void reloadFromConfig(String selectRoutineId) {
        BotsConfig.BotSpec targetBot = null;
        BotsConfig.RoutineSpec targetRoutine = null;
        for (BotsConfig.BotSpec bot : config.bots()) {
            for (BotsConfig.RoutineSpec routine : bot.routines()) {
                if (targetRoutine == null) {
                    targetBot = bot;
                    targetRoutine = routine;
                }
                if (selectRoutineId != null && selectRoutineId.equalsIgnoreCase(routine.routineId())) {
                    targetBot = bot;
                    targetRoutine = routine;
                    break;
                }
            }
            if (selectRoutineId != null && targetRoutine != null
                    && selectRoutineId.equalsIgnoreCase(targetRoutine.routineId())) {
                break;
            }
        }
        if (targetBot != null && targetRoutine != null) {
            loadRoutineIntoForm(targetBot, targetRoutine);
        } else {
            prepareNewRoutineForm(config.bots().isEmpty() ? null : config.bots().getFirst());
        }
        refreshProjectsTable();
        refreshRulesTable();
    }

    private void refreshProjectsTable() {
        projectsTableModel.setRowCount(0);
        for (ProjectsConfig.ProjectEntry p : config.contextProjects()) {
            projectsTableModel.addRow(new Object[]{
                    p.name(),
                    p.path(),
                    p.workspaceDir(),
                    String.join(", ", p.agents())
            });
        }
    }

    private void refreshRulesTable() {
        rulesTableModel.setRowCount(0);
        for (BotsConfig.AutoReviewRule r : config.autoReviewRules()) {
            rulesTableModel.addRow(new Object[]{
                    r.id(),
                    r.category(),
                    r.pattern(),
                    r.decision()
            });
        }
    }

    private void loadRoutineIntoForm(BotsConfig.BotSpec bot, BotsConfig.RoutineSpec routine) {
        botIdCombo.setSelectedItem(bot.botId());
        personaField.setText(bot.persona());
        routineIdField.setText(routine.routineId());
        routineNameField.setText(routine.name());
        routineEnabledCheck.setSelected(routine.enabled());
        modelCombo.setSelectedItem(routine.model() == null || routine.model().isBlank()
                ? DEFAULT_MODEL_CHOICE
                : routine.model());

        BotsConfig.TriggerSpec t = routine.trigger();
        sourceCombo.setSelectedItem(t.source());
        cronField.setText(t.cron() == null ? "" : t.cron());
        intervalSpinner.setValue((int) Math.max(0, t.intervalSeconds()));
        missedFireCombo.setSelectedItem(t.missedFirePolicy());

        String endpoint = "";
        if (t.mailbox() != null && !t.mailbox().isBlank()) endpoint = t.mailbox();
        else if (t.calendarUrl() != null && !t.calendarUrl().isBlank()) endpoint = t.calendarUrl();
        else if (t.path() != null && !t.path().isBlank()) endpoint = t.path();
        endpointCombo.setSelectedItem(endpoint);

        quietStartField.setText(t.quietStart() == null ? "" : t.quietStart());
        quietEndField.setText(t.quietEnd() == null ? "" : t.quietEnd());
        settleSpinner.setValue(Math.max(0, t.settleSeconds()));
        maxTurnsSpinner.setValue(Math.max(0, t.maxTurnsPerHour()));

        BotsConfig.WorkflowSpec w = routine.workflow();
        workflowKindCombo.setSelectedItem(w.kind());
        workflowTargetField.setText(w.target());
        instructionsArea.setText(w.instructions() == null ? "" : w.instructions());
        requireActivityCheck.setSelected(w.requireActivityToday());
        useContextProjectsCheck.setSelected(w.useContextProjects());
        awaitRoutineField.setText(w.awaitRoutineId() == null ? "" : w.awaitRoutineId());

        BotsConfig.PolicySpec p = routine.policy();
        sessionModeCombo.setSelectedItem(p.sessionMode());
        confirmConsequentialCheck.setSelected(p.confirmConsequential());
        ttlMinutesSpinner.setValue(Math.max(0, p.approvalTtlMinutes()));
    }

    private void prepareNewRoutineForm(BotsConfig.BotSpec bot) {
        if (bot != null) {
            botIdCombo.setSelectedItem(bot.botId());
            personaField.setText(bot.persona());
        }
        int totalRoutines = config.bots().stream().mapToInt(b -> b.routines().size()).sum();
        routineIdField.setText("custom-routine-" + (totalRoutines + 1));
        routineNameField.setText("New Bot Routine");
        routineEnabledCheck.setSelected(true);
        modelCombo.setSelectedItem(DEFAULT_MODEL_CHOICE);
        sourceCombo.setSelectedItem("timer");
        cronField.setText("0 9 * * MON-FRI");
        intervalSpinner.setValue(0);
        endpointCombo.setSelectedItem("");
        workflowKindCombo.setSelectedItem(BotsConfig.WorkflowKind.PROMPT);
        workflowTargetField.setText("Summarize key updates and stage any outbound actions for approval.");
        instructionsArea.setText("");
        requireActivityCheck.setSelected(false);
        useContextProjectsCheck.setSelected(false);
        awaitRoutineField.setText("");
    }

    private void saveCurrentRoutineAndConfig() {
        String botId = String.valueOf(botIdCombo.getSelectedItem()).trim();
        String persona = personaField.getText().trim();
        String routineId = routineIdField.getText().trim();
        if (routineId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Routine ID is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Object modelChoice = modelCombo.getSelectedItem();
        String model = (modelChoice == null || DEFAULT_MODEL_CHOICE.equals(modelChoice.toString().trim()) || modelChoice.toString().isBlank())
                ? null
                : modelChoice.toString().trim();

        String source = String.valueOf(sourceCombo.getSelectedItem());
        String cron = cronField.getText().trim();
        long interval = ((Number) intervalSpinner.getValue()).longValue();
        String missed = String.valueOf(missedFireCombo.getSelectedItem());
        Object epItem = endpointCombo.getSelectedItem();
        String ep = epItem == null ? "" : epItem.toString().trim();

        String mailbox = "email".equals(source) ? ep : null;
        String calUrl = "calendar".equals(source) ? ep : null;
        String path = "webhook".equals(source) ? ep : null;

        BotsConfig.TriggerSpec trigger = new BotsConfig.TriggerSpec(
                source,
                cron.isEmpty() ? null : cron,
                interval,
                missed,
                mailbox,
                calUrl,
                "calendar".equals(source) ? 900L : 0L,
                path,
                null,
                ("email".equals(source) || "calendar".equals(source)) ? List.of("*") : null,
                null,
                ((Number) settleSpinner.getValue()).intValue(),
                quietStartField.getText().isBlank() ? null : quietStartField.getText().trim(),
                quietEndField.getText().isBlank() ? null : quietEndField.getText().trim(),
                ((Number) maxTurnsSpinner.getValue()).intValue()
        );

        BotsConfig.WorkflowSpec workflow = new BotsConfig.WorkflowSpec(
                (BotsConfig.WorkflowKind) workflowKindCombo.getSelectedItem(),
                workflowTargetField.getText().trim(),
                instructionsArea.getText().isBlank() ? null : instructionsArea.getText().trim(),
                requireActivityCheck.isSelected(),
                useContextProjectsCheck.isSelected(),
                awaitRoutineField.getText().isBlank() ? null : awaitRoutineField.getText().trim()
        );

        BotsConfig.PolicySpec policy = new BotsConfig.PolicySpec(
                (BotsConfig.SessionMode) sessionModeCombo.getSelectedItem(),
                confirmConsequentialCheck.isSelected(),
                ((Number) ttlMinutesSpinner.getValue()).intValue()
        );

        BotsConfig.RoutineSpec spec = new BotsConfig.RoutineSpec(
                routineId,
                routineNameField.getText().trim(),
                routineEnabledCheck.isSelected(),
                model,
                trigger,
                workflow,
                policy
        );

        try {
            spec.toEventSubscription();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid Trigger", JOptionPane.ERROR_MESSAGE);
            return;
        }

        config = config.withUpsertedRoutine(botId, botId, persona, spec);
        persistConfig(routineId);
    }

    private void persistConfig(String selectRoutineId) {
        try {
            HubManager.getInstance().client().saveConfig(config);
            reloadFromConfig(selectRoutineId);
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save bots.json: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void triggerSelectedRoutineNow() {
        String botId = String.valueOf(botIdCombo.getSelectedItem()).trim();
        String routineId = routineIdField.getText().trim();
        if (routineId.isEmpty()) {
            return;
        }
        Thread.ofPlatform().name("bot-run-now").daemon(true).start(() -> {
            try {
                if (!HubManager.getInstance().isRunning()) {
                    HubManager.getInstance().start();
                }
                HubManager.getInstance().client().runRoutineNow(botId, routineId);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Triggered routine '" + routineId + "' on Agent Hub.",
                        "Run Now",
                        JOptionPane.INFORMATION_MESSAGE));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Failed to run routine '" + routineId + "': " + ex.getMessage(),
                        "Run Now Error",
                        JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    private static void addSectionHeader(JPanel panel, GridBagConstraints gbc, int row, String title) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(new EmptyBorder(8, 0, 2, 0));
        panel.add(label, gbc);
        gbc.gridwidth = 1;
    }

    private static void addSingleRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
        gbc.gridwidth = 1;
    }

    private static void addFormRow(JPanel panel, GridBagConstraints gbc, int row,
                                   String label1, Component field1,
                                   String label2, Component field2) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label1), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(field1, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label2), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(field2, gbc);
    }
}
