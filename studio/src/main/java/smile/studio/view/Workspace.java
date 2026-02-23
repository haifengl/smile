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
package smile.studio.view;

import javax.swing.*;
import java.nio.file.Path;
import smile.agent.Coder;
import smile.studio.SmileStudio;
import smile.studio.kernel.JavaRunner;

/**
 * A notebook workspace.
 *
 * @author Haifeng Li
 */
public class Workspace extends JSplitPane {
    /** Java execution engine. */
    final JavaRunner runner = new JavaRunner();
    /** The explorer of runtime information. */
    final Explorer explorer = new Explorer(runner);
    /** The pane of conversational agent. */
    final AgentCLI cli;
    /** The editor of notebook. */
    final Notebook notebook;
    /** The project pane consists of explorer and notebook. */
    final JSplitPane project = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

    /**
     * Constructor.
     * @param file the notebook file. If null, a new notebook will be created.
     */
    public Workspace(Path file) {
        super(JSplitPane.HORIZONTAL_SPLIT);
        notebook = new Notebook(file, runner, explorer::refresh);
        cli = new AgentCLI(file.getParent(), runner);

        project.setLeftComponent(explorer);
        project.setRightComponent(notebook);
        project.setResizeWeight(0.15);

        setLeftComponent(project);
        setRightComponent(cli);
        setResizeWeight(0.6);
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
     * Returns the conversational agent component.
     * @return the conversational agent component.
     */
    public AgentCLI cli() {
        return cli;
    }

    /**
     * Returns the project split pane of explorer and notebook.
     * @return the project split pane of explorer and notebook.
     */
    public JSplitPane project() {
        return project;
    }

    /**
     * Returns the Java execution engine.
     * @return the Java execution engine.
     */
    public JavaRunner runner() {
        return runner;
    }

    /**
     * Closes the execution engine and frees resources.
     */
    public void close() {
        runner.close();
    }
}
