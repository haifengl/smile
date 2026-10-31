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
package smile.studio;

import com.formdev.flatlaf.*;
import ioa.agent.email.EmailConfig;
import ioa.agent.email.EmailServices;
import ioa.agent.email.SharedSecrets;
import ioa.llm.Model;
import ioa.llm.client.LlmConfig;
import photos.sluice.secrets.SecretId;
import photos.sluice.secrets.SecretStatus;
import photos.sluice.secrets.SecretStore;
import photos.sluice.secrets.SecretStoreException;
import smile.studio.bot.HubManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 * The application preference and configuration dialog, managing both AI Service
 * settings ({@code llm.json} + {@link SecretStore}) and Email Account settings
 * ({@code email.json} + {@link SecretStore}).
 *
 * @author Haifeng Li
 */
public class SettingsDialog extends JDialog implements ActionListener {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SettingsDialog.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(SettingsDialog.class.getName(), Locale.getDefault());
    public static final String AI_SERVICE_KEY = "aiService";
    public static final String UI_THEME_KEY = "uiTheme";
    public static final String DEFAULT_MODEL_KEY = "defaultModel";
    private static final String API_KEY = "ApiKey";
    private static final String BASE_URL = "BaseUrl";
    private static final String MODEL = "Model";
    private static final String[] UI_THEMES = {"Light", "Dark"};
    // Interactions API is not yet supported on Vertex
    private static final String[] aiServiceOptions = {"OpenAI", "Azure OpenAI", "Anthropic", "Google Gemini", "Google Gemini Enterprise", "Chat Completions Compatible"};
    private static final String[] aiServiceKeys = {"openai", "azureOpenAI", "anthropic", "googleGemini", "googleEnterprise", "chatCompletions"};
    /** OpenAI / Azure — ids from {@link Model} family {@code gpt}. */
    private static final String[] openaiModels = modelIds("gpt");
    /** Anthropic — ids from {@link Model} family {@code claude}. */
    private static final String[] anthropicModels = modelIds("claude");
    /** Gemini — ids from {@link Model} family {@code gemini}. */
    private static final String[] geminiModels = modelIds("gemini");
    /**
     * Chat Completions–compatible / open-weight hosts — non-frontier families
     * registered in {@link Model}.
     */
    private static final String[] otherModels = Stream.of("qwen", "deepseek", "glm", "kimi", "minimax")
            .flatMap(family -> Model.family(family).stream())
            .map(Model::id)
            .toArray(String[]::new);

    private static String[] modelIds(String family) {
        return Model.family(family).stream().map(Model::id).toArray(String[]::new);
    }

    private static final String[] openaiBaseUrls = { "https://api.openai.com/v1" };
    private static final String[] anthropicBaseUrls = { "https://api.anthropic.com" };
    private static final String[] geminiBaseUrls = { "https://generativelanguage.googleapis.com" };
    private static final String[] otherBaseUrls = {
            "http://localhost:8888/api/v1", // SMILE Serve
            "http://localhost:11434/v1",    // Ollama
            "http://localhost:8080/v1",     // Llama.cpp or LocalAI
            "http://localhost:1234/v1",     // LM Studio
            "http://localhost:8000/v1",     // vLLM or SGLang
            "http://localhost:11434/v1",    // Ollama
            "https://api.orcarouter.ai/v1", // OrcaRouter
            "https://openrouter.ai/api/v1"  // OpenRouter
    };
    private final JComboBox<String> themeCombo = new JComboBox<>(UI_THEMES);
    private final JComboBox<String> aiServiceCombo = new JComboBox<>(aiServiceOptions);
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPane = new JPanel(cardLayout);
    private final Map<String, JTextField> apiKeyFields = new TreeMap<>();
    private final Map<String, String> initialSecrets = new TreeMap<>();
    private final Map<String, JComboBox<String>> baseUrlFields = new TreeMap<>();
    private final Map<String, JComboBox<String>> modelFields = new TreeMap<>();
    private final Preferences prefs;
    private final SecretStore secrets;

    // Agent Hub tab fields
    private final JCheckBox hubAutoStartCheck = new JCheckBox(bundle.getString("HubAutoStart"), true);
    private final JTextField hubHostField = new JTextField(StudioConfig.DEFAULT_HOST, 20);
    private final JSpinner hubPortSpinner =
            new JSpinner(new SpinnerNumberModel(StudioConfig.DEFAULT_HUB_PORT, 1, 65535, 1));
    private final JPasswordField hubAdminTokenField = new JPasswordField(24);
    private final JLabel hubAdminTokenStatusLabel = new JLabel();
    private final JCheckBox hubKeepAliveCheck = new JCheckBox(bundle.getString("HubKeepAliveOnExit"), false);

