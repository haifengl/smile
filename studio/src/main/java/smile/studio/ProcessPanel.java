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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.util.Arrays;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import smile.studio.text.Monospaced;
import smile.util.OS;

/**
 * A panel to run a background process and display its console output.
 *
 * @author Haifeng Li
 */
public class ProcessPanel extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProcessPanel.class);
    /** Number of overflow lines that must accumulate before a truncation sweep. */
    private static final int TRUNCATE_BATCH = 100;
    private final JTextArea output = new JTextArea();
    private final int scrollback;
    private Process process;

    /**
     * Constructor.
     *
     * @param scrollback the number of scrollback lines.
     */
    public ProcessPanel(int scrollback) {
        super(new BorderLayout());
        this.scrollback = scrollback;

        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setBackground(Color.BLACK);
        output.setForeground(Color.WHITE);
        output.setFont(Monospaced.getFont());
        Monospaced.addListener(e ->
                SwingUtilities.invokeLater(() -> output.setFont((Font) e.getNewValue())));

        JScrollPane scrollPane = new JScrollPane(output);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Starts a new process and redirects its output to this panel.
     *
     * @param command a string array containing the program and its arguments.
     */
    public void start(String... command) {
        // Clear previous output
        output.setText("");

        try {
            process = OS.exec(Arrays.asList(command), line -> {
                // Append the line to the JTextArea on the Event Dispatch Thread (EDT)
                SwingUtilities.invokeLater(() -> {
                    output.append(line + "\n");
                    int numLinesToTruncate = output.getLineCount() - scrollback;
                    // Truncate every TRUNCATE_BATCH overflow lines to minimise overhead.
                    if (numLinesToTruncate > TRUNCATE_BATCH) {
                        try {
                            int posOfLastLineToTruncate = output.getLineEndOffset(numLinesToTruncate - 1);
                            output.replaceRange("", 0, posOfLastLineToTruncate);
                        } catch (BadLocationException ex) {
                            logger.warn("Failed to truncate scrollback: {}", ex.getMessage());
                        }
                    }
                });
            });

            // Gracefully shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        } catch (IOException ex) {
            output.append("Failed to start process: " + ex.getMessage() + "\n");
        }
    }

    /**
     * Stops the underlying process and any descendant processes.
     */
    public void stop() {
        Process p = process;
        process = null;
        if (p != null) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
            try {
                if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.descendants().forEach(ProcessHandle::destroyForcibly);
                    p.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            }
        }
    }

    /**
     * Returns the underlying process, or {@code null} if not yet started.
     *
     * @return the process.
     */
    public Process getProcess() {
        return process;
    }

    /**
     * Returns the text currently displayed in the output area.
     *
     * @return the output text.
     */
    public String getOutput() {
        return output.getText();
    }
}
