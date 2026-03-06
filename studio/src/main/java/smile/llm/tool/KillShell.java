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
package smile.llm.tool;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.llm.Conversation;

@JsonClassDescription("""
- Kills a running background bash shell by its ID
- Takes a shell_id parameter identifying the shell to kill
- Returns a success or failure status\s
- Use this tool when you need to terminate a long-running shell
- Shell IDs can be found using the /bashes command
""")
public class KillShell implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The ID of the background shell to kill")
    public String shellId;

    @Override
    public String run(Conversation conversation) {
        return killShell(shellId);
    }

    /** Static helper method to kill a process. */
    public static String killShell(String shellId) {
        try {
            long pid = Long.parseLong(shellId);
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isPresent()) {
                ProcessHandle ph = handle.get();
                boolean destroyed = ph.destroy(); // Try graceful termination
                if (!destroyed) {
                    ph.destroyForcibly(); // Forceful kill if needed
                }
                return "Process " + shellId + " terminated.";
            } else {
                return "Process " + shellId + " not found.";
            }
        } catch (NumberFormatException e) {
            return "Error: " + shellId + " is not a valid pid.";
        }
    }

    /**
     * The specification for KillShell tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(KillShell.class,
                    List.of(KillShell.class.getMethod("killShell", String.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(KillShell.class, List.of());
    }
}
