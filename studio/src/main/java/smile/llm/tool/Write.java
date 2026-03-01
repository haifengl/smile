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
Writes a file to the local filesystem.

Usage:
- This tool will overwrite the existing file if there is one at the provided path.
- If this is an existing file, you MUST use the Read tool first to read the file's contents. This tool will fail if you did not read the file first.
- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.
- NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
- Only use emojis if the user explicitly requests it. Avoid writing emojis to files unless asked.
""")
public class Write implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The absolute path to the file to write (must be absolute, not relative)")
    public String filePath;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The content to write to the file")
    public String content;

    @Override
    public String run() {
        return writeFile(filePath, content);
    }

    /** Static helper method to write a file. */
    public static String writeFile(String filePath, String content) {
        var path = Path.of(filePath);
        if (!Files.exists(path.getParent())) {
            return String.format("Error: Directory '%s' does not exist.", path.getParent());
        }

        try {
            Files.writeString(path, content);
            return String.format("File '%s' written successfully.", filePath);
        } catch (Exception e) {
            return String.format("Failed to write file '%s': %s", filePath, e.getMessage());
        }
    }
}
