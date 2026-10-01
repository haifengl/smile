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
import ioa.llm.Model;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 * The application preference and configuration dialog.
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
    private final JComboBox<String> aiServiceCombo = new JComboBox<>(aiServiceOptions);;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPane = new JPanel(cardLayout);
    private final Map<String, JTextField> apiKeyFields = new TreeMap<>();
    private final Map<String, JComboBox<String>> baseUrlFields = new TreeMap<>();
    private final Map<String, JComboBox<String>> modelFields = new TreeMap<>();
    private final Preferences prefs;

    /**
     * Constructor.
     * @param owner the Frame from which the dialog is displayed
     *              or null if this dialog has no owner.
     * @param prefs the application preference and configuration data.
     */
    public SettingsDialog(Frame owner, Preferences prefs) {
        super(owner, bundle.getString("Title"), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        this.prefs = prefs;

        themeCombo.setSelectedItem(prefs.get(UI_THEME_KEY, "Dark"));
        themeCombo.addActionListener(this);
        aiServiceCombo.addActionListener(this);
        add(createServiceChoice(), BorderLayout.NORTH);

        // Add the panels to the dynamic panel with unique names
        for (int i = 0; i < aiServiceOptions.length; i++) {
            cardPane.add(createServiceCard(aiServiceKeys[i]), aiServiceOptions[i]);
        }

        // Add the dynamic panel to the center of the dialog
        add(cardPane, BorderLayout.CENTER);
        // Set the AI service value after adding action listener and all cards
        // so that the card pane shows existing values properly.
        aiServiceCombo.setSelectedItem(prefs.get(AI_SERVICE_KEY, aiServiceOptions[0]));

        // Panel for the buttons
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
        pane.add(themeCombo, gbc);

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

        // Row 1
        gbc.gridx = 0; // Column 0
        gbc.gridy = 0; // Row 0
        gbc.anchor = GridBagConstraints.WEST;
        JLabel apiKeyLabel = new JLabel(bundle.getString(service.equals("googleEnterprise") ? "Project" : "APIKey"));
        card.add(apiKeyLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // Allow text field to take extra horizontal space
        JTextField apiKeyField = new JTextField(25);
        apiKeyField.setText(prefs.get(service + "ApiKey", ""));
        apiKeyFields.put(service, apiKeyField);
        card.add(apiKeyField, gbc);

        // Row 2
        gbc.gridx = 0; // Column 0
        gbc.gridy = 1; // Row 1
        gbc.fill = GridBagConstraints.NONE; // Reset fill for label
        gbc.weightx = 0.0; // Reset weightx for label
        JLabel baseUrlLabel = new JLabel(bundle.getString(service.equals("googleEnterprise") ? "Location" : "BaseUrl"));
        card.add(baseUrlLabel, gbc);

        gbc.gridx = 1; // Column 1
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
        baseUrlField.setSelectedItem(prefs.get(service + "BaseUrl", ""));
        baseUrlFields.put(service, baseUrlField);
        card.add(baseUrlField, gbc);

        // Row 3
        gbc.gridx = 0; // Column 0
        gbc.gridy = 2; // Row 2
        gbc.fill = GridBagConstraints.NONE; // Reset fill for label
        gbc.weightx = 0.0; // Reset weightx for label
        JLabel modelLabel = new JLabel(bundle.getString("Model"));
        card.add(modelLabel, gbc);

        gbc.gridx = 1; // Column 1
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
        modelField.setSelectedItem(prefs.get(service + "Model", ""));
        modelField.setToolTipText(bundle.getString("Model"));
        modelFields.put(service, modelField);
        card.add(modelField, gbc);

        return card;
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
            prefs.put(AI_SERVICE_KEY, aiServiceOptions[aiServiceCombo.getSelectedIndex()]);
            for (String service : aiServiceKeys) {
                prefs.put(service + API_KEY, apiKeyFields.get(service).getText());
                prefs.put(service + BASE_URL, (String) baseUrlFields.get(service).getSelectedItem());
                prefs.put(service + MODEL, (String) modelFields.get(service).getSelectedItem());
            }
            SmileStudio.updateLLM();
            SmileStudio.refreshModelSelectors();
            dispose();
        });

        cancelButton.addActionListener((e) -> dispose());
        return pane;
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
