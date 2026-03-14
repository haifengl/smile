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
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.llm.Conversation;

@JsonClassDescription("""
Completely replaces the contents of a specific cell in a SMILE notebook (.java file) with new source. SMILE notebooks are interactive documents that combine code, text, and visualizations, commonly used for data analysis and scientific computing. The notebook_path parameter must be an absolute path, not a relative path. The cell_number is 0-indexed. Use edit_mode=insert to add a new cell at the index specified by cell_number. Use edit_mode=delete to delete the cell at the index specified by cell_number.
""")
public class NotebookEdit implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The absolute path to the SMILE notebook file to edit (must be absolute, not relative)")
    public String notebook_path;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The new source for the cell")
    public String new_source;

    @JsonPropertyDescription("The ID of the cell to edit. When inserting a new cell, the new cell will be inserted after the cell with this ID, or at the beginning if not specified.")
    public String cell_id;

    @Override
    public String run(Conversation conversation, ToolCallListener listener) {
        listener.onStatus("Editing " + notebook_path);
        return editNotebook(notebook_path, new_source, cell_id);
    }

    /** Static helper method to edit a notebook. */
    public static String editNotebook(String notebook_path, String new_source, String cell_id) {
        return "Error: Notebook editing is not yet implemented.";
    }

    /**
     * The specification for NotebookEdit tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(NotebookEdit.class,
                    List.of(NotebookEdit.class.getMethod("editNotebook", String.class, String.class, String.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(NotebookEdit.class, List.of());
    }
}
