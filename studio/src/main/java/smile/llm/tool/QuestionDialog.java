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
package smile.llm.tool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * A dialog for agent to ask user questions.
 *
 * @author Haifeng Li
 */
public class QuestionDialog extends JDialog implements ActionListener {
    private static final String OTHER = "Other";
    private final List<String> choices;
    private final List<JRadioButton> radioButtons = new ArrayList<>();
    private final ButtonGroup buttonGroup = new ButtonGroup();
    private final JTextArea customTextInput = new JTextArea(3, 40);;
    private final JButton okButton, cancelButton;
    private String selectedValue = null;

    public QuestionDialog(String question, List<String> choices, boolean multiSelect) {
        this.choices = choices;
        setTitle("Question");

        // Initialize components
        customTextInput.setEnabled(false);
        customTextInput.setLineWrap(true);
        customTextInput.setWrapStyleWord(true);

        // Create the main panel with a vertical layout
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        // Add radio buttons to the panel and group
        for (var choice : choices) {
            var radioButton = new JRadioButton(choice);
            radioButton.addActionListener(this);
            radioButtons.add(radioButton); 
            buttonGroup.add(radioButton);
            contentPane.add(radioButton);
        }
        
        // Add the text area below the "Other" radio button
        // Use a JScrollPane for the text area for better usability
        if (!choices.getLast().equals(OTHER)) {
            var radioButton = new JRadioButton(OTHER);
            radioButton.addActionListener(this);
            radioButtons.add(radioButton); 
            buttonGroup.add(radioButton);
            contentPane.add(radioButton);
        }

        JScrollPane scrollPane = new JScrollPane(customTextInput);
        contentPane.add(scrollPane);

        // Add buttons
        JPanel buttonPanel = new JPanel();
        okButton = new JButton("OK");
        okButton.addActionListener(this);
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // Add panels to the dialog
        add(contentPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pack(); // Adjusts window size to fit components
        setLocationRelativeTo(null); // Center the dialog in the screen
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            // Get the selected value
            for (int i = 0; i < radioButtons.size(); i++) {
                if (radioButtons.get(i).isSelected()) {
                    if (choices.get(i).equals(OTHER)) {
                        selectedValue = customTextInput.getText();
                    } else {
                        selectedValue = choices.get(i);
                    }
                    break;
                }
            }
            setVisible(false); // Close the dialog

        } else if (e.getSource() == cancelButton) {
            selectedValue = null; // Indicate cancellation
            setVisible(false); // Close the dialog

        } else {
            // Handle radio button selection to enable/disable text area
            if (((JRadioButton) e.getSource()).getText().equals(OTHER)) {
                customTextInput.setEnabled(true);
                customTextInput.requestFocus(); // Set focus for typing
            } else {
                customTextInput.setEnabled(false);
            }
        }
    }

    public String getSelectedValue() {
        return selectedValue;
    }
}
