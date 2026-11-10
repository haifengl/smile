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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import com.formdev.flatlaf.util.SystemFileChooser;
import ioa.agent.bot.BotsConfig;
import ioa.agent.bot.ProjectsConfig;
import ioa.agent.calendar.CalendarConfig;
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
    private static final ResourceBundle bundle = ResourceBundle.getBundle(BotManager.class.getName(), Locale.getDefault());
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
    private final JCheckBox routineEnabledCheck = new JCheckBox(bundle.getString("EnabledCheckbox"), true);
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
    private final JLabel instructionsLabel = new JLabel(bundle.getString("InstructionsLabel"));
    private final JTextArea instructionsArea = new JTextArea(4, 32);
    private final JCheckBox requireActivityCheck = new JCheckBox(bundle.getString("RequireActivityCheckbox"));
    private final JCheckBox useContextProjectsCheck = new JCheckBox(bundle.getString("UseContextProjectsCheckbox"));
    private final JTextField awaitRoutineField = new JTextField(18);

    // Policy fields
    private final JComboBox<BotsConfig.SessionMode> sessionModeCombo =
            new JComboBox<>(BotsConfig.SessionMode.values());
    private final JCheckBox confirmConsequentialCheck = new JCheckBox(bundle.getString("ConfirmConsequentialCheckbox"), false);
    private final JSpinner ttlMinutesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10080, 5));

    // Context Projects table
    private final DefaultTableModel projectsTableModel = new DefaultTableModel(
            new Object[]{
                    bundle.getString("ColProjectName"),
                    bundle.getString("ColPath"),
                    bundle.getString("ColWorkspace"),
                    bundle.getString("ColPersonas")
            }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable projectsTable = new JTable(projectsTableModel);

    // Auto Review Rules table
    private final DefaultTableModel rulesTableModel = new DefaultTableModel(
            new Object[]{
                    bundle.getString("ColRuleId"),
                    bundle.getString("ColCategory"),
                    bundle.getString("ColPattern"),
                    bundle.getString("ColDecision")
            }, 0) {
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
     * Returns the routine ID currently displayed in the Routine Definition form.
     *
     * @return the current routine ID (trimmed), or empty string if blank.
     */
    public String selectedRoutineId() {
        return routineIdField.getText().trim();
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
        Object currentSel = endpointCombo.getSelectedItem();
        endpointCombo.removeAllItems();
        endpointCombo.setEditable(true);
        String src = (String) sourceCombo.getSelectedItem();
        for (String item : resolveEndpointOptions(src, EmailConfig.load(), CalendarConfig.load())) {
            endpointCombo.addItem(item);
        }
        if (currentSel != null) {
            endpointCombo.setSelectedItem(currentSel);
        }
    }

    static List<String> resolveEndpointOptions(String source, EmailConfig emailConfig, CalendarConfig calendarConfig) {
        Set<String> options = new LinkedHashSet<>();
        if ("calendar".equalsIgnoreCase(source)) {
            options.add("primary");
            for (CalendarConfig.AccountSpec acc : calendarConfig.accounts()) {
                if (acc.accountId() != null && !acc.accountId().isBlank()) {
                    options.add(acc.accountId());
                }
            }
            for (EmailConfig.AccountSpec acc : emailConfig.accounts()) {
                if ((acc.provider() == EmailConfig.Provider.GMAIL || acc.provider() == EmailConfig.Provider.MSGRAPH)
                        && acc.emailAddress() != null && !acc.emailAddress().isBlank()) {
                    options.add(acc.emailAddress());
                }
            }
        } else {
            options.add("inbox");
            for (EmailConfig.AccountSpec acc : emailConfig.accounts()) {
                if (acc.emailAddress() != null && !acc.emailAddress().isBlank()) {
                    options.add(acc.emailAddress());
                }
            }
        }
        return List.copyOf(options);
    }

    private void initComponents() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(8, 8, 8, 8));
        tabs.addTab(bundle.getString("TabRoutine"), buildRoutineFormPanel());
        tabs.addTab(bundle.getString("TabContextProjects"), buildProjectsPanel());
        tabs.addTab(bundle.getString("TabReviewRules"), buildRulesPanel());
        add(tabs, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton runNowBtn = new JButton(bundle.getString("RunRoutineNowButton"));
        runNowBtn.addActionListener(e -> triggerSelectedRoutineNow());
        JButton saveBtn = new JButton(bundle.getString("SaveButton"));
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

        addFormRow(form, gbc, row++, bundle.getString("BotIdLabel"), botIdCombo, bundle.getString("PersonaLabel"), personaField);
        addFormRow(form, gbc, row++, bundle.getString("RoutineIdLabel"), routineIdField, "", routineEnabledCheck);
        addSingleRow(form, gbc, row++, bundle.getString("DisplayNameLabel"), routineNameField);
        addSingleRow(form, gbc, row++, bundle.getString("ModelOverrideLabel"), modelCombo);

        addSectionHeader(form, gbc, row++, bundle.getString("SectionTriggerSchedule"));
        sourceCombo.addActionListener(e -> populateEndpointCombo());
        addFormRow(form, gbc, row++,
                bundle.getString("SourceLabel"), sourceCombo, null,
                bundle.getString("MissedFirePolicyLabel"), missedFireCombo, bundle.getString("MissedFirePolicyTooltip"));
        addFormRow(form, gbc, row++,
                bundle.getString("CronLabel"), cronField, bundle.getString("CronTooltip"),
                bundle.getString("IntervalLabel"), intervalSpinner, bundle.getString("IntervalTooltip"));
        addSingleRow(form, gbc, row++,
                bundle.getString("EndpointLabel"), endpointCombo, bundle.getString("EndpointTooltip"));
        addFormRow(form, gbc, row++,
                bundle.getString("QuietStartLabel"), quietStartField, bundle.getString("QuietStartTooltip"),
                bundle.getString("QuietEndLabel"), quietEndField, bundle.getString("QuietEndTooltip"));
        addFormRow(form, gbc, row++,
                bundle.getString("SettleLabel"), settleSpinner, bundle.getString("SettleTooltip"),
                bundle.getString("MaxTurnsPerHourLabel"), maxTurnsSpinner, bundle.getString("MaxTurnsPerHourTooltip"));

        addSectionHeader(form, gbc, row++, bundle.getString("SectionWorkflowEnrichment"));
        workflowKindCombo.addActionListener(e -> updateWorkflowFieldStates());
        addFormRow(form, gbc, row++,
                bundle.getString("WorkflowKindLabel"), workflowKindCombo, null,
                bundle.getString("AwaitRoutineIdLabel"), awaitRoutineField, bundle.getString("AwaitRoutineIdTooltip"));
        addSingleRow(form, gbc, row++,
                bundle.getString("TargetSkillToolLabel"), workflowTargetField, bundle.getString("TargetSkillToolTooltip"));

        instructionsArea.setLineWrap(true);
        instructionsArea.setWrapStyleWord(true);
        String instructionsTooltip = bundle.getString("InstructionsTooltip");
        instructionsArea.setToolTipText(instructionsTooltip);
        addSingleRow(form, gbc, row++, instructionsLabel, new JScrollPane(instructionsArea), instructionsTooltip);
        addFormRow(form, gbc, row++, "", requireActivityCheck, "", useContextProjectsCheck);

        addSectionHeader(form, gbc, row++, bundle.getString("SectionSessionGuardrails"));
        addFormRow(form, gbc, row++,
                bundle.getString("SessionModeLabel"), sessionModeCombo, null,
                bundle.getString("ApprovalTtlLabel"), ttlMinutesSpinner, bundle.getString("ApprovalTtlTooltip"));
        addSingleRow(form, gbc, row++, "", confirmConsequentialCheck);

        return new JScrollPane(form);
    }

    private JComponent buildProjectsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel info = new JLabel(bundle.getString("ProjectsInfo"));
        panel.add(info, BorderLayout.NORTH);
        panel.add(new JScrollPane(projectsTable), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addProjBtn = new JButton(bundle.getString("AddProjectFolderButton"));
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
        JButton removeProjBtn = new JButton(bundle.getString("RemoveSelectedProjectButton"));
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
        JLabel info = new JLabel(bundle.getString("RulesInfo"));
        panel.add(info, BorderLayout.NORTH);
        panel.add(new JScrollPane(rulesTable), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addRuleBtn = new JButton(bundle.getString("AddRuleButton"));
        addRuleBtn.addActionListener(e -> promptAddRule());
        JButton removeRuleBtn = new JButton(bundle.getString("RemoveSelectedRuleButton"));
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
        p.add(new JLabel(bundle.getString("CategoryLabel")));
        p.add(catCombo);
        p.add(new JLabel(bundle.getString("PatternLabel")));
        p.add(patternInput);
        p.add(new JLabel(bundle.getString("DecisionLabel")));
        p.add(decCombo);

        if (JOptionPane.showConfirmDialog(this, p, bundle.getString("AddAutoReviewRuleTitle"),
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
        updateWorkflowFieldStates();
        if (w.kind() == BotsConfig.WorkflowKind.PROMPT) {
            String instructions = (w.instructions() == null || w.instructions().isBlank())
                    ? (w.target() == null ? "" : w.target())
                    : w.instructions();
            instructionsArea.setText(instructions);
        } else {
            workflowTargetField.setText(w.target());
            instructionsArea.setText(w.instructions() == null ? "" : w.instructions());
        }
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
        updateWorkflowFieldStates();
        instructionsArea.setText("Summarize key updates and stage any outbound actions for approval.");
        requireActivityCheck.setSelected(false);
        useContextProjectsCheck.setSelected(false);
        awaitRoutineField.setText("");
        confirmConsequentialCheck.setSelected(false);
    }

    private void updateWorkflowFieldStates() {
        BotsConfig.WorkflowKind kind = (BotsConfig.WorkflowKind) workflowKindCombo.getSelectedItem();
        boolean hasTarget = kind != BotsConfig.WorkflowKind.PROMPT;
        workflowTargetField.setEnabled(hasTarget);
        if (!hasTarget) {
            workflowTargetField.setText("");
        }
        boolean instructionsRequired = kind == BotsConfig.WorkflowKind.TOOL
                || kind == BotsConfig.WorkflowKind.PROMPT;
        instructionsLabel.setText(bundle.getString(instructionsRequired
                ? "InstructionsRequiredLabel"
                : "InstructionsOptionalLabel"));
    }

    private void saveCurrentRoutineAndConfig() {
        String botId = String.valueOf(botIdCombo.getSelectedItem()).trim();
        String persona = personaField.getText().trim();
        String routineId = routineIdField.getText().trim();
        if (routineId.isEmpty()) {
            JOptionPane.showMessageDialog(this, bundle.getString("RoutineIdRequiredMessage"),
                    bundle.getString("ValidationTitle"), JOptionPane.WARNING_MESSAGE);
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

        BotsConfig.WorkflowKind wfKind = (BotsConfig.WorkflowKind) workflowKindCombo.getSelectedItem();
        String wfTarget = wfKind == BotsConfig.WorkflowKind.PROMPT ? "" : workflowTargetField.getText().trim();
        String wfInstructions = instructionsArea.getText().trim();

        if ((wfKind == BotsConfig.WorkflowKind.SKILL || wfKind == BotsConfig.WorkflowKind.TOOL)
                && wfTarget.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    MessageFormat.format(bundle.getString("TargetRequiredMessage"), wfKind),
                    bundle.getString("ValidationTitle"),
                    JOptionPane.WARNING_MESSAGE);
            workflowTargetField.requestFocusInWindow();
            return;
        }
        if ((wfKind == BotsConfig.WorkflowKind.TOOL || wfKind == BotsConfig.WorkflowKind.PROMPT)
                && wfInstructions.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    MessageFormat.format(bundle.getString("InstructionsRequiredMessage"), wfKind),
                    bundle.getString("ValidationTitle"),
                    JOptionPane.WARNING_MESSAGE);
            instructionsArea.requestFocusInWindow();
            return;
        }

        BotsConfig.WorkflowSpec workflow = new BotsConfig.WorkflowSpec(
                wfKind,
                wfTarget,
                wfInstructions.isEmpty() ? null : wfInstructions,
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
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    bundle.getString("InvalidTriggerTitle"), JOptionPane.ERROR_MESSAGE);
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
            JOptionPane.showMessageDialog(this,
                    MessageFormat.format(bundle.getString("SaveConfigErrorMessage"), ex.getMessage()),
                    bundle.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
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
                HubManager.getInstance().fireEvent(new HubClient.BotStreamEvent(
                        "run", null, botId, routineId, ""));
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        MessageFormat.format(bundle.getString("RunNowSuccessMessage"), routineId),
                        bundle.getString("RunNowTitle"),
                        JOptionPane.INFORMATION_MESSAGE));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        MessageFormat.format(bundle.getString("RunNowErrorMessage"), routineId, ex.getMessage()),
                        bundle.getString("RunNowErrorTitle"),
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
        addSingleRow(panel, gbc, row, new JLabel(label), field, null);
    }

    private static void addSingleRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field, String tooltip) {
        addSingleRow(panel, gbc, row, new JLabel(label), field, tooltip);
    }

    private static void addSingleRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label, Component field) {
        addSingleRow(panel, gbc, row, label, field, null);
    }

    private static void addSingleRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label, Component field, String tooltip) {
        applyTooltip(label, field, tooltip);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
        gbc.gridwidth = 1;
    }

    private static void addFormRow(JPanel panel, GridBagConstraints gbc, int row,
                                   String label1, Component field1,
                                   String label2, Component field2) {
        addFormRow(panel, gbc, row, label1, field1, null, label2, field2, null);
    }

    private static void addFormRow(JPanel panel, GridBagConstraints gbc, int row,
                                   String label1, Component field1, String tooltip1,
                                   String label2, Component field2, String tooltip2) {
        JLabel jLabel1 = new JLabel(label1);
        applyTooltip(jLabel1, field1, tooltip1);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(jLabel1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.5;
        panel.add(field1, gbc);

        JLabel jLabel2 = new JLabel(label2);
        applyTooltip(jLabel2, field2, tooltip2);
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        panel.add(jLabel2, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        panel.add(field2, gbc);
    }

    private static void applyTooltip(JLabel label, Component field, String tooltip) {
        if (tooltip == null || tooltip.isBlank()) {
            return;
        }
        label.setToolTipText(tooltip);
        if (field instanceof JComponent jc) {
            jc.setToolTipText(tooltip);
        }
        if (field instanceof JSpinner spinner
                && spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setToolTipText(tooltip);
        } else if (field instanceof JComboBox<?> combo
                && combo.getEditor() != null
                && combo.getEditor().getEditorComponent() instanceof JComponent editorComp) {
            editorComp.setToolTipText(tooltip);
        }
    }
}
