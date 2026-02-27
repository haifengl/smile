/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Operating system utility functions.
 *
 * @author Haifeng Li
 */
public interface OS {
    String name = System.getProperty("os.name").toLowerCase();

    /**
     * Returns true if the operating system is Windows.
     */
    static boolean isWindows() {
        return name.contains("win");
    }

    /**
     * Returns true if the operating system is MacOS.
     */
    static boolean isMacOS() {
        return name.contains("mac");
    }

    /**
     * Returns true if the operating system is Linux or Unix.
     */
    static boolean isUnix() {
        return name.contains("nix") || name.contains("nux") || name.contains("aix");
    }

    /**
     * Executes a system command in a separate process.
     * @param command the program and its arguments.
     * @param outputConsumer the consumer to handle the output lines from the command.
     * @return the process.
     */
    static Process exec(List<String> command, Consumer<String> outputConsumer) throws IOException {
        var process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        // Create a thread to read the process's output
        if (outputConsumer != null) {
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputConsumer.accept(line);
                    }
                } catch (IOException ex) {
                    outputConsumer.accept("ERROR reading process output: " + ex.getMessage());
                }
            }).start();
        }

        return process;
    }

    /**
     * Executes a shell command in a separate process.
     * @param command the command line to run.
     * @param outputConsumer the consumer to handle the output lines from the command.
     * @return the process.
     */
    static Process exec(String command, Consumer<String> outputConsumer) throws IOException {
        List<String> cmd = new ArrayList<>();
        if (OS.isWindows()) {
            cmd.add("cmd.exe");
            cmd.add("/c");
        } else {
            cmd.add("bash");
            cmd.add("-c");
        }

        // Parse the command string into arguments, respecting quoted substrings
        Pattern pattern = Pattern.compile("\"[^\"]+\"|\\S+");
        pattern.matcher(command)
                .results()
                .map(MatchResult::group)
                .forEach(cmd::add);

        return exec(cmd, outputConsumer);
    }

    /**
     * Runs a bash command with timeout.
     * @param command the command line to run.
     * @param timeout the timeout in milliseconds.
     * @return the output of the command, or error message if failed.
     */
    static String exec(String command, int timeout) {
        try {
            StringBuilder output = new StringBuilder();
            var process = exec(command, line -> output.append(line).append("\n"));

            // Wait for the process to complete and return the exit code
            if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                output.append("\nCommand timed out after ")
                      .append(timeout)
                      .append(" milliseconds.\n");
            }
            return output.toString();
        } catch (InterruptedException | IOException ex) {
            Thread.currentThread().interrupt();
            return "Command execution failed: " + ex.getMessage();
        }
    }
}
