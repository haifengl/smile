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
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.io.*;

/**
 * A frame to run a process and displays its output.
 * @author Haifeng Li
 */
public class ProcessFrame extends JFrame {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProcessFrame.class);
    private final JTextArea output = new JTextArea();
    private final int scrollback;

    /**
     * Constructor.
     * @param scrollback the number of scrollback lines.
     */
    public ProcessFrame(int scrollback) {
        this.scrollback = scrollback;
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null); // Center the window

        output.setEditable(false);
        output.setFont(Monospace.getFont());
        Monospace.addListener((e) -> {
            SwingUtilities.invokeLater(() -> output.setFont((Font) e.getNewValue()));
        });

        JScrollPane scrollPane = new JScrollPane(output);
        add(scrollPane);
    }

    /**
     * Starts a new process and redirects its output to this frame.
     * @param command a string array containing the program and its arguments.
     */
    public void start(String... command) {
        // Clear previous output
        output.setText("");

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            // Create a thread to read the process's output
            Thread thread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    do {
                        String line = reader.readLine();
                        if (line == null) break;
                        // Append the line to the JTextArea on the Event Dispatch Thread (EDT)
                        SwingUtilities.invokeLater(() -> {
                            output.append(line + System.lineSeparator());
                            int numLinesToTrunk = output.getLineCount() - scrollback;
                            // trunk every 100 overflow lines to minimize the overhead
                            if (numLinesToTrunk > 100) {
                                try {
                                    int posOfLastLineToTrunk = output.getLineEndOffset(numLinesToTrunk - 1);
                                    output.replaceRange("",0, posOfLastLineToTrunk);
                                } catch (BadLocationException ex) {
                                    logger.warn("Failed to trunk scrollback: {}", ex.getMessage());
                                }
                            }
                        });
                    } while (true);
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> output.append("Error reading process output: " + ex.getMessage() +  System.lineSeparator()));
                }
            });
            thread.start();
        } catch (IOException ex) {
            output.append("Failed to start process: " + ex.getMessage() + System.lineSeparator());
        }
    }
}
