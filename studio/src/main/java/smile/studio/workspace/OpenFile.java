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
package smile.studio.workspace;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A file opened in the workspace. Every implementation is a Swing component
 * so that it can be hosted as a tab of the workspace tabbed pane.
 *
 * @author Haifeng Li
 */
public interface OpenFile {
    /**
     * Returns the file backing this tab.
     *
     * @return the file, or {@code null} if the tab is not yet associated
     *         with a file on disk.
     */
    Path getFile();

    /**
     * Sets the file backing this tab and updates the tab title.
     *
     * @param file the file.
     */
    void setFile(Path file);

    /**
     * Returns true if there are no unsaved changes.
     *
     * @return true if there are no unsaved changes.
     */
    boolean isSaved();

    /**
     * Saves the content to the backing file.
     *
     * @throws IOException if an I/O error occurs.
     */
    void save() throws IOException;

    /**
     * Re-reads the content from disk in place, discarding unsaved changes.
     *
     * @throws IOException if an I/O error occurs.
     */
    void reload() throws IOException;

    /**
     * Releases the resources held by this tab.
     */
    void close();
}
