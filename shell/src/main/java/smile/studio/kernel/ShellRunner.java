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
package smile.studio.kernel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Command execution engine.
 *
 * @author Haifeng Li
 */
public class ShellRunner extends Runner {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ShellRunner.class);
    private PrintStream shellOut = new PrintStream(console, true, StandardCharsets.UTF_8);
    private Process process;

    /**
     * Executes the specified system command in a separate process.
     * @param command a specified system command.
     * @return the exit value of the command. By convention, the value 0 indicates normal termination.
     */
    public int exec(String command) {
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            // Read output from the command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                shellOut.println(line);
            }

            // Wait for the process to complete and return the exit code
            return process.waitFor();
        } catch (IOException | InterruptedException ex) {
            logger.error("Failed to execute {}", command, ex);
        }
        return -1;
    }

    /**
     * Executes the specified system commands sequentially.
     * Stops the execution whenever a command return a nonzero code.
     * @param commands a list of system commands.
     * @return the exit value of the last executed command.
     */
    public int exec(String... commands) {
        for (String command : commands) {
            int retcode = exec(command);
            if (retcode != 0) {
                return retcode;
            }
        }
        return 0;
    }

    @Override
    public void stop() {
        process.destroy();
    }
}
