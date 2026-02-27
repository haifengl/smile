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

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.util.OS;

@JsonClassDescription("""
Executes a given bash command in a persistent shell session with optional timeout, ensuring proper handling and security measures.

IMPORTANT: This tool is for terminal operations like git, npm, docker, etc. DO NOT use it for file operations (reading, writing, editing, searching, finding files) - use the specialized tools for this instead.

Before executing the command, please follow these steps:

1. Directory Verification:
    - If the command will create new directories or files, first use `ls` to verify the parent directory exists and is the correct location
    - For example, before running "mkdir foo/bar", first use `ls foo` to check that "foo" exists and is the intended parent directory

2. Command Execution:
    - Always quote file paths that contain spaces with double quotes (e.g., cd "path with spaces/file.txt")
    - Examples of proper quoting:
        - cd "/Users/name/My Documents" (correct)
        - cd /Users/name/My Documents (incorrect - will fail)
        - python "/path/with spaces/script.py" (correct)
        - python /path/with spaces/script.py (incorrect - will fail)
    - After ensuring proper quoting, execute the command.
    - Capture the output of the command.

Usage notes:
- The command argument is required.
- You can specify an optional timeout in milliseconds (up to 600000ms / 10 minutes). If not specified, commands will timeout after 240000ms (4 minutes).
- It is very helpful if you write a clear, concise description of what this command does in 5-10 words.
- If the output exceeds 30000 characters, output will be truncated before being returned to you.
- You can use the `run_in_background` parameter to run the command in the background, which allows you to continue working while the command runs. You can monitor the output using the Bash tool as it becomes available. Never use `run_in_background` to run 'sleep' as it will return immediately. You do not need to use '&' at the end of the command when using this parameter.

- Avoid using Bash with the `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`, or `echo` commands, unless explicitly instructed or when these commands are truly necessary for the task. Instead, always prefer using the dedicated tools for these commands:
    - File search: Use Glob (NOT find or ls)
    - Content search: Use Grep (NOT grep or rg)
    - Read files: Use Read (NOT cat/head/tail)
    - Edit files: Use Edit (NOT sed/awk)
    - Write files: Use Write (NOT echo >/cat <<EOF)
    - Communication: Output text directly (NOT echo/printf)
- When issuing multiple commands:
    - If the commands are independent and can run in parallel, make multiple Bash tool calls in a single message. For example, if you need to run "git status" and "git diff", send a single message with two Bash tool calls in parallel.
    - If the commands depend on each other and must run sequentially, use a single Bash call with '&&' to chain them together (e.g., `git add . && git commit -m "message" && git push`). For instance, if one operation must complete before another starts (like mkdir before cp, Write before Bash for git operations, or git add before git commit), run these operations sequentially instead.
    - Use ';' only when you need to run commands sequentially but don't care if earlier commands fail
    - DO NOT use newlines to separate commands (newlines are ok in quoted strings)
- Try to maintain your current working directory throughout the session by using absolute paths and avoiding usage of `cd`. You may use `cd` if the User explicitly requests it.
  <good-example>
  pytest /foo/bar/tests
  </good-example>
  <bad-example>
  cd /foo/bar && pytest tests
  </bad-example>
""")
public class Bash {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The command to execute")
    public String command;

    @JsonPropertyDescription("Optional timeout in milliseconds (max 600000)")
    public int timeout = 240000;

    @JsonPropertyDescription("Clear, concise description of what this command does in 5-10 words, in active voice. Examples:\\nInput: ls\\nOutput: List files in current directory\\n\\nInput: git status\\nOutput: Show working tree status\\n\\nInput: npm install\\nOutput: Install package dependencies\\n\\nInput: mkdir foo\\nOutput: Create directory 'foo")
    public String description;

    @JsonPropertyDescription("Set to true to run this command in the background.")
    boolean runInBackground = false;

    /** Executes the tool. */
    public String run() {
        return runCommand(command, timeout, runInBackground);
    }

    /** Static helper method to run a bash command with timeout and background execution option. */
    public static String runCommand(String command, int timeout, boolean runInBackground) {
        String output = OS.exec(command, timeout);
        if (output.length() > 30000) {
            output = output.substring(0, 30000) + "\n[Output truncated due to length]";
        }
        return output;
    }
}
