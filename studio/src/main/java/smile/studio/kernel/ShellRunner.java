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

import smile.util.OS;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Command execution engine.
 *
 * @author Haifeng Li
 */
public class ShellRunner extends Runner {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ShellRunner.class);
    private final PrintStream shellOut = new PrintStream(console, true, StandardCharsets.UTF_8);
    private Process process;

    /**
     * Executes the specified system command in a separate process.
     * @param command the program and its arguments.
     * @return the exit value of the command. By convention, the value 0 indicates normal termination.
     */
    public int exec(List<String> command) {
        try {
            process = OS.exec(command, shellOut::println);
            // Wait for the process to complete and return the exit code
            if (process.waitFor(600000, TimeUnit.MILLISECONDS)) {
                return process.exitValue();
            } else {
                process.destroyForcibly();
                return -1;
            }
        } catch (IOException | InterruptedException ex) {
            shellOut.println("Failed to execute '" + String.join(" " , command) + "': " + ex.getMessage());
        }
        return -1;
    }

    @Override
    public void stop() {
        process.destroy();
    }
}
