/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import javax.swing.*;
import java.io.*;
import smile.studio.model.JavaRunner;

/**
 * A notebook workspace.
 *
 * @author Haifeng Li
 */
public class Workspace extends JSplitPane {
    @Serial
    private static final long serialVersionUID = 1L;
    /** Java execution engine. */
    final JavaRunner runner = new JavaRunner();
    /** The explorer of runtime information. */
    final Explorer explorer = new Explorer(runner);
    /** The editor of notebook. */
    final Notebook notebook;

    /**
     * Constructor.
     * @param file the notebook file. If null, a new notebook will be created.
     */
    public Workspace(File file) {
        super(JSplitPane.HORIZONTAL_SPLIT);
        notebook = new Notebook(file, runner, explorer::refresh);
        setLeftComponent(explorer);
        setRightComponent(notebook);
        setResizeWeight(0.15);
    }

    /**
     * Returns the notebook component.
     * @return the notebook component.
     */
    public Notebook notebook() {
        return notebook;
    }

    /**
     * Returns the explorer component.
     * @return the explorer component.
     */
    public Explorer explorer() {
        return explorer;
    }

    /**
     * Closes the execution engine and frees resources.
     */
    public void close() {
        runner.close();
    }
}
