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
import com.formdev.flatlaf.util.SystemFileChooser;
import smile.agent.Analyst;
import smile.shell.JShell;
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
    final Explorer explorer;
    /** The pane of conversational agent. */
    final AgentCLI cli;
    /** The editor of notebook. */
    final Notebook notebook;
    /** The project pane consists of explorer and notebook. */
    final JSplitPane project = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

    /**
     * Constructor.
     * @param file the notebook file. If null, a new notebook will be created.
     * @param fileChooser the file chooser for saving models.
     */
    public Workspace(Path file, SystemFileChooser fileChooser) {
        super(JSplitPane.HORIZONTAL_SPLIT);
        explorer = new Explorer(runner, fileChooser);
        notebook = new Notebook(file, runner, explorer::refresh);
        cli = new AgentCLI(analyst(file.getParent()));

        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") + """
        =====================================================================
        Welcome! I am Clair, your AI assistant for machine learning modeling.
        
        /help for help, /init for initializing your project
        cwd:\s""" + System.getProperty("user.dir"),

        """
        As a state-of-the-art machine learning engineering agent,
        I can help you with:
        
        🤖 Automatic end-to-end ML/AI solutions based on your requirements.
        🔍 Best practices and state-of-the-art methods with web search.
        🏅 Targeted code block refinement by ablation study.
        🤝 Improved solution using iterative ensemble strategy.
        💡 High-quality code completion and generation.
        📊 Advanced interactive data visualization.
        📂 Process data from CSV, ARFF, JSON, Avro, Parquet, Iceberg, to SQL.
        🌐 Built-in inference server.
        
        Tips for getting started:
        1. Ctrl + ENTER to execute your intents.
        2. Ctrl + SPACE to show slash command argument hint.
        3. Run /init to create a SMILE.md file with instructions for agent.
        4. Be as specific as you would with another data scientist for the best result.
        5. Data visualization can be feed to AI agents for interpretation and advices.
        6. Create custom slash commands for reusable prompts or workflows.
        7. Run Shell commands starting with a percentage sign (%).
        8. Run Python expressions starting with an exclamation mark (!).
        9. AI can make mistakes. Always review agent's responses.""");

        project.setLeftComponent(explorer);
        project.setRightComponent(notebook);
        project.setResizeWeight(0.15);

        setLeftComponent(project);
        setRightComponent(cli);
        setResizeWeight(0.6);
    }

    /** Creates an analyst agent. */
    private Analyst analyst(Path path) {
        return new Analyst(SmileStudio::llm,
                path.resolve(".smile", "analyst"),
                path);
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
