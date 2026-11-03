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
import ioa.agent.calendar.CalendarConfig;
import ioa.agent.calendar.CalendarServices;
import ioa.agent.email.EmailConfig;
import ioa.agent.email.EmailServices;
import ioa.agent.email.OAuthFlow;
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
    private LlmConfig llmConfig;
    private final Map<String, String> pendingLlmSecrets = new LinkedHashMap<>();
    private final Map<String, String> initialSecrets = new LinkedHashMap<>();
    private boolean suppressLlmComboEvents = false;
    private String currentLlmServiceName = "";
    private final JComboBox<String> llmServiceCombo = new JComboBox<>();
    private final JCheckBox llmDefaultCheck = new JCheckBox(bundle.getString("ServiceDefault"));
    private final JTextField llmServiceNameField = new JTextField(18);
    private final JComboBox<String> llmProviderCombo = new JComboBox<>(aiServiceOptions);
    private final JLabel llmApiKeyLabel = new JLabel(bundle.getString("APIKey"));
    private final CardLayout llmCredentialCardLayout = new CardLayout();
    private final JPanel llmCredentialHolder = new JPanel(llmCredentialCardLayout);
    private final JPasswordField llmApiKeyField = new JPasswordField(24);
    private final JTextField llmProjectField = new JTextField(24);
    private final JLabel llmSecretStatusLabel = new JLabel();
    private final JLabel llmBaseUrlLabel = new JLabel(bundle.getString("BaseUrl"));
    private final JComboBox<String> llmBaseUrlField = new JComboBox<>();
    private final JComboBox<String> llmModelField = new JComboBox<>();
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
    private final JTextField serveLlmField = new JTextField(StudioConfig.DEFAULT_LLM, 24);

    // Email Accounts tab state & fields
    private EmailConfig emailConfig;
    private final Map<String, String> pendingEmailSecrets = new LinkedHashMap<>();
    private boolean suppressEmailComboEvents = false;
    private String currentEmailAccountId = "";
    private final JComboBox<String> emailAccountCombo = new JComboBox<>();
    private final JCheckBox emailDefaultCheck = new JCheckBox(bundle.getString("EmailDefault"));
    private final JCheckBox emailEnabledCheck = new JCheckBox(bundle.getString("EmailEnabled"), true);
    private final JTextField emailAddressField = new JTextField(22);
    private final JTextField emailSenderNameField = new JTextField(18);
    private final JTextField emailDescriptionField = new JTextField(22);
    private final JComboBox<EmailConfig.Provider> emailProviderCombo =
            new JComboBox<>(EmailConfig.Provider.values());
    private final JSpinner emailPollSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 86400, 15));
    private final JTextField emailFolderField = new JTextField("INBOX", 14);
    private final JTextField emailImapHostField = new JTextField(18);
    private final JSpinner emailImapPortSpinner = new JSpinner(new SpinnerNumberModel(993, 1, 65535, 1));
    private final JTextField emailSmtpHostField = new JTextField(18);
    private final JSpinner emailSmtpPortSpinner = new JSpinner(new SpinnerNumberModel(465, 1, 65535, 1));
    private final JCheckBox emailStartTlsCheck = new JCheckBox(bundle.getString("EmailStartTls"), false);
    private final JPasswordField emailSecretField = new JPasswordField(24);
    private final JLabel emailSecretStatusLabel = new JLabel();
    private final JButton emailLoginGoogleButton = new JButton(bundle.getString("OAuthLoginWithGoogle"));
    private final JButton emailLoginMicrosoftButton = new JButton(bundle.getString("OAuthLoginWithMicrosoft"));
    private final JButton emailTestButton = new JButton(bundle.getString("EmailTestConnection"));

    // Calendar Accounts tab state & fields
    private CalendarConfig calendarConfig;
    private final Map<String, String> pendingCalendarSecrets = new LinkedHashMap<>();
    private boolean suppressCalendarComboEvents = false;
    private String currentCalendarAccountId = "";
    private final JComboBox<String> calendarAccountCombo = new JComboBox<>();
    private final JCheckBox calendarDefaultCheck = new JCheckBox(bundle.getString("CalendarDefault"));
    private final JCheckBox calendarEnabledCheck = new JCheckBox(bundle.getString("CalendarEnabled"), true);
    private final JTextField calendarAccountIdField = new JTextField(22);
    private final JComboBox<CalendarConfig.Provider> calendarProviderCombo =
            new JComboBox<>(new CalendarConfig.Provider[]{
                    CalendarConfig.Provider.GOOGLE,
                    CalendarConfig.Provider.MSGRAPH,
                    CalendarConfig.Provider.ICS
            });
    private final JTextField calendarIdField = new JTextField("primary", 18);
    private final JTextField calendarDescriptionField = new JTextField(22);
    private final JSpinner calendarPollSpinner = new JSpinner(new SpinnerNumberModel(300, 15, 86400, 30));
    private final JCheckBox calendarUseEmailCheck = new JCheckBox(bundle.getString("CalendarUseEmailAccount"), true);
    private final JPasswordField calendarSecretField = new JPasswordField(24);
    private final JLabel calendarSecretStatusLabel = new JLabel();
    private final JButton calendarLoginGoogleButton = new JButton(bundle.getString("OAuthLoginWithGoogle"));
    private final JButton calendarLoginMicrosoftButton = new JButton(bundle.getString("OAuthLoginWithMicrosoft"));
    private final JButton calendarTestButton = new JButton(bundle.getString("CalendarTestConnection"));

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
        this.llmConfig = LlmServices.loadOrMigrateConfig(prefs);
        if (this.llmConfig.services().isEmpty()) {
            this.llmConfig = LlmConfig.empty();
        }
        LlmServices.syncToPreferences(this.llmConfig, prefs);
        for (LlmConfig.ServiceEntry svc : this.llmConfig.services()) {
            LlmServices.secretId(svc.name(), svc.provider())
                    .flatMap(id -> LlmServices.readSecret(secrets, id))
                    .ifPresent(sec -> {
                        pendingLlmSecrets.put(svc.name(), sec);
                        initialSecrets.put(svc.name(), sec);
                    });
        }
        this.emailConfig = EmailConfig.load();
        for (EmailConfig.AccountSpec acc : emailConfig.accounts()) {
            SharedSecrets.resolveEmailSecret(secrets, acc.emailAddress())
                    .ifPresent(sec -> pendingEmailSecrets.put(acc.emailAddress(), sec));
        }
        this.calendarConfig = CalendarConfig.load();
        if (this.calendarConfig.accounts().isEmpty() && !this.emailConfig.accounts().isEmpty()) {
            // Pre-populate linked calendar accounts from any OAuth email accounts (GMAIL / MSGRAPH)
            for (EmailConfig.AccountSpec emailSpec : this.emailConfig.accounts()) {
                CalendarConfig.AccountSpec.fromEmailAccount(emailSpec).ifPresent(calSpec ->
                        this.calendarConfig = this.calendarConfig.withUpsertedAccount(calSpec));
            }
        }
        for (CalendarConfig.AccountSpec acc : calendarConfig.accounts()) {
            SharedSecrets.resolveCalendarSecret(secrets, acc.accountId(), false)
                    .ifPresent(sec -> pendingCalendarSecrets.put(acc.accountId(), sec));
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(6, 6, 0, 6));
        tabs.addTab(bundle.getString("TabAIServices"), createAiServicesPane());
        tabs.addTab(bundle.getString("TabAgentHub"), createAgentHubPane());
        tabs.addTab(bundle.getString("TabInferenceServer"), createInferenceServerPane());
        tabs.addTab(bundle.getString("TabEmailAccounts"), createEmailAccountsPane());
        tabs.addTab(bundle.getString("TabCalendarAccounts"), createCalendarAccountsPane());

        add(tabs, BorderLayout.CENTER);
        add(createButtonPane(), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel createAiServicesPane() {
        JPanel pane = new JPanel(new BorderLayout(6, 6));
        pane.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topBar.add(new JLabel(bundle.getString("Service")));
        llmServiceCombo.setPreferredSize(new Dimension(190, 26));
        llmServiceCombo.addActionListener(e -> {
            if (!suppressLlmComboEvents) {
                commitCurrentLlmServiceForm();
                String selectedName = (String) llmServiceCombo.getSelectedItem();
                if (selectedName != null) {
                    loadLlmServiceForm(selectedName);
                }
            }
        });
        topBar.add(llmServiceCombo);

        JButton addBtn = new JButton(bundle.getString("ServiceAdd"));
        addBtn.addActionListener(e -> addNewLlmService());
        JButton removeBtn = new JButton(bundle.getString("ServiceRemove"));
        removeBtn.addActionListener(e -> removeSelectedLlmService());
        topBar.add(addBtn);
        topBar.add(removeBtn);
        topBar.add(llmDefaultCheck);
        pane.add(topBar, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;
        addEmailRow(form, gbc, r++,
                bundle.getString("ServiceName"), llmServiceNameField,
                bundle.getString("ServiceProvider"), llmProviderCombo);

        llmApiKeyField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        llmCredentialHolder.add(llmApiKeyField, "secret");
        llmCredentialHolder.add(llmProjectField, "project");

        gbc.gridy = r++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        form.add(llmApiKeyLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        form.add(llmCredentialHolder, gbc);
        gbc.gridwidth = 1;

        llmSecretStatusLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        llmSecretStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        gbc.gridx = 1;
        gbc.gridy = r++;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(0, 4, 4, 4);
        form.add(llmSecretStatusLabel, gbc);
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.gridwidth = 1;

        llmBaseUrlField.setEditable(true);
        gbc.gridy = r++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        form.add(llmBaseUrlLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        form.add(llmBaseUrlField, gbc);
        gbc.gridwidth = 1;

        llmModelField.setEditable(true);
        llmModelField.setToolTipText(bundle.getString("Model"));
        addEmailSingleRow(form, gbc, r, bundle.getString("Model"), llmModelField);

        llmProviderCombo.addActionListener(e -> {
            if (!suppressLlmComboEvents) {
                updateLlmProviderFieldStates(true);
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        pane.add(wrapper, BorderLayout.CENTER);
        refreshLlmServiceCombo(llmConfig.defaultService());
        return pane;
    }

    private String selectedLlmProviderKey() {
        int idx = llmProviderCombo.getSelectedIndex();
        if (idx >= 0 && idx < aiServiceKeys.length) {
            return aiServiceKeys[idx];
        }
        Object item = llmProviderCombo.getSelectedItem();
        String key = item == null ? "" : LlmConfig.serviceKeyForLabel(item.toString());
        return key.isEmpty() ? "openai" : key;
    }

    private void selectLlmProviderKey(String providerKey) {
        String key = (providerKey == null || providerKey.isBlank()) ? "openai" : providerKey.trim();
        for (int i = 0; i < aiServiceKeys.length; i++) {
            if (aiServiceKeys[i].equalsIgnoreCase(key) || aiServiceOptions[i].equalsIgnoreCase(key)) {
                llmProviderCombo.setSelectedIndex(i);
                return;
            }
        }
        llmProviderCombo.setSelectedIndex(0);
    }

    private static String comboText(JComboBox<String> combo) {
        Object editorItem = combo.getEditor() != null ? combo.getEditor().getItem() : null;
        if (editorItem != null && !editorItem.toString().isBlank()) {
            return editorItem.toString().trim();
        }
        Object selected = combo.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    private static void setComboOptionsAndValue(JComboBox<String> combo, String[] options, String value) {
        combo.removeAllItems();
        if (options != null) {
            for (String opt : options) {
                combo.addItem(opt);
            }
        }
        String text = value == null ? "" : value;
        combo.setSelectedItem(text);
        if (combo.getEditor() != null) {
            combo.getEditor().setItem(text);
        }
    }

    private void updateLlmProviderFieldStates(boolean applyDefaultBaseUrlWhenBlank) {
        String provider = selectedLlmProviderKey();
        boolean isVertex = "googleEnterprise".equals(provider);

        llmApiKeyLabel.setText(bundle.getString(isVertex ? "Project" : "APIKey"));
        llmCredentialCardLayout.show(llmCredentialHolder, isVertex ? "project" : "secret");
        llmSecretStatusLabel.setVisible(!isVertex);
        llmBaseUrlLabel.setText(bundle.getString(isVertex ? "Location" : "BaseUrl"));

        String currentBaseUrl = comboText(llmBaseUrlField);
        String currentModel = comboText(llmModelField);

        String[] baseUrls = switch (provider) {
            case "openai" -> openaiBaseUrls;
            case "anthropic" -> anthropicBaseUrls;
            case "googleGemini" -> geminiBaseUrls;
            case "chatCompletions" -> otherBaseUrls;
            default -> new String[0];
        };
        String[] models = switch (provider) {
            case "openai", "azureOpenAI" -> openaiModels;
            case "anthropic" -> anthropicModels;
            case "googleGemini", "googleEnterprise" -> geminiModels;
            case "chatCompletions" -> otherModels;
            default -> new String[0];
        };

        String nextBaseUrl = currentBaseUrl;
        if (applyDefaultBaseUrlWhenBlank && (nextBaseUrl.isEmpty() || isDefaultProviderBaseUrl(nextBaseUrl))) {
            nextBaseUrl = LlmConfig.defaultBaseUrlForProvider(provider);
        }
        setComboOptionsAndValue(llmBaseUrlField, baseUrls, nextBaseUrl);
        setComboOptionsAndValue(llmModelField, models, currentModel);

        String name = llmServiceNameField.getText().trim();
        if (name.isEmpty()) {
            name = currentLlmServiceName.isEmpty() ? provider : currentLlmServiceName;
        }
        Optional<SecretId> secretIdOpt = LlmServices.secretId(name, provider);
        if (secretIdOpt.isPresent()) {
            llmSecretStatusLabel.setText(formatSecretStatus(secrets, secretIdOpt.get()));
        } else {
            llmSecretStatusLabel.setText(" ");
        }
    }

    private static boolean isDefaultProviderBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        for (String[] entry : LlmConfig.SERVICES) {
            String def = LlmConfig.defaultBaseUrlForProvider(entry[0]);
            if (!def.isEmpty() && def.equalsIgnoreCase(url.trim())) {
                return true;
            }
        }
        return false;
    }

    private void refreshLlmServiceCombo(String selectName) {
        suppressLlmComboEvents = true;
        try {
            llmServiceCombo.removeAllItems();
            for (LlmConfig.ServiceEntry svc : llmConfig.services()) {
                llmServiceCombo.addItem(svc.name());
            }
            if (!llmConfig.services().isEmpty()) {
                String target = (selectName != null && !selectName.isBlank())
                        ? selectName
                        : llmConfig.services().getFirst().name();
                Optional<LlmConfig.ServiceEntry> found = llmConfig.findService(target);
                String resolvedName = found.map(LlmConfig.ServiceEntry::name)
                        .orElseGet(() -> llmConfig.services().getFirst().name());
                llmServiceCombo.setSelectedItem(resolvedName);
                loadLlmServiceForm(resolvedName);
            } else {
                currentLlmServiceName = "";
                clearLlmServiceForm();
            }
        } finally {
            suppressLlmComboEvents = false;
        }
    }

    private void clearLlmServiceForm() {
        llmServiceNameField.setText("");
        selectLlmProviderKey("chatCompletions");
        llmApiKeyField.setText("");
        llmProjectField.setText("");
        llmDefaultCheck.setSelected(llmConfig.services().isEmpty());
        setComboOptionsAndValue(llmBaseUrlField, otherBaseUrls, LlmConfig.defaultBaseUrlForProvider("chatCompletions"));
        setComboOptionsAndValue(llmModelField, otherModels, "");
        updateLlmProviderFieldStates(false);
    }

    private void loadLlmServiceForm(String serviceName) {
        Optional<LlmConfig.ServiceEntry> opt = llmConfig.findService(serviceName);
        if (opt.isEmpty()) {
            clearLlmServiceForm();
            return;
        }
        boolean prevSuppress = suppressLlmComboEvents;
        suppressLlmComboEvents = true;
        try {
            LlmConfig.ServiceEntry svc = opt.get();
            currentLlmServiceName = svc.name();
            llmServiceNameField.setText(svc.name());
            selectLlmProviderKey(svc.provider());
            llmDefaultCheck.setSelected(svc.name().equalsIgnoreCase(llmConfig.defaultService()));

            boolean isVertex = "googleEnterprise".equals(svc.provider());
            if (isVertex) {
                llmProjectField.setText(svc.project());
                llmApiKeyField.setText("");
            } else {
                String secret = pendingLlmSecrets.getOrDefault(
                        svc.name(),
                        LlmServices.secretId(svc.name(), svc.provider())
                                .flatMap(id -> LlmServices.readSecret(secrets, id))
                                .orElse("")
                );
                llmApiKeyField.setText(secret);
                llmProjectField.setText("");
            }
            String endpointVal = isVertex
                    ? (!svc.location().isBlank() ? svc.location() : svc.baseUrl())
                    : svc.baseUrl();
            setComboOptionsAndValue(llmBaseUrlField, new String[0], endpointVal);
            setComboOptionsAndValue(llmModelField, new String[0], svc.modelsCsv());
            updateLlmProviderFieldStates(false);
        } finally {
            suppressLlmComboEvents = prevSuppress;
        }
    }

    private LlmConfig.ServiceEntry buildSpecFromLlmForm() {
        String nameText = llmServiceNameField.getText().trim();
        String name = !nameText.isEmpty() ? nameText : currentLlmServiceName;
        if (name.isEmpty()) {
            return null;
        }
        String provider = selectedLlmProviderKey();
        String endpointText = comboText(llmBaseUrlField);
        String modelsText = comboText(llmModelField);
        List<String> models = LlmConfig.parseModelIds(modelsText);

        if ("googleEnterprise".equals(provider)) {
            String project = llmProjectField.getText().trim();
            return new LlmConfig.ServiceEntry(name, provider, "", project, endpointText, models);
        }
        return new LlmConfig.ServiceEntry(name, provider, endpointText, null, null, models);
    }

    private void commitCurrentLlmServiceForm() {
        LlmConfig.ServiceEntry spec = buildSpecFromLlmForm();
        if (spec == null) {
            return;
        }
        if (!currentLlmServiceName.isEmpty() && !currentLlmServiceName.equalsIgnoreCase(spec.name())) {
            llmConfig = llmConfig.withoutService(currentLlmServiceName);
            String prevSecret = pendingLlmSecrets.remove(currentLlmServiceName);
            if (prevSecret != null) {
                pendingLlmSecrets.put(spec.name(), prevSecret);
            }
        }
        llmConfig = llmConfig.withUpsertedService(spec);
        if (llmDefaultCheck.isSelected()) {
            llmConfig = llmConfig.withDefaultService(spec.name());
        }
        if (!"googleEnterprise".equals(spec.provider())) {
            String secretVal = new String(llmApiKeyField.getPassword()).strip();
            pendingLlmSecrets.put(spec.name(), secretVal);
        }
        currentLlmServiceName = spec.name();
    }

    private void addNewLlmService() {
        commitCurrentLlmServiceForm();
        suppressLlmComboEvents = true;
        try {
            currentLlmServiceName = "";
            clearLlmServiceForm();
            llmServiceNameField.requestFocusInWindow();
        } finally {
            suppressLlmComboEvents = false;
        }
    }

    private void removeSelectedLlmService() {
        String name = !llmServiceNameField.getText().trim().isEmpty()
                ? llmServiceNameField.getText().trim()
                : currentLlmServiceName;
        if (name.isEmpty()) {
            return;
        }
        String provider = llmConfig.findService(name)
                .map(LlmConfig.ServiceEntry::provider)
                .orElseGet(this::selectedLlmProviderKey);
        llmConfig = llmConfig.withoutService(name);
        pendingLlmSecrets.remove(name);
        initialSecrets.remove(name);
        SharedSecrets.deleteLlmSecret(secrets, name, provider);
        refreshLlmServiceCombo(llmConfig.defaultService());
    }

    private void saveLlmConfiguration() {
        commitCurrentLlmServiceForm();
        for (LlmConfig.ServiceEntry svc : llmConfig.services()) {
            Optional<SecretId> secretIdOpt = LlmServices.secretId(svc.name(), svc.provider());
            if (secretIdOpt.isPresent()) {
                String value = pendingLlmSecrets.getOrDefault(svc.name(), "").strip();
                saveServiceSecret(svc.name(), svc.name(), secretIdOpt.get(), value);
            }
        }
        llmConfig.save();
        LlmServices.syncToPreferences(llmConfig, prefs);
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
        serveLlmField.setText(cfg.llm());
        serveLlmField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void syncAutoStart() {
                if (!serveLlmField.getText().trim().isEmpty()) {
                    serveAutoStartCheck.setSelected(true);
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { syncAutoStart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { syncAutoStart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { syncAutoStart(); }
        });

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
        row++;

        // Row 4: LLM Model (Hugging Face ID or directory)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("ServeLlm")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(serveLlmField, gbc);

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
        String serveLlm = serveLlmField.getText().trim();
        StudioConfig.InferenceServer inferenceServer = new StudioConfig.InferenceServer(
                serveAutoStartCheck.isSelected() || !serveLlm.isEmpty(),
                serveHost.isEmpty() ? StudioConfig.DEFAULT_HOST : serveHost,
                servePort > 0 ? servePort : StudioConfig.DEFAULT_PORT,
                serveModelPath.isEmpty() ? StudioConfig.DEFAULT_MODEL_PATH : serveModelPath,
                serveLlm
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
                bundle.getString("EmailSenderName"), emailSenderNameField,
                bundle.getString("EmailDescription"), emailDescriptionField);
        addEmailRow(form, gbc, r++,
                bundle.getString("EmailFolder"), emailFolderField,
                bundle.getString("EmailPollInterval"), emailPollSpinner);
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
        emailLoginGoogleButton.addActionListener(e -> launchOneClickOAuthLogin(EmailConfig.Provider.GMAIL));
        emailLoginMicrosoftButton.addActionListener(e -> launchOneClickOAuthLogin(EmailConfig.Provider.MSGRAPH));
        emailTestButton.addActionListener(e -> testCurrentEmailConnection());
        actionRow.add(emailLoginGoogleButton);
        actionRow.add(emailLoginMicrosoftButton);
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

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        pane.add(wrapper, BorderLayout.CENTER);
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
        emailSenderNameField.setText("");
        emailDescriptionField.setText("");
        emailProviderCombo.setSelectedItem(EmailConfig.Provider.GMAIL);
        emailPollSpinner.setValue(60);
        emailFolderField.setText("INBOX");
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
        emailSenderNameField.setText(acc.senderName() != null ? acc.senderName() : "");
        emailDescriptionField.setText(acc.description() != null ? acc.description() : "");
        emailProviderCombo.setSelectedItem(acc.provider());
        emailPollSpinner.setValue(acc.pollIntervalSeconds());
        emailFolderField.setText(acc.folder());
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
        boolean isImap = provider == EmailConfig.Provider.IMAP;

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
        Optional<EmailConfig.AccountSpec> existing = emailConfig.findAccount(email);
        EmailConfig.Provider provider = (EmailConfig.Provider) emailProviderCombo.getSelectedItem();
        String senderName = emailSenderNameField.getText().trim();
        String description = emailDescriptionField.getText().trim();
        int pollSec = ((Number) emailPollSpinner.getValue()).intValue();
        String folder = emailFolderField.getText().trim();
        String clientId = existing.map(EmailConfig.AccountSpec::clientId).orElse(null);
        String tenantId = existing.map(EmailConfig.AccountSpec::tenantId)
                .orElse(provider == EmailConfig.Provider.MSGRAPH ? "common" : null);
        String imapHost = emailImapHostField.getText().trim();
        int imapPort = ((Number) emailImapPortSpinner.getValue()).intValue();
        boolean imapSsl = imapPort != 143;
        String smtpHost = emailSmtpHostField.getText().trim();
        int smtpPort = ((Number) emailSmtpPortSpinner.getValue()).intValue();
        boolean startTls = smtpPort != 465 && (smtpPort == 587 || emailStartTlsCheck.isSelected());
        boolean enabled = emailEnabledCheck.isSelected();

        return new EmailConfig.AccountSpec(
                email,
                senderName.isEmpty() ? null : senderName,
                description.isEmpty() ? null : description,
                provider,
                enabled,
                pollSec,
                folder.isEmpty() ? "INBOX" : folder,
                provider == EmailConfig.Provider.GMAIL ? "label:INBOX" : null,
                clientId,
                tenantId,
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

    private void setOAuthLoginButtonsEnabled(boolean enabled) {
        emailLoginGoogleButton.setEnabled(enabled);
        emailLoginMicrosoftButton.setEnabled(enabled);
        calendarLoginGoogleButton.setEnabled(enabled);
        calendarLoginMicrosoftButton.setEnabled(enabled);
    }

    private void launchOneClickOAuthLogin(EmailConfig.Provider provider) {
        commitCurrentEmailAccountForm();
        commitCurrentCalendarAccountForm();
        setOAuthLoginButtonsEnabled(false);
        Thread.ofVirtual().name("studio-oneclick-oauth").start(() -> {
            try {
                OAuthFlow.OAuthLoginResult login = provider == EmailConfig.Provider.GMAIL
                        ? EmailOAuthFlow.loginWithGoogle(Duration.ofMinutes(3))
                        : EmailOAuthFlow.loginWithMicrosoft(Duration.ofMinutes(3));
                SwingUtilities.invokeLater(() -> {
                    OAuthFlow.ProvisionedAccounts provisioned = OAuthFlow.provisionAccounts(
                            login, emailConfig, calendarConfig, secrets);
                    emailConfig = provisioned.emailConfig();
                    calendarConfig = provisioned.calendarConfig();
                    pendingEmailSecrets.put(login.emailAddress(), login.refreshToken());
                    refreshEmailAccountCombo(login.emailAddress());
                    refreshCalendarAccountCombo(login.emailAddress());
                    setOAuthLoginButtonsEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setOAuthLoginButtonsEnabled(true);
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

    private JPanel createCalendarAccountsPane() {
        JPanel pane = new JPanel(new BorderLayout(6, 6));
        pane.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topBar.add(new JLabel(bundle.getString("CalendarAccount")));
        calendarAccountCombo.setPreferredSize(new Dimension(190, 26));
        calendarAccountCombo.addActionListener(e -> {
            if (!suppressCalendarComboEvents) {
                commitCurrentCalendarAccountForm();
                String selectedId = (String) calendarAccountCombo.getSelectedItem();
                if (selectedId != null) {
                    loadCalendarAccountForm(selectedId);
                }
            }
        });
        topBar.add(calendarAccountCombo);

        JButton addBtn = new JButton(bundle.getString("CalendarAdd"));
        addBtn.addActionListener(e -> addNewCalendarAccount());
        JButton removeBtn = new JButton(bundle.getString("CalendarRemove"));
        removeBtn.addActionListener(e -> removeSelectedCalendarAccount());
        topBar.add(addBtn);
        topBar.add(removeBtn);
        topBar.add(calendarDefaultCheck);
        topBar.add(calendarEnabledCheck);
        pane.add(topBar, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;
        addEmailRow(form, gbc, r++,
                bundle.getString("CalendarAccountId"), calendarAccountIdField,
                bundle.getString("CalendarProvider"), calendarProviderCombo);
        addEmailRow(form, gbc, r++,
                bundle.getString("CalendarId"), calendarIdField,
                bundle.getString("CalendarPollInterval"), calendarPollSpinner);
        addEmailSingleRow(form, gbc, r++,
                bundle.getString("CalendarDescription"), calendarDescriptionField);

        gbc.gridx = 1;
        gbc.gridy = r++;
        gbc.gridwidth = 3;
        form.add(calendarUseEmailCheck, gbc);
        gbc.gridwidth = 1;

        calendarSecretField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        gbc.gridx = 0;
        gbc.gridy = r;
        gbc.weightx = 0.0;
        form.add(new JLabel(bundle.getString("CalendarSecret")), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        form.add(calendarSecretField, gbc);
        gbc.gridwidth = 1;
        r++;

        calendarSecretStatusLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        calendarSecretStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        gbc.gridx = 1;
        gbc.gridy = r++;
        gbc.gridwidth = 3;
        form.add(calendarSecretStatusLabel, gbc);
        gbc.gridwidth = 1;

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        calendarLoginGoogleButton.addActionListener(e -> launchOneClickOAuthLogin(EmailConfig.Provider.GMAIL));
        calendarLoginMicrosoftButton.addActionListener(e -> launchOneClickOAuthLogin(EmailConfig.Provider.MSGRAPH));
        calendarTestButton.addActionListener(e -> testCurrentCalendarConnection());
        actionRow.add(calendarLoginGoogleButton);
        actionRow.add(calendarLoginMicrosoftButton);
        actionRow.add(calendarTestButton);

        gbc.gridx = 1;
        gbc.gridy = r;
        gbc.gridwidth = 3;
        form.add(actionRow, gbc);
        gbc.gridwidth = 1;

        calendarProviderCombo.addActionListener(e -> updateCalendarProviderFieldStates());
        calendarUseEmailCheck.addActionListener(e -> updateCalendarProviderFieldStates());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        pane.add(wrapper, BorderLayout.CENTER);
        refreshCalendarAccountCombo(calendarConfig.defaultAccount());
        return pane;
    }

    private void refreshCalendarAccountCombo(String selectId) {
        suppressCalendarComboEvents = true;
        try {
            calendarAccountCombo.removeAllItems();
            for (CalendarConfig.AccountSpec acc : calendarConfig.accounts()) {
                calendarAccountCombo.addItem(acc.accountId());
            }
            if (!calendarConfig.accounts().isEmpty()) {
                String target = (selectId != null && !selectId.isBlank())
                        ? selectId
                        : calendarConfig.accounts().getFirst().accountId();
                calendarAccountCombo.setSelectedItem(target);
                loadCalendarAccountForm(target);
            } else {
                currentCalendarAccountId = "";
                clearCalendarAccountForm();
            }
        } finally {
            suppressCalendarComboEvents = false;
        }
    }

    private void clearCalendarAccountForm() {
        calendarAccountIdField.setText("");
        calendarProviderCombo.setSelectedItem(CalendarConfig.Provider.GOOGLE);
        calendarIdField.setText("primary");
        calendarDescriptionField.setText("");
        calendarPollSpinner.setValue(300);
        calendarUseEmailCheck.setSelected(true);
        calendarDefaultCheck.setSelected(true);
        calendarEnabledCheck.setSelected(true);
        calendarSecretField.setText("");
        calendarSecretStatusLabel.setText(formatSecretStatus(secrets, SharedSecrets.calendarSecretId("default")));
        updateCalendarProviderFieldStates();
    }

    private void loadCalendarAccountForm(String accountId) {
        Optional<CalendarConfig.AccountSpec> opt = calendarConfig.findAccount(accountId);
        if (opt.isEmpty()) {
            clearCalendarAccountForm();
            return;
        }
        CalendarConfig.AccountSpec acc = opt.get();
        currentCalendarAccountId = acc.accountId();
        calendarAccountIdField.setText(acc.accountId());
        calendarProviderCombo.setSelectedItem(acc.provider());
        calendarIdField.setText(acc.calendarId() != null ? acc.calendarId() : "primary");
        calendarDescriptionField.setText(acc.description() != null ? acc.description() : "");
        calendarPollSpinner.setValue(acc.pollIntervalSeconds());
        calendarUseEmailCheck.setSelected(acc.useEmailAccount());
        calendarDefaultCheck.setSelected(acc.accountId().equalsIgnoreCase(calendarConfig.defaultAccount()));
        calendarEnabledCheck.setSelected(acc.enabled());

        String secret = pendingCalendarSecrets.getOrDefault(
                acc.accountId(),
                SharedSecrets.resolveCalendarSecret(secrets, acc.accountId(), false).orElse("")
        );
        calendarSecretField.setText(secret);
        SecretId statusId = (secret.isEmpty() && acc.useEmailAccount() && emailConfig.findAccount(acc.accountId()).isPresent())
                ? SharedSecrets.emailSecretId(acc.accountId())
                : SharedSecrets.calendarSecretId(acc.accountId());
        calendarSecretStatusLabel.setText(formatSecretStatus(secrets, statusId));
        updateCalendarProviderFieldStates();
    }

    private void updateCalendarProviderFieldStates() {
        CalendarConfig.Provider provider = (CalendarConfig.Provider) calendarProviderCombo.getSelectedItem();
        boolean isOAuth = provider == CalendarConfig.Provider.GOOGLE || provider == CalendarConfig.Provider.MSGRAPH;

        calendarUseEmailCheck.setEnabled(isOAuth);
        calendarSecretField.setEnabled(isOAuth);
    }

    private CalendarConfig.AccountSpec buildSpecFromCalendarForm() {
        String idText = calendarAccountIdField.getText().trim();
        String accountId = !idText.isEmpty() ? idText : currentCalendarAccountId;
        if (accountId.isEmpty()) {
            return null;
        }
        Optional<CalendarConfig.AccountSpec> existing = calendarConfig.findAccount(accountId);
        CalendarConfig.Provider provider = (CalendarConfig.Provider) calendarProviderCombo.getSelectedItem();
        String calId = calendarIdField.getText().trim();
        String description = calendarDescriptionField.getText().trim();
        int pollSec = ((Number) calendarPollSpinner.getValue()).intValue();
        boolean useEmail = calendarUseEmailCheck.isSelected();
        String clientId = existing.map(CalendarConfig.AccountSpec::clientId).orElse(null);
        String tenantId = existing.map(CalendarConfig.AccountSpec::tenantId)
                .orElse(provider == CalendarConfig.Provider.MSGRAPH ? "common" : null);
        boolean enabled = calendarEnabledCheck.isSelected();

        return new CalendarConfig.AccountSpec(
                accountId,
                description.isEmpty() ? null : description,
                provider,
                enabled,
                pollSec,
                calId.isEmpty() ? "primary" : calId,
                useEmail,
                clientId,
                tenantId
        );
    }

    private void commitCurrentCalendarAccountForm() {
        CalendarConfig.AccountSpec spec = buildSpecFromCalendarForm();
        if (spec == null) {
            return;
        }
        if (!currentCalendarAccountId.isEmpty() && !currentCalendarAccountId.equalsIgnoreCase(spec.accountId())) {
            calendarConfig = calendarConfig.withoutAccount(currentCalendarAccountId);
            pendingCalendarSecrets.remove(currentCalendarAccountId);
        }
        calendarConfig = calendarConfig.withUpsertedAccount(spec);
        if (calendarDefaultCheck.isSelected()) {
            calendarConfig = new CalendarConfig(spec.accountId(), calendarConfig.accounts());
        }
        String secretVal = new String(calendarSecretField.getPassword()).strip();
        pendingCalendarSecrets.put(spec.accountId(), secretVal);
        currentCalendarAccountId = spec.accountId();
    }

    private void addNewCalendarAccount() {
        commitCurrentCalendarAccountForm();
        suppressCalendarComboEvents = true;
        try {
            currentCalendarAccountId = "";
            clearCalendarAccountForm();
            calendarAccountIdField.requestFocusInWindow();
        } finally {
            suppressCalendarComboEvents = false;
        }
    }

    private void removeSelectedCalendarAccount() {
        String id = !calendarAccountIdField.getText().trim().isEmpty()
                ? calendarAccountIdField.getText().trim()
                : currentCalendarAccountId;
        if (id.isEmpty()) {
            return;
        }
        calendarConfig = calendarConfig.withoutAccount(id);
        pendingCalendarSecrets.remove(id);
        SharedSecrets.deleteCalendarSecret(secrets, id);
        refreshCalendarAccountCombo(calendarConfig.defaultAccount());
    }

    private void testCurrentCalendarConnection() {
        commitCurrentEmailAccountForm();
        commitCurrentCalendarAccountForm();
        CalendarConfig.AccountSpec spec = buildSpecFromCalendarForm();
        if (spec == null) {
            return;
        }
        String secretVal = new String(calendarSecretField.getPassword()).strip();
        calendarTestButton.setEnabled(false);
        Thread.ofVirtual().name("studio-calendar-test").start(() -> {
            try {
                CalendarServices testServices = new CalendarServices();
                testServices.setEmailConfigSupplier(() -> emailConfig);
                var client = testServices.createDefaultClient(
                        spec,
                        s -> !secretVal.isEmpty()
                                ? Optional.of(secretVal)
                                : SharedSecrets.resolveCalendarSecret(secrets, s.accountId(), s.useEmailAccount())
                );
                String status = client.testConnection();
                SwingUtilities.invokeLater(() -> {
                    calendarTestButton.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            MessageFormat.format(bundle.getString("CalendarTestSuccess"), status),
                            bundle.getString("Title"),
                            JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    calendarTestButton.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            MessageFormat.format(bundle.getString("CalendarTestFailure"), ex.getMessage()),
                            bundle.getString("Title"),
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void saveCalendarConfiguration() {
        commitCurrentCalendarAccountForm();
        calendarConfig.save();
        for (CalendarConfig.AccountSpec acc : calendarConfig.accounts()) {
            String sec = pendingCalendarSecrets.getOrDefault(acc.accountId(), "");
            if (!sec.isEmpty()) {
                SharedSecrets.saveCalendarSecret(secrets, acc.accountId(), sec);
            }
        }
        CalendarServices.getInstance().reload(calendarConfig, secrets);
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
            saveLlmConfiguration();
            saveStudioConfiguration();
            saveEmailConfiguration();
            saveCalendarConfiguration();
            SmileStudio.updateLLM();
            SmileStudio.refreshModelSelectors();
            dispose();
        });

        cancelButton.addActionListener((e) -> dispose());
        return pane;
    }

    private void saveServiceSecret(String service, String label, SecretId id, String value) {
        prefs.remove(service + API_KEY);
        if (secrets == null) {
            return;
        }
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
        if (e.getSource() == llmProviderCombo && !suppressLlmComboEvents) {
            updateLlmProviderFieldStates(true);
        }
    }
}
