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

import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("""
Performs exact string replacements in files.

Usage:
- You must use your `Read` tool at least once in the conversation before editing. This tool will error if you attempt an edit without reading the file.
- When editing text from Read tool output, ensure you preserve the exact indentation (tabs/spaces) as it appears AFTER the line number prefix. The line number prefix format is: spaces + line number + tab. Everything after that tab is the actual file content to match. Never include any part of the line number prefix in the old_string or new_string.
- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.
- Only use emojis if the user explicitly requests it. Avoid adding emojis to files unless asked.
- The edit will FAIL if `old_string` is not unique in the file. Either provide a larger string with more surrounding context to make it unique or use `replace_all` to change every instance of `old_string`.
- Use `replace_all` for replacing and renaming strings across the file. This parameter is useful if you want to rename a variable for instance.
""")
public class Edit implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The absolute path to the file to modify (must be absolute, not relative)")
    public String filePath;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The text to replace")
    public String oldString;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The text to replace it with (must be different from old_string)")
    public String newString;

    @JsonPropertyDescription("Replace all occurrences of old_string (default false)")
    public boolean replaceAll = false;

    @Override
    public String run() {
        return editFile(filePath, oldString, newString, replaceAll);
    }

    /** Static helper method to edit a file. */
    public static String editFile(String filePath, String oldString, String newString, boolean replaceAll) {
        var path = Path.of(filePath);
        if (!Files.exists(path)) {
            return String.format("Error: File '%s' does not exist.", path);
        }

        try {
            String content = Files.readString(path);
            String result = replaceAll ? content.replaceAll(oldString, newString)
                    : content.replaceFirst(oldString, newString);
            Files.writeString(path, result);
            return String.format("File '%s' edited successfully.", filePath);
        } catch (Exception e) {
            return String.format("Failed to edit file '%s': %s", filePath, e.getMessage());
        }
    }
}
