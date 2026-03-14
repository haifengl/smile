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
import java.util.concurrent.CompletableFuture;

/**
 * A UI widget for agent to ask the user questions.
 *
 * @author Haifeng Li
 */
public class Question extends JPanel implements ActionListener {
    /** Users may select "Other" to provide custom text input. */
    static final String OTHER = "Other";
    private final List<String> choices;
    private final List<JRadioButton> radioButtons = new ArrayList<>();
    private final ButtonGroup buttonGroup = new ButtonGroup();
    private final JTextArea customTextInput = new JTextArea(3, 40);;
    private final JButton okButton, cancelButton;
    private final CompletableFuture<String> answer = new CompletableFuture<>();

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

        // Create the choice panel with a vertical layout
        JPanel choicePane = new JPanel();
        choicePane.setLayout(new BoxLayout(choicePane, BoxLayout.Y_AXIS));

        // Add radio buttons to the panel and group
        for (var choice : choices) {
            var radioButton = new JRadioButton(choice);
            radioButton.addActionListener(this);
            radioButtons.add(radioButton); 
            buttonGroup.add(radioButton);
            choicePane.add(radioButton);
        }
        
        // Add the text area below the "Other" radio button
        // Use a JScrollPane for the text area for better usability
        if (choices.contains(OTHER)) {
            // Initialize components
            customTextInput.setEnabled(false);
            customTextInput.setLineWrap(true);
            customTextInput.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(customTextInput);
            choicePane.add(scrollPane);
        }
        add(choicePane, BorderLayout.CENTER);

        // Add buttons
        JPanel buttonPanel = new JPanel();
        okButton = new JButton("OK");
        okButton.addActionListener(this);
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            // Get the selected value
            for (int i = 0; i < radioButtons.size(); i++) {
                if (radioButtons.get(i).isSelected()) {
                    if (choices.get(i).equals(OTHER)) {
                        answer.complete(customTextInput.getText());
                    } else {
                        answer.complete(choices.get(i));
                    }
                    break;
                }
            }
            setVisible(false); // Close the dialog

        } else if (e.getSource() == cancelButton) {
            answer.complete(null); // Indicate cancellation
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

    /**
     * Returns a CompletableFuture that will be completed with the user's answer
     * when they click OK, or null if they click Cancel.
     */
    public CompletableFuture<String> ask() {
        return answer;
    }
}
