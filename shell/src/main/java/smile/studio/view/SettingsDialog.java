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
    private static final ResourceBundle bundle = ResourceBundle.getBundle(SettingsDialog.class.getName(), Locale.getDefault());
    private final JLabel apiKeyLabel = new JLabel(bundle.getString("APIKey"));
    private final JTextField apiKeyField = new JTextField(25);
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

        apiKeyField.setText(prefs.get(OPENAI_API_KEY, ""));
        // Panel for the input field and label
        JPanel inputPane = new JPanel(new FlowGridBagLayout());
        inputPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        inputPane.add(apiKeyLabel);
        inputPane.add(apiKeyField);

        // Panel for the buttons
        JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPane.setBorder(new EmptyBorder(0, 0, 0, 10));
        buttonPane.add(okButton);
        buttonPane.add(cancelButton);

        okButton.addActionListener((e) -> {
            prefs.put(OPENAI_API_KEY, apiKeyField.getText());
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
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            setConstraints(apiKeyLabel, gbc);
            gbc.gridwidth = GridBagConstraints.REMAINDER; // End of row
            gbc.fill = GridBagConstraints.HORIZONTAL;
            setConstraints(apiKeyField, gbc);
        }
    }
}
