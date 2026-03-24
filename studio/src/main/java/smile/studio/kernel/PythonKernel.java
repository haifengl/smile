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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Python code execution engine.
 *
 * @author Haifeng Li
 */
public class PythonKernel extends Kernel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PythonKernel.class);
    /** Python process. */
    private Process process;
    /** Send commands to the Python process's input. */
    private PrintWriter writer;

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
            PrintStream shellOut = new PrintStream(console, true, StandardCharsets.UTF_8);
            PrintStream shellErr = new PrintStream(console, true, StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder("python3", "-i");
            builder.redirectErrorStream(true);
            process = builder.start();
            writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to start Python REPL: {}", e.getMessage());
        }
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
        // Ctrl-C to stop the current execution.
        writer.print(3);
        writer.flush();
    }

    @Override
    public boolean eval(String code, List<Object> values, Consumer<Object> eventListener) {
        writer.println(code);
        writer.flush();
        StringBuilder sb = new StringBuilder();
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            // Code has finished executing when the primary prompt (>>>) appears.
            while ((line = reader.readLine()) != null && !line.startsWith(">>>")) {
                eventListener.accept(line);
                sb.append(line).append('\n');
            }
            values.add(sb.toString());
            return true;
        } catch (IOException ex) {
            sb.append("Error reading Python output: ")
              .append(ex.getMessage());
            return false;
        }
    }

    @Override
    public List<Variable> variables() {
        return List.of();
    }
}
