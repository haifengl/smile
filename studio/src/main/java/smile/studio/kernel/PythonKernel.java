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
package smile.studio.kernel;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Python code execution engine.
 *
 * @author Haifeng Li
 */
public class PythonKernel extends Kernel<String> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PythonKernel.class);
    /** Print Python output to the output area. */
    private final PrintWriter out = new PrintWriter(console, true, StandardCharsets.UTF_8);
    /** Regex to detect iPython prompt. */
    private final Pattern pythonPromptRegex = Pattern.compile("^In \\[(\\d+)\\]:");
    /** Regex to detect Python errors. */
    private final Pattern pythonErrorRegex = Pattern.compile("^(\\w+Error:)");
    /** Python process. */
    private Process process;
    /** Send commands to the Python process's input. */
    private PrintWriter writer;
    /** Read output from the Python process. */
    private BufferedReader reader;

    /**
     * Constructor.
     */
    public PythonKernel() throws IOException {
        restart();
    }

    @Override
    public synchronized void close() {
        if (process != null) {
            process.destroy();
            writer.close();
            process = null;
            writer = null;
        }
    }

    @Override
    public void restart() {
        close();
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "ipython", "--simple-prompt", "--colors", "NoColor",
                    "--no-banner", "--no-pdb");
            builder.redirectErrorStream(true);
            process = builder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to start iPython REPL: {}", e.getMessage());
            JOptionPane.showMessageDialog(
                    null,
                    "To install iPython, run `pip install ipython` in your terminal.",
                    "iPython",
                    JOptionPane.INFORMATION_MESSAGE);

        }
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
        // Ctrl-C to stop the current execution.
        writer.write(3);
        writer.flush();
    }

    @Override
    public boolean eval(String code, List<Object> values) {
        boolean success = true;
        try {
            // paste magic command allows us to send multi-line code to iPython REPL.
            writer.println("%cpaste");
            writer.println(code);
            // Stop paste mode. Two ending newlines are critical.
            writer.println("\n--\n");
            writer.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                boolean output = !line.contains("Pasting code;");
                if (output) {
                    // Remove iPython's paste prefix
                    line = line.replaceFirst("^:.+", "");
                    // Code has finished executing when the prompt appears.
                    if (pythonPromptRegex.matcher(line).find()) break;

                    // If error happens
                    if (pythonErrorRegex.matcher(line).find()) {
                        process(List.of(line));
                        success = false;
                    }

                    out.println(line);
                    out.flush();
                }
            }
        } catch (IOException ex) {
            out.println("Error reading Python output: " + ex.getMessage());
            out.flush();
            success = false;
        }

        return success;
    }

    @Override
    public void process(List<String> errors) {
        for (String error : errors) {
            logger.error(error);
        }
    }

    @Override
    public List<Variable> variables() {
        return List.of();
    }
}
