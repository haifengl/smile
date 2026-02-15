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
package smile.agent;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Custom commands let users save and reuse their favorite or
 * most frequently used prompts as personal shortcuts. Commands
 * may be specific to a single project or are available globally
 * across all projects to ensure consistency.
 *
 * @author Haifeng Li
 */
public record Command(String name,
                      String description,
                      String content) {
    /**
     * Reads the command from a file with UTF-8 charset.
     * @param path the path to the file.
     * @return the command.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static Command from(Path path) throws IOException {
        Markdown md = Markdown.from(path);
        var fm = md.frontMatter();
        return new Command(
                fm.get("name"),
                fm.get("description"),
                md.content());
    }
}
