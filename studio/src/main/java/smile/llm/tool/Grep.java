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
import java.util.function.Consumer;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.llm.Conversation;
import smile.util.OS;
import smile.util.Strings;

@JsonClassDescription("""
A powerful search tool built on ripgrep

Usage:
- ALWAYS use Grep for search tasks. NEVER invoke `grep` or `rg` as a Bash command. The Grep tool has been optimized for correct permissions and access.
- Supports full regex syntax (e.g., "log.*Error", "function\\s+\\w+")
- Filter files with glob parameter (e.g., "*.js", "**/*.tsx") or type parameter (e.g., "js", "py", "rust")
- Output modes: "content" shows matching lines, "files_with_matches" shows only file paths (default), "count" shows match counts
- Use Task tool for open-ended searches requiring multiple rounds
- Pattern syntax: Uses ripgrep (not grep) - literal braces need escaping (use `interface\\{\\}` to find `interface{}` in Go code)
- Multiline matching: By default patterns match within single lines only. For cross-line patterns like `struct \\{[\\s\\S]*?field`, use `multiline: true`
""")
public class Grep implements Tool {
    // Ensure ripgrep is installed when the class is loaded
    static {
        install();
    }

    @JsonProperty(required = true)
    @JsonPropertyDescription("The regular expression pattern to search for in file contents")
    public String pattern;

    @JsonPropertyDescription("File or directory to search in (rg PATH). Defaults to current working directory.")
    public String path;

    @JsonPropertyDescription("Glob pattern to filter files (e.g. \\\"*.js\\\", \\\"*.{ts,tsx}\\\") - maps to rg --glob")
    public String glob;

    @JsonPropertyDescription("File type to search (rg --type). Common types: js, py, rust, go, java, etc. More efficient than include for standard file types.")
    public String type;

    @JsonPropertyDescription("Enable multiline mode where . matches newlines and patterns can span lines (rg -U --multiline-dotall). Default: false.")
    public boolean multiline = false;

    @Override
    public String run(Conversation conversation, Consumer<String> statusUpdate) {
        if (path == null) {
            path = Tool.cwd(conversation).toString();
        }
        statusUpdate.accept("Searching files matching" + pattern + " in " + path);
        return grepFiles(pattern, path, glob, type, multiline);
    }

    /** Static helper method to edit a file. */
    public static String grepFiles(String pattern, String path, String glob, String type, boolean multiline) {
        String command = "rg -nH --hidden --no-messages --field-match-separator='|' --regexp " + pattern;
        if (!Strings.isNullOrBlank(glob)) {
            command += " --glob " + glob;
        }
        if (!Strings.isNullOrBlank(type)) {
            command += " --type " + type;
        }
        if (multiline) {
            command += " -U --multiline-dotall";
        }
        command += " \"" + path + '"';

        return Bash.runCommand(command, 240000);
    }

    /**
     * The specification for Grep tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(Grep.class, List.of(Grep.class.getMethod("grepFiles",
                    String.class, String.class, String.class, String.class, boolean.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(Grep.class, List.of());
    }

    /**
     * Checks if ripgrep is installed and attempts installation if not.
     * @return true if ripgrep is available or was successfully installed, false otherwise.
     */
    public static boolean install() {
        // Check if rg is installed
        try {
            Process process = new ProcessBuilder("rg", "--version").start();
            if (process.waitFor() == 0) {
                return true; // rg is available
            }
        } catch (Exception e) {
            // rg is not installed
            System.err.println("ripgrep is not available. Attempt to install...");
        }

        // Attempt to install ripgrep
        String os = System.getProperty("os.name").toLowerCase();
        try {
            ProcessBuilder installProcess;
            if (OS.isWindows()) {
                installProcess = new ProcessBuilder("powershell", "-Command",
                        "winget install -e --id BurntSushi.ripgrep.MSVC");
            } else if (OS.isMacOS()) {
                installProcess = new ProcessBuilder("brew", "install", "ripgrep");
            } else if (OS.isUnix()) {
                installProcess = new ProcessBuilder("sh", "-c",
                        "if command -v apt-get > /dev/null; then sudo apt-get install -y ripgrep; " +
                        "elif command -v yum > /dev/null; then sudo yum install -y ripgrep; " +
                        "elif command -v pacman > /dev/null; then sudo pacman -S --noconfirm ripgrep; " +
                        "else echo 'Unsupported Linux distribution. Please install ripgrep manually.'; exit 1; fi");
            } else {
                System.err.println("Unsupported OS. Please install ripgrep manually.");
                return false;
            }

           if (installProcess.inheritIO().start().waitFor() == 0) {
               if (OS.isWindows()) {
                   System.out.println("Path environment variable modified; restart your shell to use the new value.");
                   System.exit(0);
               }
               return true; // Installation succeeded
            } else {
                System.err.println("Failed to install ripgrep. Please install it manually.");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Failed to start installation process: " + e.getMessage());
            return false;
        }
    }
}
