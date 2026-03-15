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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A UI widget for agent to ask the user questions.
 *
 * @author Haifeng Li
 */
public class Question {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Very short label (max 30 chars).")
    public String header;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Clear, concise description of the question you want to ask the user.")
    public String question;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The available choices for the user to select. Users will also have the option to select 'Other' to provide custom input.")
    public List<String> choices;

    @JsonPropertyDescription("Set to true to allow multiple answers to be selected for a question.")
    public boolean multiSelect = false;

    private final CompletableFuture<String> answer = new CompletableFuture<>();

    /**
     * Constructor.
     * @param header a very short label for the question.
     * @param question the question to ask.
     * @param choices the list of choices. If the list contains "Other", a text area will be provided for custom input.
     * @param multiSelect whether to allow multiple selections (checkboxes) or single selection (radio buttons).
     */
    public Question(String header, String question, List<String> choices, boolean multiSelect) {
        this.header = header;
        this.question = question;
        this.choices = choices;
        this.multiSelect = multiSelect;
    }

    /**
     * Returns a CompletableFuture that will be completed with the user's answer
     * when they click OK, or null if they click Cancel.
     */
    public CompletableFuture<String> ask() {
        return answer;
    }

    /** Creates the GUI component for this question. */
    public JComponent createGUI() {
        return new GUI();
    }

    private class GUI extends JPanel implements ActionListener {
        private final List<JToggleButton> choiceButtons = new ArrayList<>();
        private final JTextArea customTextInput = new JTextArea(4, 80);
        // Use a JScrollPane for the text area for better usability
        private final JScrollPane customTextPane = new JScrollPane(customTextInput);
        private final JPanel choicePane = new JPanel();
        private final JButton okButton;
        private final JButton cancelButton;

        public GUI() {
            super(new BorderLayout());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(16,8,8,8),
                    BorderFactory.createTitledBorder(header)));

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
                // Choice panel with a vertical layout
                choicePane.setLayout(new BoxLayout(choicePane, BoxLayout.Y_AXIS));
                choicePane.setBorder(BorderFactory.createEmptyBorder(16,8,8,8));

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
                    button.setAlignmentX(Component.LEFT_ALIGNMENT);
                    button.setBorder(null);
                    button.addActionListener(this);
                    choiceButtons.add(button);
                    choicePane.add(button);
                    choicePane.add(Box.createRigidArea(new Dimension(0, 8)));
                }

                // Add the text area below the "Other" radio button
                customTextInput.setLineWrap(true);
                customTextInput.setWrapStyleWord(true);
                customTextPane.setAlignmentX(Component.LEFT_ALIGNMENT);
                customTextPane.setBorder(BorderFactory.createEmptyBorder(8,24,8,8));

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
            JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPane.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
            add(buttonPane, BorderLayout.SOUTH);
            if (!choices.isEmpty()) {
                okButton = new JButton("OK");
                cancelButton = new JButton("Cancel");
                // Disable buttons until user makes a selection or provides input
                okButton.setEnabled(false);
                cancelButton.setEnabled(false);
                buttonPane.add(okButton);
            } else {
                okButton = new JButton("Yes");
                cancelButton = new JButton("No");
                okButton.setEnabled(true);
                // Show Cancel button only if there are no choices.
                // Otherwise, user can skip answering the question.
                cancelButton.setEnabled(true);

                buttonPane.add(okButton);
                buttonPane.add(Box.createRigidArea(new Dimension(20, 0)));
                buttonPane.add(cancelButton);
            }
            okButton.addActionListener(this);
            cancelButton.addActionListener(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == okButton) {
                if (choices.isEmpty()) {
                    answer.complete("Yes");
                } else {
                    String result = getAnswer();
                    if (!result.isBlank()) {
                        answer.complete(result);
                    }
                }
            } else if (e.getSource() == cancelButton) {
                if (choices.isEmpty()) {
                    answer.complete("No");
                } else {
                    answer.complete(null); // Indicate cancellation
                }
            } else {
                // Handle choice button selection to enable/disable text area
                if (((JToggleButton) e.getSource()).getText().equals(choices.getLast())) {
                    choicePane.add(customTextPane);
                    customTextInput.requestFocus(); // Set focus for typing
                    checkCustomInput();
                } else {
                    choicePane.remove(customTextPane);
                    okButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                }
            }

            if (answer.isDone()) {
                okButton.setEnabled(false);
                cancelButton.setEnabled(false);
                for (var button : choiceButtons) {
                    button.setEnabled(false);
                }
            }
        }

        /** Enables or disables the OK and Cancel buttons based on the custom text input. */
        private void checkCustomInput() {
            boolean enabled = !customTextInput.getText().isBlank();
            okButton.setEnabled(enabled);
            cancelButton.setEnabled(enabled);
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
    }
}