    // Inference Server tab fields
    private final JCheckBox serveAutoStartCheck = new JCheckBox(bundle.getString("ServeAutoStart"), false);
    private final JTextField serveHostField = new JTextField(StudioConfig.DEFAULT_HOST, 20);
    private final JSpinner servePortSpinner =
            new JSpinner(new SpinnerNumberModel(StudioConfig.DEFAULT_PORT, 1, 65535, 1));
    private final JTextField serveModelPathField = new JTextField(StudioConfig.DEFAULT_MODEL_PATH, 24);

    // Email Accounts tab state & fields
    private EmailConfig emailConfig;
    private final Map<String, String> pendingEmailSecrets = new LinkedHashMap<>();
    private boolean suppressEmailComboEvents = false;
    private String currentEmailAccountId = "";
    private final JComboBox<String> emailAccountCombo = new JComboBox<>();
    private final JCheckBox emailDefaultCheck = new JCheckBox(bundle.getString("EmailDefault"));
    private final JCheckBox emailEnabledCheck = new JCheckBox(bundle.getString("EmailEnabled"), true);
    private final JTextField emailAddressField = new JTextField(22);
    private final JTextField emailDescriptionField = new JTextField(22);
    private final JComboBox<EmailConfig.Provider> emailProviderCombo =
            new JComboBox<>(EmailConfig.Provider.values());
    private final JSpinner emailPollSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 86400, 15));
    private final JTextField emailFolderField = new JTextField("INBOX", 14);
    private final JTextField emailClientIdField = new JTextField(24);
    private final JTextField emailTenantIdField = new JTextField("common", 14);
    private final JTextField emailImapHostField = new JTextField(18);
    private final JSpinner emailImapPortSpinner = new JSpinner(new SpinnerNumberModel(993, 1, 65535, 1));
    private final JTextField emailSmtpHostField = new JTextField(18);
    private final JSpinner emailSmtpPortSpinner = new JSpinner(new SpinnerNumberModel(465, 1, 65535, 1));
    private final JCheckBox emailStartTlsCheck = new JCheckBox(bundle.getString("EmailStartTls"), false);
    private final JPasswordField emailSecretField = new JPasswordField(24);
    private final JLabel emailSecretStatusLabel = new JLabel();
    private final JButton emailOAuthButton = new JButton(bundle.getString("EmailOAuthSignIn"));
    private final JButton emailTestButton = new JButton(bundle.getString("EmailTestConnection"));

    /**
     * Constructor using the default application secret store.
     * @param owner the Frame from which the dialog is displayed
     *              or null if this dialog has no owner.
     * @param prefs the application preference and configuration data.
     */
    public SettingsDialog(Frame owner, Preferences prefs) {
        this(owner, prefs, SmileStudio.secrets());
    }

    /**
     * Constructor.
     * @param owner the Frame from which the dialog is displayed
     *              or null if this dialog has no owner.
     * @param prefs the application preference and configuration data.
     * @param secrets the application credential store for API keys.
     */
    public SettingsDialog(Frame owner, Preferences prefs, SecretStore secrets) {
        super(owner, bundle.getString("Title"), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        this.prefs = prefs;
        this.secrets = secrets;

        LlmServices.migratePreferencesSecrets(prefs, secrets);
        LlmConfig llmCfg = LlmServices.loadOrMigrateConfig(prefs);
        LlmServices.syncToPreferences(llmCfg, prefs);
        this.emailConfig = EmailConfig.load();
        for (EmailConfig.AccountSpec acc : emailConfig.accounts()) {
            SharedSecrets.resolveEmailSecret(secrets, acc.emailAddress())
                    .ifPresent(sec -> pendingEmailSecrets.put(acc.emailAddress(), sec));
        }

        themeCombo.setSelectedItem(prefs.get(UI_THEME_KEY, "Dark"));
        themeCombo.addActionListener(this);
        aiServiceCombo.addActionListener(this);

        // Add the panels to the dynamic panel with unique names
        for (int i = 0; i < aiServiceOptions.length; i++) {
            cardPane.add(createServiceCard(aiServiceKeys[i]), aiServiceOptions[i]);
        }
        aiServiceCombo.setSelectedItem(prefs.get(AI_SERVICE_KEY, aiServiceOptions[0]));

        JPanel aiTab = new JPanel(new BorderLayout());
        aiTab.add(createServiceChoice(), BorderLayout.NORTH);
        aiTab.add(cardPane, BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(6, 6, 0, 6));
        tabs.addTab(bundle.getString("TabGeneralAI"), aiTab);
        tabs.addTab(bundle.getString("TabAgentHub"), createAgentHubPane());
        tabs.addTab(bundle.getString("TabInferenceServer"), createInferenceServerPane());
        tabs.addTab(bundle.getString("TabEmailAccounts"), createEmailAccountsPane());

        add(tabs, BorderLayout.CENTER);
        add(createButtonPane(), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel createServiceChoice() {
        JPanel pane = new JPanel(new GridBagLayout());
        pane.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 0: UI Theme
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel themeLabel = new JLabel(bundle.getString("Theme"));
        pane.add(themeLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        pane.add(themeLabel.getLabelFor() == null ? themeCombo : themeCombo, gbc);

        // Row 1: AI Service
        gbc.gridx = 0; // Column 0
        gbc.gridy = 1; // Row 1
        gbc.fill = GridBagConstraints.NONE; // Reset fill for label
        gbc.weightx = 0.0; // Reset weightx for label
        JLabel serviceLabel = new JLabel(bundle.getString("Service"));
        pane.add(serviceLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // Allow text field to take extra horizontal space
        pane.add(aiServiceCombo, gbc);

        return pane;
    }

    private JPanel createServiceCard(String service) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        Optional<SecretId> secretIdOpt = LlmServices.secretId(service);
        int row = 0;

        // Row 0: API Key (or Project for googleEnterprise)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel apiKeyLabel = new JLabel(bundle.getString(service.equals("googleEnterprise") ? "Project" : "APIKey"));
        card.add(apiKeyLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        if (secretIdOpt.isPresent()) {
            SecretId id = secretIdOpt.get();
            JPasswordField passwordField = new JPasswordField(25);
            passwordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
            String existingSecret = LlmServices.readSecret(secrets, id).orElse("");
            passwordField.setText(existingSecret);
            initialSecrets.put(service, existingSecret);
            apiKeyFields.put(service, passwordField);
            card.add(passwordField, gbc);

            // Row 1: Storage tier status label
            row++;
            gbc.gridx = 1;
            gbc.gridy = row;
            gbc.insets = new Insets(0, 5, 5, 5);
            JLabel statusLabel = new JLabel(formatSecretStatus(secrets, id));
            statusLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
            statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            card.add(statusLabel, gbc);
            gbc.insets = new Insets(5, 5, 5, 5);
        } else {
            JTextField projectField = new JTextField(25);
            projectField.setText(prefs.get(service + API_KEY, ""));
            apiKeyFields.put(service, projectField);
            card.add(projectField, gbc);
        }

        // Next Row: Base URL (or Location for googleEnterprise)
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        JLabel baseUrlLabel = new JLabel(bundle.getString(service.equals("googleEnterprise") ? "Location" : "BaseUrl"));
        card.add(baseUrlLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JComboBox<String> baseUrlField = switch (service) {
            case "openai" -> new JComboBox<>(openaiBaseUrls);
            case "anthropic" -> new JComboBox<>(anthropicBaseUrls);
            case "googleGemini" -> new JComboBox<>(geminiBaseUrls);
            case "chatCompletions" -> new JComboBox<>(otherBaseUrls);
            default -> new JComboBox<>();
        };
        baseUrlField.setEditable(true);
        baseUrlField.setSelectedItem(prefs.get(service + BASE_URL, ""));
        baseUrlFields.put(service, baseUrlField);
        card.add(baseUrlField, gbc);

        // Next Row: Model
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        JLabel modelLabel = new JLabel(bundle.getString("Model"));
        card.add(modelLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JComboBox<String> modelField = switch (service) {
            case "openai", "azureOpenAI" -> new JComboBox<>(openaiModels);
            case "anthropic" -> new JComboBox<>(anthropicModels);
            case "googleGemini", "googleEnterprise" -> new JComboBox<>(geminiModels);
            case "chatCompletions" -> new JComboBox<>(otherModels);
            default -> new JComboBox<>();
        };
        modelField.setEditable(true);
        // Comma-separated list; first id is the default for this service.
        modelField.setSelectedItem(prefs.get(service + MODEL, ""));
        modelField.setToolTipText(bundle.getString("Model"));
        modelFields.put(service, modelField);
        card.add(modelField, gbc);

        return card;
    }

    private JPanel createAgentHubPane() {
        StudioConfig.AgentHub cfg = StudioConfig.agentHub(secrets);
        hubAutoStartCheck.setSelected(cfg.autoStart());
        hubHostField.setText(cfg.host());
        hubPortSpinner.setValue(cfg.port());
        hubAdminTokenField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        hubAdminTokenField.setText(cfg.adminToken());
        hubAdminTokenStatusLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        hubAdminTokenStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        hubAdminTokenStatusLabel.setText(
                formatSecretStatus(secrets, StudioConfig.AGENT_HUB_ADMIN_TOKEN_SECRET));
        hubKeepAliveCheck.setSelected(cfg.keepAliveOnExit());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Row 0: Auto Start
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(hubAutoStartCheck, gbc);
        gbc.gridwidth = 1;

        // Row 1: Host
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("HubHost")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(hubHostField, gbc);
        row++;

        // Row 2: Port
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("HubPort")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        JSpinner.NumberEditor hubPortEditor = new JSpinner.NumberEditor(hubPortSpinner, "#");
        hubPortSpinner.setEditor(hubPortEditor);
        form.add(hubPortSpinner, gbc);
        row++;

        // Row 3: Admin Token
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("HubAdminToken")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(hubAdminTokenField, gbc);
        row++;

        // Row 4: Admin Token SecretStore status
        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.insets = new Insets(0, 5, 5, 5);
        form.add(hubAdminTokenStatusLabel, gbc);
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 5: Keep Alive On Exit
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(hubKeepAliveCheck, gbc);
        gbc.gridwidth = 1;

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel createInferenceServerPane() {
        StudioConfig.InferenceServer cfg = StudioConfig.inferenceServer();
        serveAutoStartCheck.setSelected(cfg.autoStart());
        serveHostField.setText(cfg.host());
        servePortSpinner.setValue(cfg.port());
        serveModelPathField.setText(cfg.modelPath());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Row 0: Auto Start
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(serveAutoStartCheck, gbc);
        gbc.gridwidth = 1;

        // Row 1: Host
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("ServeHost")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(serveHostField, gbc);
        row++;

        // Row 2: Port
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("ServePort")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        JSpinner.NumberEditor servePortEditor = new JSpinner.NumberEditor(servePortSpinner, "#");
        servePortSpinner.setEditor(servePortEditor);
        form.add(servePortSpinner, gbc);
        row++;

        // Row 3: Model Path
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("ServeModelPath")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JPanel modelPathRow = new JPanel(new BorderLayout(6, 0));
        modelPathRow.add(serveModelPathField, BorderLayout.CENTER);
        JButton browseBtn = new JButton(bundle.getString("ServeBrowse"));
        browseBtn.addActionListener(e -> browseServeModelPath());
        modelPathRow.add(browseBtn, BorderLayout.EAST);
        form.add(modelPathRow, gbc);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        return wrapper;
    }

    private void browseServeModelPath() {
        JFileChooser chooser = new JFileChooser(serveModelPathField.getText().trim());
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                && chooser.getSelectedFile() != null) {
            serveModelPathField.setText(chooser.getSelectedFile().getPath());
        }
    }

    private void saveStudioConfiguration() {
        String serveHost = serveHostField.getText().trim();
        int servePort = ((Number) servePortSpinner.getValue()).intValue();
        String serveModelPath = serveModelPathField.getText().trim();
        StudioConfig.InferenceServer inferenceServer = new StudioConfig.InferenceServer(
                serveAutoStartCheck.isSelected(),
                serveHost.isEmpty() ? StudioConfig.DEFAULT_HOST : serveHost,
                servePort > 0 ? servePort : StudioConfig.DEFAULT_PORT,
                serveModelPath.isEmpty() ? StudioConfig.DEFAULT_MODEL_PATH : serveModelPath
        );

        String hubHost = hubHostField.getText().trim();
        int hubPort = ((Number) hubPortSpinner.getValue()).intValue();
        String hubToken = new String(hubAdminTokenField.getPassword()).strip();
        StudioConfig.AgentHub agentHub = new StudioConfig.AgentHub(
                hubAutoStartCheck.isSelected(),
                hubHost.isEmpty() ? StudioConfig.DEFAULT_HOST : hubHost,
                hubPort > 0 ? hubPort : StudioConfig.DEFAULT_HUB_PORT,
                hubToken.isEmpty() ? StudioConfig.DEFAULT_HUB_ADMIN_TOKEN : hubToken,
                hubKeepAliveCheck.isSelected()
        );

        StudioConfig.save(inferenceServer, agentHub, secrets);
        HubManager.getInstance().refreshClient(agentHub);
    }

    private JPanel createEmailAccountsPane() {
        JPanel pane = new JPanel(new BorderLayout(6, 6));
        pane.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Top account selector bar
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topBar.add(new JLabel(bundle.getString("EmailAccount")));
        emailAccountCombo.setPreferredSize(new Dimension(190, 26));
        emailAccountCombo.addActionListener(e -> {
            if (!suppressEmailComboEvents) {
                commitCurrentEmailAccountForm();
                String selectedId = (String) emailAccountCombo.getSelectedItem();
                if (selectedId != null) {
                    loadEmailAccountForm(selectedId);
                }
            }
        });
        topBar.add(emailAccountCombo);

        JButton addBtn = new JButton(bundle.getString("EmailAdd"));
        addBtn.addActionListener(e -> addNewEmailAccount());
        JButton removeBtn = new JButton(bundle.getString("EmailRemove"));
        removeBtn.addActionListener(e -> removeSelectedEmailAccount());
        topBar.add(addBtn);
        topBar.add(removeBtn);
        topBar.add(emailDefaultCheck);
        topBar.add(emailEnabledCheck);
        pane.add(topBar, BorderLayout.NORTH);

        // Form grid
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;
        addEmailRow(form, gbc, r++,
                bundle.getString("EmailAddress"), emailAddressField,
                bundle.getString("EmailProvider"), emailProviderCombo);
        addEmailRow(form, gbc, r++,
                bundle.getString("EmailDescription"), emailDescriptionField,
                bundle.getString("EmailFolder"), emailFolderField);
        addEmailRow(form, gbc, r++,
                bundle.getString("EmailPollInterval"), emailPollSpinner,
                bundle.getString("EmailTenantId"), emailTenantIdField);
        addEmailSingleRow(form, gbc, r++,
                bundle.getString("EmailClientId"), emailClientIdField);
        addEmailRow(form, gbc, r++,
                bundle.getString("EmailImapHost"), emailImapHostField,
                bundle.getString("EmailImapPort"), emailImapPortSpinner);
        addEmailRow(form, gbc, r++,
                bundle.getString("EmailSmtpHost"), emailSmtpHostField,
                bundle.getString("EmailSmtpPort"), emailSmtpPortSpinner);

        gbc.gridx = 1;
        gbc.gridy = r++;
        gbc.gridwidth = 3;
        form.add(emailStartTlsCheck, gbc);
        gbc.gridwidth = 1;

        emailSecretField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        gbc.gridx = 0;
        gbc.gridy = r;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("EmailSecret")), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        form.add(emailSecretField, gbc);
        gbc.gridwidth = 1;
        r++;

        emailSecretStatusLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        emailSecretStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        gbc.gridx = 1;
        gbc.gridy = r++;
        gbc.gridwidth = 3;
        form.add(emailSecretStatusLabel, gbc);
        gbc.gridwidth = 1;

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        emailOAuthButton.addActionListener(e -> launchEmailOAuthSignIn());
        emailTestButton.addActionListener(e -> testCurrentEmailConnection());
        actionRow.add(emailOAuthButton);
        actionRow.add(emailTestButton);

        gbc.gridx = 1;
        gbc.gridy = r;
        gbc.gridwidth = 3;
        form.add(actionRow, gbc);
        gbc.gridwidth = 1;

        emailProviderCombo.addActionListener(e -> updateEmailProviderFieldStates());
        emailSmtpPortSpinner.addChangeListener(e -> {
            int p = ((Number) emailSmtpPortSpinner.getValue()).intValue();
            if (p == 465) {
                emailStartTlsCheck.setSelected(false);
            } else if (p == 587) {
                emailStartTlsCheck.setSelected(true);
            }
        });

        pane.add(form, BorderLayout.CENTER);
        refreshEmailAccountCombo(emailConfig.defaultAccount());
        return pane;
    }

    private static void addEmailRow(
            JPanel form,
            GridBagConstraints gbc,
            int row,
            String label1,
            JComponent comp1,
            String label2,
            JComponent comp2
    ) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        form.add(new JLabel(label1), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.5;
        form.add(comp1, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        form.add(new JLabel(label2), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        form.add(comp2, gbc);
    }

    private static void addEmailSingleRow(
            JPanel form,
            GridBagConstraints gbc,
            int row,
            String label,
            JComponent comp
    ) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        form.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        form.add(comp, gbc);
        gbc.gridwidth = 1;
    }

    private void refreshEmailAccountCombo(String selectId) {
        suppressEmailComboEvents = true;
        try {
            emailAccountCombo.removeAllItems();
            for (EmailConfig.AccountSpec acc : emailConfig.accounts()) {
                emailAccountCombo.addItem(acc.emailAddress());
            }
            if (!emailConfig.accounts().isEmpty()) {
                String target = (selectId != null && !selectId.isBlank())
                        ? selectId
                        : emailConfig.accounts().getFirst().emailAddress();
                emailAccountCombo.setSelectedItem(target);
                loadEmailAccountForm(target);
            } else {
                currentEmailAccountId = "";
                clearEmailAccountForm();
            }
        } finally {
            suppressEmailComboEvents = false;
        }
    }

    private void clearEmailAccountForm() {
        emailAddressField.setText("");
        emailDescriptionField.setText("");
        emailProviderCombo.setSelectedItem(EmailConfig.Provider.GMAIL);
        emailPollSpinner.setValue(60);
        emailFolderField.setText("INBOX");
        emailClientIdField.setText("");
        emailTenantIdField.setText("common");
        emailImapHostField.setText("imap.gmail.com");
        emailImapPortSpinner.setValue(993);
        emailSmtpHostField.setText("smtp.gmail.com");
        emailSmtpPortSpinner.setValue(465);
        emailStartTlsCheck.setSelected(false);
        emailDefaultCheck.setSelected(true);
        emailEnabledCheck.setSelected(true);
        emailSecretField.setText("");
        emailSecretStatusLabel.setText(formatSecretStatus(secrets, SharedSecrets.emailSecretId("default")));
        updateEmailProviderFieldStates();
    }

    private void loadEmailAccountForm(String accountId) {
        Optional<EmailConfig.AccountSpec> opt = emailConfig.findAccount(accountId);
        if (opt.isEmpty()) {
            clearEmailAccountForm();
            return;
        }
        EmailConfig.AccountSpec acc = opt.get();
        currentEmailAccountId = acc.emailAddress();
        emailAddressField.setText(acc.emailAddress());
        emailDescriptionField.setText(acc.description() != null ? acc.description() : "");
        emailProviderCombo.setSelectedItem(acc.provider());
        emailPollSpinner.setValue(acc.pollIntervalSeconds());
        emailFolderField.setText(acc.folder());
        emailClientIdField.setText(acc.clientId() != null ? acc.clientId() : "");
        emailTenantIdField.setText(acc.tenantId() != null ? acc.tenantId() : "common");
        emailImapHostField.setText(acc.imapHost() != null ? acc.imapHost() : "");
        emailImapPortSpinner.setValue(acc.imapPort());
        emailSmtpHostField.setText(acc.smtpHost() != null ? acc.smtpHost() : "");
        emailSmtpPortSpinner.setValue(acc.smtpPort());
        emailStartTlsCheck.setSelected(acc.smtpStartTls());
        emailDefaultCheck.setSelected(acc.emailAddress().equalsIgnoreCase(emailConfig.defaultAccount()));
        emailEnabledCheck.setSelected(acc.enabled());

        String secret = pendingEmailSecrets.getOrDefault(
                acc.emailAddress(),
                SharedSecrets.resolveEmailSecret(secrets, acc.emailAddress()).orElse("")
        );
        emailSecretField.setText(secret);
        emailSecretStatusLabel.setText(formatSecretStatus(secrets, SharedSecrets.emailSecretId(acc.emailAddress())));
        updateEmailProviderFieldStates();
    }

    private void updateEmailProviderFieldStates() {
        EmailConfig.Provider provider = (EmailConfig.Provider) emailProviderCombo.getSelectedItem();
        boolean isOAuth = provider == EmailConfig.Provider.GMAIL || provider == EmailConfig.Provider.MSGRAPH;
        boolean isMsGraph = provider == EmailConfig.Provider.MSGRAPH;
        boolean isImap = provider == EmailConfig.Provider.IMAP;

        emailClientIdField.setEnabled(isOAuth);
        emailTenantIdField.setEnabled(isMsGraph);
        emailOAuthButton.setEnabled(isOAuth);
        emailImapHostField.setEnabled(isImap);
        emailImapPortSpinner.setEnabled(isImap);
        emailSmtpHostField.setEnabled(isImap);
        emailSmtpPortSpinner.setEnabled(isImap);
        emailStartTlsCheck.setEnabled(isImap);
    }

    private EmailConfig.AccountSpec buildSpecFromEmailForm() {
        String addr = emailAddressField.getText().trim();
        String email = !addr.isEmpty() ? addr : currentEmailAccountId;
        if (email.isEmpty()) {
            return null;
        }
        EmailConfig.Provider provider = (EmailConfig.Provider) emailProviderCombo.getSelectedItem();
        String description = emailDescriptionField.getText().trim();
        int pollSec = ((Number) emailPollSpinner.getValue()).intValue();
        String folder = emailFolderField.getText().trim();
        String clientId = emailClientIdField.getText().trim();
        String tenantId = emailTenantIdField.getText().trim();
        String imapHost = emailImapHostField.getText().trim();
        int imapPort = ((Number) emailImapPortSpinner.getValue()).intValue();
        boolean imapSsl = imapPort != 143;
        String smtpHost = emailSmtpHostField.getText().trim();
        int smtpPort = ((Number) emailSmtpPortSpinner.getValue()).intValue();
        boolean startTls = smtpPort != 465 && (smtpPort == 587 || emailStartTlsCheck.isSelected());
        boolean enabled = emailEnabledCheck.isSelected();

        return new EmailConfig.AccountSpec(
                email,
                description.isEmpty() ? null : description,
                provider,
                enabled,
                pollSec,
                folder.isEmpty() ? "INBOX" : folder,
                provider == EmailConfig.Provider.GMAIL ? "label:INBOX" : null,
                clientId.isEmpty() ? null : clientId,
                tenantId.isEmpty() ? "common" : tenantId,
                imapHost.isEmpty() ? null : imapHost,
                imapPort,
                imapSsl,
                smtpHost.isEmpty() ? null : smtpHost,
                smtpPort,
                startTls
        );
    }

    private void commitCurrentEmailAccountForm() {
        EmailConfig.AccountSpec spec = buildSpecFromEmailForm();
        if (spec == null) {
            return;
        }
        if (!currentEmailAccountId.isEmpty() && !currentEmailAccountId.equalsIgnoreCase(spec.emailAddress())) {
            emailConfig = emailConfig.withoutAccount(currentEmailAccountId);
            pendingEmailSecrets.remove(currentEmailAccountId);
        }
        emailConfig = emailConfig.withUpsertedAccount(spec);
        if (emailDefaultCheck.isSelected()) {
            emailConfig = new EmailConfig(spec.emailAddress(), emailConfig.accounts());
        }
        String secretVal = new String(emailSecretField.getPassword()).strip();
        pendingEmailSecrets.put(spec.emailAddress(), secretVal);
        currentEmailAccountId = spec.emailAddress();
    }

    private void addNewEmailAccount() {
        commitCurrentEmailAccountForm();
        suppressEmailComboEvents = true;
        try {
            currentEmailAccountId = "";
            clearEmailAccountForm();
            emailAddressField.requestFocusInWindow();
        } finally {
            suppressEmailComboEvents = false;
        }
    }

    private void removeSelectedEmailAccount() {
        String id = !emailAddressField.getText().trim().isEmpty()
                ? emailAddressField.getText().trim()
                : currentEmailAccountId;
        if (id.isEmpty()) {
            return;
        }
        emailConfig = emailConfig.withoutAccount(id);
        pendingEmailSecrets.remove(id);
        SharedSecrets.deleteEmailSecret(secrets, id);
        refreshEmailAccountCombo(emailConfig.defaultAccount());
    }

    private void launchEmailOAuthSignIn() {
        commitCurrentEmailAccountForm();
        EmailConfig.AccountSpec spec = buildSpecFromEmailForm();
        if (spec == null) {
            return;
        }
        emailOAuthButton.setEnabled(false);
        Thread.ofVirtual().name("studio-email-oauth").start(() -> {
            try {
                EmailOAuthFlow.OAuthResult result = EmailOAuthFlow.authorizePkceLoopback(
                        spec, null, Duration.ofMinutes(3));
                SwingUtilities.invokeLater(() -> {
                    emailSecretField.setText(result.refreshToken());
                    pendingEmailSecrets.put(spec.emailAddress(), result.refreshToken());
                    SharedSecrets.saveEmailSecret(secrets, spec.emailAddress(), result.refreshToken());
                    emailSecretStatusLabel.setText(
                            formatSecretStatus(secrets, SharedSecrets.emailSecretId(spec.emailAddress())));
                    emailOAuthButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    emailOAuthButton.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            MessageFormat.format(bundle.getString("EmailTestFailure"), ex.getMessage()),
                            bundle.getString("Title"),
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void testCurrentEmailConnection() {
        commitCurrentEmailAccountForm();
        EmailConfig.AccountSpec spec = buildSpecFromEmailForm();
        if (spec == null) {
            return;
        }
        String secretVal = new String(emailSecretField.getPassword()).strip();
        emailTestButton.setEnabled(false);
        Thread.ofVirtual().name("studio-email-test").start(() -> {
            try {
                var client = EmailServices.createDefaultClient(
                        spec,
                        id -> secretVal.isEmpty()
                                ? SharedSecrets.resolveEmailSecret(secrets, id)
                                : Optional.of(secretVal)
                );
                String status = client.testConnection();
                SwingUtilities.invokeLater(() -> {
                    emailTestButton.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            MessageFormat.format(bundle.getString("EmailTestSuccess"), status),
                            bundle.getString("Title"),
                            JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    emailTestButton.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            MessageFormat.format(bundle.getString("EmailTestFailure"), ex.getMessage()),
                            bundle.getString("Title"),
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void saveEmailConfiguration() {
        commitCurrentEmailAccountForm();
        emailConfig.save();
        for (EmailConfig.AccountSpec acc : emailConfig.accounts()) {
            String sec = pendingEmailSecrets.getOrDefault(acc.emailAddress(), "");
            SharedSecrets.saveEmailSecret(secrets, acc.emailAddress(), sec);
        }
        EmailServices.getInstance().reload(emailConfig, secrets);
        if (HubManager.getInstance().isRunning()) {
            Thread.ofVirtual().name("hub-email-reload").start(() -> {
                try {
                    var overview = HubManager.getInstance().client().getOverview();
                    if (overview != null && overview.config() != null) {
                        HubManager.getInstance().client().saveConfig(overview.config());
                    }
                } catch (Exception ex) {
                    logger.debug("Could not notify Agent Hub after saving email config: {}", ex.getMessage());
                }
            });
        }
    }

    /**
     * Formats a user-facing description of where a secret is stored or would be saved.
     * @param secrets the secret store.
     * @param id the secret identifier.
     * @return the localized status text.
     */
    static String formatSecretStatus(SecretStore secrets, SecretId id) {
        if (secrets == null) {
            return bundle.getString("StatusUnavailable");
        }
        try {
            return switch (secrets.status(id)) {
                case SecretStatus.InEnvironment(String envVar) ->
                        MessageFormat.format(bundle.getString("StatusInEnvironment"), envVar);
                case SecretStatus.InKeyring _ ->
                        bundle.getString("StatusInKeyring");
                case SecretStatus.InFile _ ->
                        bundle.getString("StatusInFile");
                case SecretStatus.Absent _ -> {
                    var target = secrets.whereASaveWouldStoreIt();
                    if (target.isEmpty()) {
                        yield bundle.getString("StatusUnavailable");
                    }
                    yield switch (target.get()) {
                        case SecretStatus.InKeyring _ -> bundle.getString("StatusAbsentKeyring");
                        case SecretStatus.InFile _ -> bundle.getString("StatusAbsentFile");
                    };
                }
            };
        } catch (SecretStoreException ex) {
            logger.warn("Failed to check status for secret '{}': {}", id.name(), ex.getMessage());
            return bundle.getString("StatusUnavailable");
        }
    }

    private JPanel createButtonPane() {
        JPanel pane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pane.setBorder(new EmptyBorder(0, 0, 0, 10));
        JButton okButton = new JButton(bundle.getString("OK"));
        JButton cancelButton = new JButton(bundle.getString("Cancel"));
        pane.add(okButton);
        pane.add(cancelButton);
        getRootPane().setDefaultButton(okButton);

        okButton.addActionListener((e) -> {
            prefs.put(UI_THEME_KEY, (String) themeCombo.getSelectedItem());
            String selectedServiceLabel = aiServiceOptions[aiServiceCombo.getSelectedIndex()];
            prefs.put(AI_SERVICE_KEY, selectedServiceLabel);
            for (int i = 0; i < aiServiceKeys.length; i++) {
                String service = aiServiceKeys[i];
                String label = aiServiceOptions[i];
                Optional<SecretId> secretIdOpt = LlmServices.secretId(service);
                if (secretIdOpt.isPresent()) {
                    saveServiceSecret(service, label, secretIdOpt.get());
                } else {
                    prefs.put(service + API_KEY, apiKeyFields.get(service).getText());
                }
                Object baseUrlItem = baseUrlFields.get(service).getSelectedItem();
                Object modelItem = modelFields.get(service).getSelectedItem();
                prefs.put(service + BASE_URL, baseUrlItem == null ? "" : baseUrlItem.toString());
                prefs.put(service + MODEL, modelItem == null ? "" : modelItem.toString());
            }
            LlmConfig.fromPreferences(prefs).ifPresent(LlmConfig::save);
            saveStudioConfiguration();
            saveEmailConfiguration();
            SmileStudio.updateLLM();
            SmileStudio.refreshModelSelectors();
            dispose();
        });

        cancelButton.addActionListener((e) -> dispose());
        return pane;
    }

    private void saveServiceSecret(String service, String label, SecretId id) {
        prefs.remove(service + API_KEY);
        if (secrets == null) {
            return;
        }
        JTextField field = apiKeyFields.get(service);
        String value = (field instanceof JPasswordField pf
                ? new String(pf.getPassword())
                : field.getText()).strip();
        String initial = initialSecrets.getOrDefault(service, "").strip();
        try {
            if (value.isEmpty()) {
                if (!initial.isEmpty()) {
                    secrets.remove(id);
                }
            } else if (!value.equals(initial)) {
                secrets.save(id, value);
            }
        } catch (IllegalArgumentException | SecretStoreException ex) {
            logger.error("Failed to save API key for {}: {}", label, ex.getMessage());
            JOptionPane.showMessageDialog(
                    this,
                    MessageFormat.format(bundle.getString("SecretSaveError"), label, ex.getMessage()),
                    bundle.getString("Title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == themeCombo) {
            String theme = (String) themeCombo.getSelectedItem();
            prefs.put(UI_THEME_KEY, theme);
            try {
                if ("Dark".equals(theme)) {
                    UIManager.setLookAndFeel(new FlatDarculaLaf());
                } else {
                    UIManager.setLookAndFeel(new FlatIntelliJLaf());
                }

                // Tells FlatLaf to refresh all open frames and dialogs
                FlatLaf.updateUI();
            } catch (UnsupportedLookAndFeelException ex) {
                logger.error("Failed to setup L&F: {}", ex.getMessage());
            }
        } else if (e.getSource() == aiServiceCombo) {
            String selectedOption = (String) aiServiceCombo.getSelectedItem();

            // Tell the CardLayout to show the panel corresponding to the selected option
            cardLayout.show(cardPane, selectedOption);

            // Repaint and revalidate the container to ensure correct display
            cardPane.revalidate();
            cardPane.repaint();
            // Call pack() if the new panel has a different preferred size
            pack();
        }
    }
}
