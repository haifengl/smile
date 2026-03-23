/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.prefs.Preferences;
import smile.studio.SmileStudio;

/**
 * The application preference and configuration dialog.
 *
 * @author Haifeng Li
 */
public class SettingsDialog extends JDialog implements ActionListener {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(SettingsDialog.class.getName(), Locale.getDefault());
    private static final String AI_SERVICE = "aiService";
    private static final String API_KEY = "ApiKey";
    private static final String BASE_URL = "BaseUrl";
    private static final String MODEL = "Model";
    private static final String[] options = {"OpenAI", "Azure OpenAI", "Anthropic", "Google Gemini", "Google Vertex AI"};
    private static final String[] keys = {"openai", "azureOpenAI", "anthropic", "googleGemini", "googleVertexAI"};
    private final JComboBox<String> comboBox = new JComboBox<>(options);;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPane = new JPanel(cardLayout);
    private final Map<String, JTextField> apiKeyFields = new TreeMap<>();
    private final Map<String, JTextField> baseUrlFields = new TreeMap<>();
    private final Map<String, JTextField> modelFields = new TreeMap<>();
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

        comboBox.addActionListener(this);
        add(createServiceChoice(), BorderLayout.NORTH);

        // Add the panels to the dynamic panel with unique names
        for (int i = 0; i < options.length; i++) {
            cardPane.add(createServiceCard(keys[i]), options[i]);
        }

        // Add the dynamic panel to the center of the dialog
        add(cardPane, BorderLayout.CENTER);
        comboBox.setSelectedItem(prefs.get(AI_SERVICE, options[0]));

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

        // Row 1
        gbc.gridx = 0; // Column 0
        gbc.gridy = 0; // Row 0
        gbc.anchor = GridBagConstraints.WEST;
        JLabel apiKeyLabel = new JLabel(bundle.getString("Service"));
        pane.add(apiKeyLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // Allow text field to take extra horizontal space
        pane.add(comboBox, gbc);

        return pane;
    }

    private JPanel createServiceCard(String key) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 1
        gbc.gridx = 0; // Column 0
        gbc.gridy = 0; // Row 0
        gbc.anchor = GridBagConstraints.WEST;
        JLabel apiKeyLabel = new JLabel(bundle.getString("APIKey"));
        card.add(apiKeyLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // Allow text field to take extra horizontal space
        JTextField apiKeyField = new JTextField(25);
        apiKeyField.setText(prefs.get(key + "ApiKey", ""));
        apiKeyFields.put(key, apiKeyField);
        card.add(apiKeyField, gbc);

        // Row 2
        gbc.gridx = 0; // Column 0
        gbc.gridy = 1; // Row 1
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE; // Reset fill for label
        gbc.weightx = 0.0; // Reset weightx for label
        JLabel baseUrlLabel = new JLabel(bundle.getString("BaseUrl"));
        card.add(baseUrlLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JTextField baseUrlField = new JTextField(25);
        baseUrlField.setText(prefs.get(key + "BaseUrl", ""));
        baseUrlFields.put(key, baseUrlField);
        card.add(baseUrlField, gbc);

        // Row 3
        gbc.gridx = 0; // Column 0
        gbc.gridy = 2; // Row 2
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE; // Reset fill for label
        gbc.weightx = 0.0; // Reset weightx for label
        JLabel modelLabel = new JLabel(bundle.getString("Model"));
        card.add(modelLabel, gbc);

        gbc.gridx = 1; // Column 1
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JTextField modelField = new JTextField(25);
        modelField.setText(prefs.get(key + "Model", ""));
        modelFields.put(key, modelField);
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
            prefs.put(AI_SERVICE, options[comboBox.getSelectedIndex()]);
            for (String service : keys) {
                prefs.put(service + API_KEY, apiKeyFields.get(service).getText());
                prefs.put(service + BASE_URL, baseUrlFields.get(service).getText());
                prefs.put(service + MODEL, modelFields.get(service).getText());
            }
            SmileStudio.initLLM();
            dispose();
        });

        cancelButton.addActionListener((e) -> dispose());
        return pane;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // This method is called when the JComboBox selection changes
        String selectedOption = (String) comboBox.getSelectedItem();

        // Tell the CardLayout to show the panel corresponding to the selected option
        cardLayout.show(cardPane, selectedOption);

        // Repaint and revalidate the container to ensure correct display
        cardPane.revalidate();
        cardPane.repaint();
        // Call pack() if the new panel has a different preferred size
        pack();
    }
}
