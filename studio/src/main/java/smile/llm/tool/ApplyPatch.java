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
import java.util.List;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.llm.Conversation;
import smile.llm.client.ResponseHandler;
import smile.util.OS;

@JsonClassDescription("""
Use the ApplyDiff tool to edit files. The patch text contains a set of instructions in the unified diff format (invoked with diff -u) for modifying one or more files. 

A standard unified patch file presents the changes in a single, unified view with a few lines of context. Changes are organized into sections called "hunks". The file consists of three main parts: 
- **File Header**: identifies the files being , often including timestamps. The header starts with 
  - `--- a/file` (original file)
  - `+++ b/file` (new/modified file)
- **Hunk Header**: Marks a specific block of changes within the file, formatted as `@@ -old_start,old_count +new_start,new_count @@`.
  - `-old_start,old_count`: The starting line number and total number of lines in the original file's chunk.
  - `+new_start,new_count`: The starting line number and total number of lines in the new file's chunk.
- **The Hunk Content**: The actual line-by-line differences.Change Indicators:
  - Lines beginning with a space ( ) are context lines, unchanged in both files.
  - Lines beginning with a minus sign (-) are lines removed from the original file.
  - Lines beginning with a plus sign (+) are lines added to the new file.

Example patch:

```
--- a/old_script.py
+++ b/new_script.py
@@ -1,4 +1,4 @@
 def main():
-  print("Hello World")
+  print("Hello Unified Diff")
   return 0
```
""")
public class ApplyPatch implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The full patch text that describes all changes to be made")
    public String patch;

    @Override
    public String run(Conversation conversation, ResponseHandler handler) {
        handler.onStatus("Applying patch");
        return applyPatch(patch);
    }

    /** Static helper method to apply patch. */
    public static String applyPatch(String patch) {
        try {
            Path patchFile = Files.createTempFile("smile", ".patch");
            patchFile.toFile().deleteOnExit();
            Files.writeString(patchFile, patch);

            String command = "git apply";
            if (OS.isWindows()) {
                command += " < " + patchFile;
            } else {
                command = "cat " + patchFile + " | " + command + " -";
            }

            String output = OS.exec(command, 60000);
            if (output.length() > 30000) {
                output = output.substring(0, 30000) + "\n[Output truncated due to length]";
            }
            return output;
        } catch (Exception e) {
            return String.format("Failed to apply patch file: %s", e.getMessage());
        }
    }

    /**
     * The specification for ApplyDiff tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(ApplyPatch.class,
                    List.of(ApplyPatch.class.getMethod("applyPatch", String.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(ApplyPatch.class, List.of());
    }
}
