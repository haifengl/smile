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

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("""
Appends to a file in the local filesystem.

Usage:
- This tool will append to the existing file if there is one at the provided path.
- If the file doesn't exist, this tool will create the file.
- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.
- NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
- Only use emojis if the user explicitly requests it. Avoid writing emojis to files unless asked.
""")
public class Append implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The absolute path to the file to append (must be absolute, not relative)")
    public String filePath;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The content to append to the file")
    public String content;

    @Override
    public String run() {
        return appendFile(filePath, content);
    }

    /** Static helper method to append to a file. */
    public static String appendFile(String filePath, String content) {
        var path = Path.of(filePath);
        if (!Files.exists(path.getParent())) {
            return String.format("Error: Directory '%s' does not exist.", path.getParent());
        }

        try {
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return String.format("File '%s' appended successfully.", filePath);
        } catch (Exception e) {
            return String.format("Failed to append file '%s': %s", filePath, e.getMessage());
        }
    }
}
