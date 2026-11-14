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

import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OS} shell command construction and execution.
 *
 * @author Haifeng Li
 */
public class OSTest {

    @Test
    public void givenWindowsCommandWithNestedQuotes_whenExec_thenQuotesArePreserved() {
        // Given
        Assumptions.assumeTrue(OS.isWindows(), "Windows-specific PowerShell quoting test");
        String cmd = "$h = @{ Authorization = \"Bearer dev-admin-token\" }; "
                + "$arr = @(\"alpha beta\", 'gamma'); "
                + "Write-Output ($h['Authorization'] + '|' + $arr[0] + '|' + $arr[1])";

        // When
        String output = OS.exec(cmd, 15000).trim();

        // Then
        assertEquals("Bearer dev-admin-token|alpha beta|gamma", output);
    }

    @Test
    public void givenShellCommand_whenBuildArgs_thenUsesExpectedShell() {
        // Given
        String cmd = "echo \"hello world\"";

        // When
        List<String> args = OS.shell(cmd);

        // Then
        if (OS.isWindows()) {
            assertEquals("powershell.exe", args.getFirst());
            assertTrue(args.contains("-NoProfile"));
            assertTrue(args.contains("-NonInteractive"));
            assertTrue(args.getLast().contains("FromBase64String"));
        } else {
            assertEquals(List.of("bash", "-c", cmd), args);
        }
    }
}
