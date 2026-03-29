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

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import com.formdev.flatlaf.util.SystemFileChooser;
import ioa.agent.Analyst;
import ioa.agent.Coder;
import smile.io.Paths;
import smile.shell.JShell;
import smile.studio.Notepad;
import smile.studio.SmileStudio;
import smile.swing.FileExplorer;
import smile.swing.tree.DirectoryTreeNode;
import smile.studio.cli.AgentCLI;
import smile.studio.notebook.Notebook;

/**
 * A notebook workspace.
 *
 * @author Haifeng Li
 */
public class Workspace extends JSplitPane {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Workspace.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Workspace.class.getName(), Locale.getDefault());
    /**
     * Source code file name extensions.
     */
    private static final String[] SMILE_FILE_EXTENSIONS = {
            "java", "jsh", // Java source files and JShell snippets
            "scala", "sc", // Scala source files and Ammonite scripts
            "kt", "kts",   // Kotlin source files and scripts
            "py", "ipynb"  // Python source files and Jupyter notebooks
    };
    /**
     * Workspace FileChooser that points to its own recent directory.
     */
    private final SystemFileChooser fileChooser;
    /**
     * The project pane consists of explorer and notebook.
     */
    private final JSplitPane project = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    /**
     * The tabbed pane for file/environment explorers.
     */
    private final JTabbedPane explorerTabs = new JTabbedPane();
    /**
     * The tabbed pane for notebooks.
     */
    private final JTabbedPane notebookTabs = new JTabbedPane();
    /**
     * The tabbed pane for agent CLIs.
     */
    private final JTabbedPane agentTabs = new JTabbedPane();
    /**
     * The editor of notebook.
     */
    private final List<Notebook> notebooks = new ArrayList<>();
    /**
     * The file explorer of current working directory.
     */
    private final FileExplorer fileExplorer;
    /**
     * The explorer of runtime information.
     */
    private final KernelExplorer kernelExplorer;
    /**
     * The coding agents for each programming language.
     */
    private final Map<String, Coder> coders = new HashMap<>();
    /**
     * The current working directory for the workspace.
     */
    private final Path cwd;
    /**
     * The absolute paths of opened files.
     */
    final List<String> files = new ArrayList<>();
    /**
     * File-change watching (WatchService)
     */
    private final OpenFileWatcher fileWatcher = new OpenFileWatcher(files, this::handleFileChanged);

    /**
     * Constructor.
     *
     * @param cwd the current working directory for the workspace.
     */
    public Workspace(Path cwd) {
        super(JSplitPane.HORIZONTAL_SPLIT);
        this.cwd = cwd;
        this.fileChooser = new SystemFileChooser();
        fileChooser.setCurrentDirectory(cwd.toFile());

        Analyst analyst = initAnalyst(cwd);
        coders.put("Java", initJavaCoder(cwd));
        coders.put("Python", initPythonCoder(cwd));
        fileExplorer = new FileExplorer(cwd);
        kernelExplorer = new KernelExplorer(fileChooser);
        explorerTabs.addTab("Project", new JScrollPane(fileExplorer));
        explorerTabs.addTab("Kernel", new JScrollPane(kernelExplorer));

        for (var file : getOpenFilePaths()) {
            openNotebook(file);
        }

        // Open a default notebook if there is no previously opened file.
        if (files.isEmpty()) {
            openNotebook(Path.of("Untitled.java"));
            // Initialized as true so that we won't try to save sample code.
            // Delay 200ms so that it be called after DocumentUpdate events.
            Timer timer = new Timer(200, e -> notebooks.getFirst().setSaved(true));
            timer.setRepeats(false); // Ensures the action only runs once
            timer.start();
        }

        for (var notebook : notebooks) {
            notebookTabs.addTab(notebook.getFile().getFileName().toString(), notebook);
        }

        agentTabs.addTab("📊 Clair the Analyst", analystCLI(analyst));
        agentTabs.addTab("☕ James the Java Guru", javaCoderCLI(coders.get("Java")));
        agentTabs.addTab("\uD83D\uDC0D Guido the Pythonista", pythonCoderCLI(coders.get("Python")));

        project.setLeftComponent(explorerTabs);
        project.setRightComponent(notebookTabs);
        project.setResizeWeight(0.2);

        setFileExplorerMouseListener();
        setNotebookTabsListener();
        setNotebookTabCloseCallback();
        setLeftComponent(project);
        setRightComponent(agentTabs);
        setResizeWeight(0.55);
    }

    /**
     * Opens a notebook when double-clicking a supported file in the explorer.
     */
    private void setFileExplorerMouseListener() {
        fileExplorer.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                // Check if the event is a double click
                if (e.getClickCount() == 2) {
                    // Determine which row/path was clicked at the coordinates
                    int selRow = fileExplorer.getRowForLocation(e.getX(), e.getY());
                    TreePath selPath = fileExplorer.getPathForLocation(e.getX(), e.getY());

                    if (selRow != -1 && selPath != null) {
                        if (selPath.getLastPathComponent() instanceof DirectoryTreeNode node) {
                            Path path = node.path();
                            if (Files.isRegularFile(path)) {
                                String fileExtension = Paths.getFileExtension(path);
                                if (Arrays.asList(SMILE_FILE_EXTENSIONS).contains(fileExtension)) {
                                    openNotebook(path);
                                } else if (!Paths.isBinary(path)) {
                                    Notepad.open(path);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Initializes the analyst agent.
     */
    private Analyst initAnalyst(Path cwd) {
        try {
            return new Analyst("data-analyst", SmileStudio.llm(), cwd);
        } catch (Exception ex) {
            logger.error("Failed to initialize data analyst agent", ex);
        }
        return null;
    }

    /**
     * Initializes the Java coding agent.
     */
    private Coder initJavaCoder(Path cwd) {
        try {
            return new Coder("java-coder", SmileStudio.llm(), cwd);
        } catch (Exception ex) {
            logger.error("Failed to initialize Java coding agent", ex);
        }
        return null;
    }

    /**
     * Initializes the Python coding agent.
     */
    private Coder initPythonCoder(Path cwd) {
        try {
            return new Coder("pythonista", SmileStudio.llm(), cwd);
        } catch (Exception ex) {
            logger.error("Failed to initialize Python coding agent", ex);
        }
        return null;
    }

    /**
     * Creates an analyst agent cli.
     */
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
                        3. Ctrl + Click on output links to open browser.
                        4. Run /init to create a SMILE.md file with instructions for agents.
                        5. Be as specific as you would with another data scientist for the best result.
                        6. Data visualization can be feed to AI agents for interpretation and advices.
                        7. Create custom slash commands for reusable prompts or workflows.
                        8. Run Shell commands starting with an exclamation mark (!).
                        9. AI can make mistakes. Always review agent's responses.""");

        return cli;
    }

    /**
     * Creates a Java coding agent cli.
     */
    private AgentCLI javaCoderCLI(Coder coder) {
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
                        3. Ctrl + Click on output links to open browser.
                        4. TAB to complete code in the notebook.
                        5. Be as specific as you would with another programmer for the best result.
                        6. Create custom slash commands for reusable prompts or workflows.
                        7. Run Shell commands starting with an exclamation mark (!).
                        8. AI can make mistakes. Always review agent's responses.""");

        return cli;
    }

    /**
     * Creates a Python coding agent cli.
     */
    private AgentCLI pythonCoderCLI(Coder coder) {
        var cli = new AgentCLI(coder);
        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") + """
                        =====================================================================
                        Welcome! I am Guido, your AI assistant for Python programming.
                        
                        I can help with code completion and generation in the notebook too.
                        cwd:\s""" + System.getProperty("user.dir"),

                """
                        💡 Tips for getting started:
                        1. Ctrl + ENTER to execute your intents.
                        2. Ctrl + SPACE to show slash command argument hint.
                        3. Ctrl + Click on output links to open browser.
                        4. TAB to complete code in the notebook.
                        5. Be as specific as you would with another programmer for the best result.
                        6. Create custom slash commands for reusable prompts or workflows.
                        7. Run Shell commands starting with an exclamation mark (!).
                        8. AI can make mistakes. Always review agent's responses.""");

        return cli;
    }

    /**
     * Sets the callback for closing notebook tabs.
     */
    private void setNotebookTabCloseCallback() {
        notebookTabs.putClientProperty("JTabbedPane.tabClosable", true);
        notebookTabs.putClientProperty("JTabbedPane.tabCloseCallback",
                (IntConsumer) tabIndex -> {
                    Notebook notebook = (Notebook) notebookTabs.getComponentAt(tabIndex);
                    if (closeNotebook(notebook)) {
                        notebookTabs.removeTabAt(tabIndex);
                        files.remove(notebook.getFile().toString());
                    }
                });
    }

    /**
     * Sets the listener for switching notebook tabs to refresh the kernel explorer.
     */
    private void setNotebookTabsListener() {
        notebookTabs.addChangeListener(e -> {
            int tabIndex = notebookTabs.getSelectedIndex();
            if (notebookTabs.getSelectedComponent() instanceof Notebook notebook) {
                kernelExplorer.refresh(notebook.kernel());
            }
        });
    }

    /**
     * Saves the list of opened file paths to a local properties file.
     */
    public void saveOpenFilePaths() {
        Path path = cwd.resolve(".smile", "studio.properties");
        Properties properties = new Properties();

        // Store each file path with a unique key (e.g., file.1, file.2, ...)
        for (int i = 0; i < files.size(); i++) {
            // Using a simple numeric key allows for a list-like structure
            properties.setProperty("file." + (i + 1), files.get(i));
        }

        try (OutputStream output = new FileOutputStream(path.toFile())) {
            properties.store(output, "Smile Studio Properties");
        } catch (IOException e) {
            logger.error("Error saving studio properties file: ", e);
        }
    }

    /**
     * Gets the list of opened file paths in previous session from a local properties file.
     */
    public List<Path> getOpenFilePaths() {
        Path path = cwd.resolve(".smile", "studio.properties");
        List<Path> files = new ArrayList<>();
        if (Files.exists(path)) {
            Properties properties = new Properties();
            try (FileInputStream input = new FileInputStream(path.toFile())) {
                properties.load(input);
                for (int i = 1; i <= 100; i++) {
                    String file = properties.getProperty("file." + i);
                    if (file != null) {
                        files.add(Path.of(file));
                    } else {
                        break; // stop if no more file entries
                    }
                }
            } catch (IOException ex) {
                logger.error("Error reading studio properties file: ", ex);
            }
        }

        return files;
    }

    /**
     * Returns the opened notebooks.
     *
     * @return the opened notebooks.
     */
    public List<Notebook> notebooks() {
        return notebooks;
    }

    /**
     * Returns the selected notebook.
     *
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
     *
     * @param path the notebook file path.
     */
    public void openNotebook(Path path) {
        path = path.toAbsolutePath().normalize();
        var filename = path.getFileName().toString();
        // already opened
        if (files.contains(path.toString())) {
            int index = notebookTabs.indexOfTab(filename);
            if (index != -1) {
                notebookTabs.setSelectedIndex(index);
            } else {
                logger.warn("Tab {} not found", filename);
            }
        } else {
            Notebook notebook = new Notebook(path, coders, kernelExplorer::refresh);
            notebookTabs.addTab(filename, notebook);
            notebookTabs.setSelectedComponent(notebook);
            notebooks.add(notebook);
            files.add(path.toString());
            fileWatcher.recordModTime(path);
            fileWatcher.watchDirectory(path.getParent());
        }
    }

    /**
     * Opens a notebook with file chooser.
     */
    public void openNotebook() {
        fileChooser.setDialogTitle(bundle.getString("OpenNotebook"));
        fileChooser.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(
                bundle.getString("SmileFile"), SMILE_FILE_EXTENSIONS));
        if (fileChooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            Path file = fileChooser.getSelectedFile().toPath();
            openNotebook(file);
        }
    }

    /**
     * Closes a notebook with prompt to save if there are unsaved changes.
     *
     * @param notebook the notebook to close.
     * @return true if the notebook is closed, false if the close operation is canceled.
     */
    public boolean closeNotebook(Notebook notebook) {
        boolean confirmed = switch (confirmSaveNotebook(notebook)) {
            case JOptionPane.YES_OPTION -> saveNotebook(notebook, false);
            case JOptionPane.NO_OPTION -> true;
            default -> false;
        };

        if (confirmed) {
            // Shuts down the execution engines and frees resources.
            notebook.close();
            notebooks.remove(notebook);
            files.remove(notebook.getFile().toString());
        }
        return confirmed;
    }

    /**
     * Prompts if the notebook is not saved.
     *
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
     *
     * @param notebook the notebook to save.
     * @param saveAs   save the notebook to a new file if true.
     * @return true if the notebook is saved successfully, false otherwise
     * or the save operation is canceled.
     */
    public boolean saveNotebook(Notebook notebook, boolean saveAs) {
        if (notebook.getFile() == null || saveAs) {
            fileChooser.setDialogTitle(bundle.getString("SaveNotebook"));
            fileChooser.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(
                    bundle.getString("SmileFile"), SMILE_FILE_EXTENSIONS));
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
            // Update the known mod time so our own write is not mistaken
            // for an external change when the WatchService event arrives.
            fileWatcher.recordModTime(notebook.getFile().toAbsolutePath().normalize());
            fileWatcher.watchDirectory(notebook.getFile().toAbsolutePath().normalize().getParent());
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
     *
     * @return the explorer component.
     */
    public KernelExplorer explorer() {
        return kernelExplorer;
    }

    /**
     * Returns the project split pane of explorer and notebook.
     *
     * @return the project split pane of explorer and notebook.
     */
    public JSplitPane project() {
        return project;
    }

    /**
     * Restarts the execution environment and refreshes dependent views.
     */
    public void restart() {
        notebook().ifPresent(notebook -> {
            notebook.restart();
            kernelExplorer.refresh(notebook.kernel());
        });
    }

    /**
     * Called on the Swing EDT when an external change to {@code path} has
     * been detected.  Presents a confirm dialog and reloads the notebook if
     * the user agrees.
     *
     * @param path the changed file path (absolute, normalized).
     */
    private void handleFileChanged(Path path) {
        // Find the open notebook for this path
        Notebook target = null;
        for (Notebook nb : notebooks) {
            if (nb.getFile().toAbsolutePath().normalize().equals(path)) {
                target = nb;
                break;
            }
        }
        if (target == null) return;

        final Notebook notebook = target;
        String filename = path.getFileName().toString();

        int choice = JOptionPane.showConfirmDialog(
                this,
                String.format(bundle.getString("ExternalChangeMessage"), filename),
                bundle.getString("ExternalChangeTitle"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            reloadNotebook(notebook, path);
        }
    }

    /**
     * Reloads the content of {@code notebook} from disk, preserving its
     * position in the tab strip.
     *
     * @param notebook the notebook to reload.
     * @param path     the file to reload from.
     */
    private void reloadNotebook(Notebook notebook, Path path) {
        int tabIndex = notebookTabs.indexOfComponent(notebook);
        if (tabIndex < 0) return;

        // Close the old notebook silently (skip unsaved-changes check
        // as the user just confirmed they want the disk version).
        notebook.close();
        notebooks.remove(notebook);
        files.remove(path.toString());

        // Open fresh copy
        Notebook fresh = new Notebook(path, coders, kernelExplorer::refresh);
        notebookTabs.setComponentAt(tabIndex, fresh);
        notebookTabs.setSelectedIndex(tabIndex);
        notebooks.add(fresh);
        files.add(path.toString());
        fileWatcher.recordModTime(path);

        logger.info("Reloaded notebook from disk: {}", path);
    }

    /**
     * Shuts down the file-change watcher.  Should be called when the
     * workspace is being disposed (e.g. application shutdown).
     */
    public void shutdown() {
        fileWatcher.shutdown();
    }
}
