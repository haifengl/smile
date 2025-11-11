/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * The application preference and configuration dialog.
 *
 * @author Haifeng Li
 */
public class SettingsDialog extends JDialog {
    public static final String OPENAI_API_KEY = "openaiApiKey";
    public static final String ANTHROPIC_API_KEY = "anthropicApiKey";
    public static final String GOOGLE_API_KEY = "googleApiKey";
    private static final ResourceBundle bundle = ResourceBundle.getBundle(SettingsDialog.class.getName(), Locale.getDefault());
    private final JLabel openaiApiKeyLabel = new JLabel(bundle.getString("OpenAIAPIKey"));
    private final JTextField openaiApiKeyField = new JTextField(25);
    private final JLabel anthropicApiKeyLabel = new JLabel(bundle.getString("AnthropicAPIKey"));
    private final JTextField anthropicApiKeyField = new JTextField(25);
    private final JLabel googleApiKeyLabel = new JLabel(bundle.getString("GoogleAPIKey"));
    private final JTextField googleApiKeyField = new JTextField(25);
    private final JButton okButton = new JButton(bundle.getString("OK"));
    private final JButton cancelButton = new JButton(bundle.getString("Cancel"));

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

        openaiApiKeyField.setText(prefs.get(OPENAI_API_KEY, ""));
        anthropicApiKeyField.setText(prefs.get(ANTHROPIC_API_KEY, ""));
        googleApiKeyField.setText(prefs.get(GOOGLE_API_KEY, ""));

        // Panel for the input field and label
        JPanel inputPane = new JPanel(new GridBagLayout());
        inputPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 1
        gbc.gridx = 0; // Column 0
        gbc.gridy = 0; // Row 0
        gbc.anchor = GridBagConstraints.WEST;
        inputPane.add(openaiApiKeyLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // Allow text field to take extra horizontal space
        inputPane.add(openaiApiKeyField, gbc);

        // Row 2
        gbc.gridx = 0; // Column 0
        gbc.gridy = 1; // Row 1
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE; // Reset fill for label
        gbc.weightx = 0.0; // Reset weightx for label
        inputPane.add(anthropicApiKeyLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        inputPane.add(anthropicApiKeyField, gbc);

        // Row 3
        gbc.gridx = 0; // Column 0
        gbc.gridy = 2; // Row 2
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        inputPane.add(googleApiKeyLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        inputPane.add(googleApiKeyField, gbc);

        // Panel for the buttons
        JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPane.setBorder(new EmptyBorder(0, 0, 0, 10));
        buttonPane.add(okButton);
        buttonPane.add(cancelButton);

        okButton.addActionListener((e) -> {
            prefs.put(OPENAI_API_KEY, openaiApiKeyField.getText());
            prefs.put(ANTHROPIC_API_KEY, anthropicApiKeyField.getText());
            prefs.put(GOOGLE_API_KEY, googleApiKeyField.getText());
            dispose();
        });

        cancelButton.addActionListener((e) -> {
            dispose();
        });

        add(inputPane, BorderLayout.CENTER);
        add(buttonPane, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    // Custom Layout Manager helper for simple label-field alignment
    class FlowGridBagLayout extends GridBagLayout {
        public FlowGridBagLayout() {
        }
    }
}
