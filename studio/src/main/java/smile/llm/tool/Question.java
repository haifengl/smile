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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A UI widget for agent to ask the user questions.
 *
 * @author Haifeng Li
 */
public class Question extends JPanel implements ActionListener {
    private final List<String> choices;
    private final List<JToggleButton> choiceButtons = new ArrayList<>();
    private final JTextArea customTextInput = new JTextArea(3, 40);;
    private final JButton okButton;
    private final JButton cancelButton;
    private final CompletableFuture<String> answer = new CompletableFuture<>();

    /**
     * Constructor.
     * @param question the question to ask.
     * @param choices the list of choices. If the list contains "Other", a text area will be provided for custom input.
     * @param multiSelect whether to allow multiple selections (checkboxes) or single selection (radio buttons).
     */
    public Question(String question, List<String> choices, boolean multiSelect) {
        super(new BorderLayout());
        this.choices = choices;

        // Add the question label at the top
        JTextArea questionLabel = new JTextArea(question);
        questionLabel.setEditable(false);
        questionLabel.setCursor(null);
        questionLabel.setOpaque(false);
        questionLabel.setFocusable(false);
        questionLabel.setLineWrap(true);
        questionLabel.setWrapStyleWord(true);
        add(questionLabel, BorderLayout.NORTH);

        // Initialize choice panel if there are choices to display
        if (!choices.isEmpty()) {
            // Create the choice panel with a vertical layout
            JPanel choicePane = new JPanel();
            choicePane.setLayout(new BoxLayout(choicePane, BoxLayout.Y_AXIS));

            // Add radio buttons to the panel and group
            ButtonGroup buttonGroup = new ButtonGroup();
            for (var choice : choices) {
                JToggleButton button;
                if (multiSelect) {
                    button = new JCheckBox(choice);
                } else {
                    button = new JRadioButton(choice);
                    buttonGroup.add(button);
                }
                button.addActionListener(this);
                choiceButtons.add(button);
                choicePane.add(button);
            }

            // Add the text area below the "Other" radio button
            customTextInput.setEnabled(false);
            customTextInput.setLineWrap(true);
            customTextInput.setWrapStyleWord(true);
            // Use a JScrollPane for the text area for better usability
            JScrollPane scrollPane = new JScrollPane(customTextInput);
            choicePane.add(scrollPane);

            customTextInput.getDocument().addDocumentListener(new DocumentListener() {
                public void removeUpdate(DocumentEvent e) {
                    checkCustomInput();
                }
                public void insertUpdate(DocumentEvent e) {
                    checkCustomInput();
                }
                public void changedUpdate(DocumentEvent e) {
                    // This method is for attribute changes, not relevant for plain text
                }
            });

            add(choicePane, BorderLayout.CENTER);
        }

        // Add buttons
        JPanel buttonPanel = new JPanel();
        add(buttonPanel, BorderLayout.SOUTH);
        if (!choices.isEmpty()) {
            okButton = new JButton("OK");
            cancelButton = new JButton("Cancel");
            // Disable buttons until user makes a selection or provides input
            okButton.setEnabled(false);
            cancelButton.setEnabled(false);
            buttonPanel.add(okButton);
        } else {
            okButton = new JButton("Yes");
            cancelButton = new JButton("No");
            okButton.setEnabled(true);
            // Show Cancel button only if there are no choices.
            // Otherwise, user can skip answering the question.
            cancelButton.setEnabled(true);

            buttonPanel.add(okButton);
            buttonPanel.add(Box.createRigidArea(new Dimension(10, 0)));
            buttonPanel.add(cancelButton);
        }
        okButton.addActionListener(this);
        cancelButton.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            if (choices.isEmpty()) {
                this.answer.complete("Yes");
            } else {
                String answer = getAnswer();
                if (!answer.isBlank()) {
                    this.answer.complete(answer);
                }
            }
        } else if (e.getSource() == cancelButton) {
            if (choices.isEmpty()) {
                this.answer.complete("No");
            } else {
                answer.complete(null); // Indicate cancellation
            }
        } else {
            // Handle choice button selection to enable/disable text area
            if (((JToggleButton) e.getSource()).getText().equals(choices.getLast())) {
                customTextInput.setEnabled(true);
                customTextInput.requestFocus(); // Set focus for typing
                checkCustomInput();
            } else {
                okButton.setEnabled(true);
                cancelButton.setEnabled(true);
                customTextInput.setEnabled(false);
            }
        }
    }

    /** Enables or disables the OK and Cancel buttons based on the custom text input. */
    private void checkCustomInput() {
        if (customTextInput.getText().isBlank()) {
            okButton.setEnabled(false);
            cancelButton.setEnabled(false);
        }
    }

    /** Retrieves the user's answer based on their selection. */
    private String getAnswer() {
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < choiceButtons.size(); i++) {
            if (choiceButtons.get(i).isSelected()) {
                String choice;
                if (i == choices.size() - 1) { // "Other" option
                    choice = customTextInput.getText();
                } else {
                    choice = choices.get(i);
                }
                if (!answer.isEmpty()) answer.append(", ");
                answer.append(choice);
            }
        }
        return answer.toString();
    }

    /**
     * Returns a CompletableFuture that will be completed with the user's answer
     * when they click OK, or null if they click Cancel.
     */
    public CompletableFuture<String> ask() {
        return answer;
    }
}
