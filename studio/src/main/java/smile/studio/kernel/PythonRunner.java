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

/**
 * Python code execution engine.
 *
 * @author Haifeng Li
 */
public class PythonRunner extends Runner {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PythonRunner.class);
    /** Python process. */
    private final Process process;
    /** Send commands to the Python process's input. */
    private final PrintWriter writer;

    /**
     * Constructor.
     */
    public PythonRunner() throws IOException {
        PrintStream shellOut = new PrintStream(console, true, StandardCharsets.UTF_8);
        PrintStream shellErr = new PrintStream(console, true, StandardCharsets.UTF_8);
        ProcessBuilder builder = new ProcessBuilder("python3", "-i"); 
        builder.redirectErrorStream(true);
        process = builder.start();
        writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream()));
    }

    @Override
    public void stop() {
        process.destroy();
    }

    /**
     * Evaluates a code block.
     * @param code a code block.
     * @return the value of last variable snippet. Or null if no variables.
     */
    public String eval(String code) {
        writer.println(code);
        writer.flush();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            // Code has finished executing when the primary prompt (>>>) appears.
            while ((line = reader.readLine()) != null && !line.startsWith(">>>")) {
                sb.append(line).append('\n');
            }
        } catch (IOException ex) {
            logger.error("Error reading Python output", ex);
        }
        return sb.toString();
    }
}
