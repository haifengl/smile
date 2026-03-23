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
import java.nio.file.Paths;
import com.formdev.flatlaf.util.SystemFileChooser;
import ioa.agent.Analyst;
import ioa.agent.Coder;
import smile.shell.JShell;
import smile.studio.SmileStudio;
import smile.studio.kernel.JavaKernel;
import smile.swing.FileExplorer;

/**
 * A notebook workspace.
 *
 * @author Haifeng Li
 */
public class Workspace extends JSplitPane {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Workspace.class);
    /** The project pane consists of explorer and notebook. */
    final JSplitPane project = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    /** The tabbed pane for file/environment explorers. */
    final JTabbedPane explorerTabs = new JTabbedPane();
    /** The tabbed pane for notebooks. */
    final JTabbedPane notebookTabs = new JTabbedPane();
    /** The tabbed pane for agent CLIs. */
    final JTabbedPane agentTabs = new JTabbedPane();
    /** Java execution engine. */
    final JavaKernel kernel = new JavaKernel();
    /** The file explorer of current working directory. */
    final FileExplorer fileExplorer = new FileExplorer(Paths.get("").toAbsolutePath());
    /** The explorer of runtime information. */
    final KernelExplorer kernelExplorer;
    /** The editor of notebook. */
    final Notebook notebook;

    /**
     * Constructor.
     * @param file the notebook file. If null, a new notebook will be created.
     * @param fileChooser the file chooser for saving models.
     */
    public Workspace(Path file, SystemFileChooser fileChooser) {
        super(JSplitPane.HORIZONTAL_SPLIT);

        Analyst analyst = null;
        Coder coder = null;
        try {
            var dir = file.getParent(); // working directory
            analyst = new Analyst("data-analyst", SmileStudio.llm(), dir);
            coder = new Coder("java-coder", SmileStudio.llm(), dir);
        } catch (Exception ex) {
            logger.error("Failed to initialize agents", ex);
        }

        kernelExplorer = new KernelExplorer(kernel, fileChooser);
        explorerTabs.addTab("Project", new JScrollPane(fileExplorer));
        explorerTabs.addTab("Kernel", new JScrollPane(kernelExplorer));

        notebook = new Notebook(file, coder, kernel, kernelExplorer::refresh);
        agentTabs.addTab("📊 Clair the Analyst", analystCLI(analyst));
        agentTabs.addTab("☕ James the Java Guru", coderCLI(coder));

        project.setLeftComponent(explorerTabs);
        project.setRightComponent(notebook);
        project.setResizeWeight(0.15);

        setLeftComponent(project);
        setRightComponent(agentTabs);
        setResizeWeight(0.5);
    }

    /** Creates an analyst agent cli. */
    private AgentCLI analystCLI(Analyst analyst) {
        var cli = new AgentCLI(analyst);

        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") + """
        =====================================================================
        Welcome! I am Clair, your AI assistant for machine learning modeling.
        
        /help for available commands, /init for initializing your project
        cwd:\s""" + System.getProperty("user.dir"),

        """
        As a state-of-the-art machine learning engineering agent,
        I can help you with:
        
        🤖 Automatic end-to-end ML/AI solutions based on your requirements.
        🔍 Best practices and state-of-the-art methods with web search.
        🏅 Targeted code block refinement by ablation study.
        🤝 Improved solution using iterative ensemble strategy.
        📊 Advanced interactive data visualization.
        📂 Process data from CSV, ARFF, JSON, Avro, Parquet, Iceberg, to SQL.
        🌐 Built-in inference server.
        
        💡 Tips for getting started:
        1. Ctrl + ENTER to execute your intents.
        2. Ctrl + SPACE to show slash command argument hint.
        3. Run /init to create a SMILE.md file with instructions for agents.
        4. Be as specific as you would with another data scientist for the best result.
        5. Data visualization can be feed to AI agents for interpretation and advices.
        6. Create custom slash commands for reusable prompts or workflows.
        7. Run Shell commands starting with a percentage sign (%).
        8. Run Python expressions starting with an exclamation mark (!).
        9. AI can make mistakes. Always review agent's responses.""");

        return cli;
    }

    /** Creates a coding agent cli. */
    private AgentCLI coderCLI(Coder coder) {
        var cli = new AgentCLI(coder);
        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") + """
        =====================================================================
        Welcome! I am James, your AI assistant for Java programming.
        
        I can help with code completion and generation in the notebook too.
        cwd:\s""" + System.getProperty("user.dir"),

        """
        💡 Tips for getting started:
        1. Ctrl + ENTER to execute your intents.
        2. Ctrl + SPACE to show slash command argument hint.
        3. TAB to complete code in the notebook.
        3. Be as specific as you would with another programmer for the best result.
        4. Create custom slash commands for reusable prompts or workflows.
        5. Run Shell commands starting with a percentage sign (%).
        6. Run Python expressions starting with an exclamation mark (!).
        7. AI can make mistakes. Always review agent's responses.""");

        return cli;
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
    public KernelExplorer explorer() {
        return kernelExplorer;
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
    public JavaKernel kernel() {
        return kernel;
    }

    /**
     * Restarts the execution environment and refreshes dependent views.
     */
    public void restart() {
        notebook.restart();
        kernelExplorer.refresh();
    }

    /**
     * Shuts down the execution engine and frees resources.
     */
    public void close() {
        kernel.close();
    }
}
