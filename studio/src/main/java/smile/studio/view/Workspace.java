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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntConsumer;
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
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Workspace.class.getName(), Locale.getDefault());
    /** Source code file name extensions. */
    private static final String[] JAVA_FILE_EXTENSIONS = {"java", "jsh"};
    /** Workspace FileChooser that points to its own recent directory. */
    private final SystemFileChooser fileChooser;
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
    /** The opened files. */
    final Set<Path> files = new HashSet<>();
    /** The editor of notebook. */
    final List<Notebook> notebooks = new ArrayList<>();
    /** The file explorer of current working directory. */
    final FileExplorer fileExplorer;
    /** The explorer of runtime information. */
    final KernelExplorer kernelExplorer;
    /** The coding agent. */
    final Coder coder;

    /**
     * Constructor.
     * @param cwd the current working directory for the workspace.
     */
    public Workspace(Path cwd) {
        super(JSplitPane.HORIZONTAL_SPLIT);
        this.fileChooser = new SystemFileChooser();
        fileChooser.setCurrentDirectory(cwd.toFile());

        Analyst analyst = initAnalyst(cwd);
        coder = initCoder(cwd);
        fileExplorer = new FileExplorer(cwd);
        kernelExplorer = new KernelExplorer(kernel, fileChooser);
        explorerTabs.addTab("Project", new JScrollPane(fileExplorer));
        explorerTabs.addTab("Kernel", new JScrollPane(kernelExplorer));

        openNotebook(Path.of("Untitled.java"));
        setTabCloseCallback();
        for (var notebook : notebooks) {
            notebookTabs.addTab(notebook.getFile().getFileName().toString(), notebook);
        }

        agentTabs.addTab("📊 Clair the Analyst", analystCLI(analyst));
        agentTabs.addTab("☕ James the Java Guru", coderCLI(coder));

        project.setLeftComponent(explorerTabs);
        project.setRightComponent(notebookTabs);
        project.setResizeWeight(0.15);

        setLeftComponent(project);
        setRightComponent(agentTabs);
        setResizeWeight(0.5);
    }

    /** Initializes the analyst agent. */
    private Analyst initAnalyst(Path cwd) {
        try {
            return new Analyst("data-analyst", SmileStudio.llm(), cwd);
        } catch (Exception ex) {
            logger.error("Failed to initialize data analyst agent", ex);
        }
        return null;
    }

    /** Initializes the coding agent. */
    private Coder initCoder(Path cwd) {
        try {
            return new Coder("java-coder", SmileStudio.llm(), cwd);
        } catch (Exception ex) {
            logger.error("Failed to initialize Java coding agent", ex);
        }
        return null;
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

    /** Sets the callback for closing notebook tabs. */
    private void setTabCloseCallback() {
        notebookTabs.putClientProperty("JTabbedPane.tabClosable", true);
        notebookTabs.putClientProperty("JTabbedPane.tabCloseCallback",
                (IntConsumer) tabIndex -> {
                    Notebook notebook = (Notebook) notebookTabs.getComponentAt(tabIndex);
                    if (closeNotebook(notebook)) {
                        notebookTabs.removeTabAt(tabIndex);
                    }
                });
    }

    /**
     * Returns the opened notebooks.
     * @return the opened notebooks.
     */
    public List<Notebook> notebooks() {
        return notebooks;
    }

    /**
     * Returns the selected notebook.
     * @return the selected notebook.
     */
    public Optional<Notebook> notebook() {
        if (notebookTabs.getSelectedComponent() instanceof Notebook notebook) {
            return Optional.of(notebook);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Opens a notebook.
     * @param path the notebook file path.
     */
    public void openNotebook(Path path) {
        Notebook notebook = new Notebook(path, coder, kernel, kernelExplorer::refresh);
        notebookTabs.addTab(notebook.getFile().getFileName().toString(), notebook);
        notebookTabs.setSelectedComponent(notebook);
        notebooks.add(notebook);
        files.add(path);
    }

    /**
     * Opens a notebook with file chooser.
     */
    public void openNotebook() {
        fileChooser.setDialogTitle(bundle.getString("OpenNotebook"));
        fileChooser.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(bundle.getString("SmileFile"), JAVA_FILE_EXTENSIONS));
        if (fileChooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            Path file = fileChooser.getSelectedFile().toPath();
            openNotebook(file);
        }
    }

    /**
     * Closes a notebook with prompt to save if there are unsaved changes.
     * @param notebook the notebook to close.
     * @return true if the notebook is closed, false if the close operation is cancelled.
     */
    public boolean closeNotebook(Notebook notebook) {
        boolean closed = switch (confirmSaveNotebook(notebook)) {
            case JOptionPane.YES_OPTION -> saveNotebook(notebook, false);
            case JOptionPane.NO_OPTION -> true;
            default -> false;
        };

        if (closed) {
            notebooks.remove(notebook);
            files.remove(notebook.getFile());
        }
        return closed;
    }

    /**
     * Prompts if the notebook is not saved.
     * @return an integer indicating the option selected by the user.
     */
    private int confirmSaveNotebook(Notebook notebook) {
        if (notebook.isSaved()) return JOptionPane.NO_OPTION;
        return JOptionPane.showConfirmDialog(this,
                String.format(bundle.getString("SaveMessage"), notebook.getFile().getFileName()),
                bundle.getString("SaveTitle"),
                JOptionPane.YES_NO_CANCEL_OPTION);
    }

    /**
     * Saves the notebook.
     * @param notebook the notebook to save.
     * @param saveAs save the notebook to a new file if true.
     * @return true if the notebook is saved successfully, false otherwise
     *              or the save operation is cancelled.
     */
    public boolean saveNotebook(Notebook notebook, boolean saveAs) {
        if (notebook.getFile() == null || saveAs) {
            fileChooser.setDialogTitle(bundle.getString("SaveNotebook"));
            fileChooser.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(bundle.getString("SmileFile"), JAVA_FILE_EXTENSIONS));
            if (fileChooser.showSaveDialog(this) != SystemFileChooser.APPROVE_OPTION) {
                return false;
            }

            File file = fileChooser.getSelectedFile();
            String name = file.getName().toLowerCase();
            if (!(name.endsWith(".java") || name.endsWith(".jsh"))) {
                file = new File(file.getParentFile(), file.getName() + ".java");
            }

            Path path = file.toPath();
            notebook.setFile(path);
        }

        try {
            notebook.save();
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
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
        notebook().ifPresent(book -> {
            book.restart();
            kernelExplorer.refresh();
        });
    }

    /**
     * Shuts down the execution engine and frees resources.
     */
    public void close() {
        kernel.close();
    }
}
