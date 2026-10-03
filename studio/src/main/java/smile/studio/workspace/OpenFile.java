/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.workspace;

import java.io.IOException;
import java.nio.file.Path;
import org.fife.rsta.ui.search.SearchListener;

/**
 * A file opened in the workspace. Every implementation is a Swing component
 * so that it can be hosted as a tab of the workspace tabbed pane.
 *
 * <p>An open file is also a {@link SearchListener}, so that the application
 * level Find and Replace dialogs can be routed to whichever tab is selected,
 * whether it is a notebook or a plain text file.
 *
 * @author Haifeng Li
 */
public interface OpenFile extends SearchListener {
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
     * Sets the callback invoked whenever the document changes, so that the
     * workspace can schedule a debounced auto save. The callback runs on the
     * event dispatch thread. Passing {@code null} clears it.
     *
     * @param listener the change listener, or {@code null} to clear it.
     */
    void setChangeListener(Runnable listener);

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
