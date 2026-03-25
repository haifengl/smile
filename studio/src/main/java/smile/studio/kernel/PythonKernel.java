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
package smile.studio.kernel;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Python code execution engine.
 *
 * @author Haifeng Li
 */
public class PythonKernel extends Kernel {
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
            logger.info("To install iPython, run `pip install ipython` in your terminal.");
            JOptionPane.showMessageDialog(null,
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
    public boolean eval(String code, List<Object> values, Consumer<Object> eventListener) {
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
                    eventListener.accept(line);
                    out.println(line);
                    out.flush();

                    // Code has finished executing when the prompt appears.
                    if (pythonPromptRegex.matcher(line).find()) break;
                    // Or error happens
                    //if (pythonErrorRegex.matcher(line).find()) break;
                }
            }
            return true;
        } catch (IOException ex) {
            out.println("Error reading Python output: " + ex.getMessage());
            out.flush();
            return false;
        }
    }

    @Override
    public List<Variable> variables() {
        return List.of();
    }
}
