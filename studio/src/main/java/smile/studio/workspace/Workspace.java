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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.IntConsumer;
import com.formdev.flatlaf.util.SystemFileChooser;
import ioa.agent.Agent;
import ioa.agent.Coder;
import smile.io.Paths;
import smile.shell.JShell;
import smile.studio.SmileStudio;
import smile.studio.cli.AgentCLI;
import smile.studio.notebook.Notebook;
import smile.studio.text.Notepad;
import smile.swing.FileExplorer;
import smile.swing.tree.DirectoryTreeNode;

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
            "jsh", // JShell scripts
            "sc",  // Scala scripts
            "kts", // Kotlin scripts
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
     * The opened files, notebooks and plain text alike.
     */
    private final List<OpenFile> openFiles = new ArrayList<>();
    /**
     * Index from absolute, normalized path string to the open {@link OpenFile},
     * enabling O(1) lookup in {@link #handleFileChanged} and {@link #openFile(Path)}.
     */
    private final Map<String, OpenFile> openFileIndex = new HashMap<>();
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
     * File-change watcher — single source of truth for the set of open files.
     */
    private final OpenFileWatcher fileWatcher = new OpenFileWatcher(List.of(), this::handleFileChanged);

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

        Agent chiefOfStaff = initChiefOfStaff(cwd);
        Agent dataScientist = initDataScientist(cwd);
        Agent productManager = initProductManager(cwd);
        Agent desktopOperator = initDesktopOperator(cwd);
        coders.put("Java", initJavaCoder(cwd));
        coders.put("Python", initPythonCoder(cwd));
        fileExplorer = new FileExplorer(cwd);
        kernelExplorer = new KernelExplorer(fileChooser);
        explorerTabs.addTab("Project", new JScrollPane(fileExplorer));
        explorerTabs.addTab("Kernel", new JScrollPane(kernelExplorer));

        for (var file : getOpenFilePaths()) {
            try {
                openFile(file);
            } catch (Exception ex) {
                logger.error("Failed to restore open file {}: {}", file, ex.getMessage());
            }
        }

        // Open default files (Welcome and Release Notes) if there are no previously opened files.
        if (fileWatcher.files().isEmpty()) {
            Path welcome = resolveHomeFile("Welcome");
            Path releaseNotes = resolveHomeFile("Release Notes");

            if (Files.exists(welcome)) {
                openFile(welcome);
            }
            if (Files.exists(releaseNotes)) {
                openFile(releaseNotes);
            }

            if (Files.exists(welcome)) {
                OpenFile welcomeFile = openFileIndex.get(welcome.toAbsolutePath().normalize().toString());
                if (welcomeFile != null) {
                    notebookTabs.setSelectedComponent((Component) welcomeFile);
                }
            }
        }

        Agent architect = initArchitect(cwd);
        openAgent("\uD83E\uDD1D Frank the Chief of Staff", chiefOfStaff, "frank", chiefOfStaffCLI(chiefOfStaff));
        openAgent("\uD83C\uDFAF Steve the Product Manager", productManager, "steve", productManagerCLI(productManager));
        openAgent("📊 Clair the Data Scientist", dataScientist, "clair", dataScientistCLI(dataScientist));
        openAgent("\uD83D\uDCD0 Ada the Architect", architect, "ada", architectCLI(architect));
        openAgent("☕ James the Java Guru", coders.get("Java"), "james", javaCoderCLI(coders.get("Java")));
        openAgent("\uD83D\uDC0D Guido the Pythonista", coders.get("Python"), "guido", pythonCoderCLI(coders.get("Python")));
        openAgent("\uD83D\uDDA5\uFE0F Chuck the Desktop Operator", desktopOperator, "chuck", desktopOperatorCLI(desktopOperator));

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
     * Opens a file when double-clicking it in the explorer.
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
                                openFile(path);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Initializes the chief of staff agent.
     */
    private Agent initChiefOfStaff(Path cwd) {
        try {
            Agent agent = new Agent(Agent.Spec.of("chief-of-staff"), SmileStudio::llm, cwd);
            applyDefaultModel(agent);
            return agent;
        } catch (Exception ex) {
            logger.error("Failed to initialize chief of staff agent: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Initializes the data scientist agent.
     */
    private Agent initDataScientist(Path cwd) {
        try {
            Agent agent = new Agent(Agent.Spec.of("data-scientist"), SmileStudio::llm, cwd);
            applyDefaultModel(agent);
            return agent;
        } catch (Exception ex) {
            logger.error("Failed to initialize data scientist agent: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Initializes the product manager agent.
     */
    private Agent initProductManager(Path cwd) {
        try {
            Agent agent = new Agent(Agent.Spec.of("product-manager"), SmileStudio::llm, cwd);
            applyDefaultModel(agent);
            return agent;
        } catch (Exception ex) {
            logger.error("Failed to initialize Product Manager agent: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Initializes the architect agent.
     */
    private Agent initArchitect(Path cwd) {
        try {
            Agent agent = new Agent(Agent.Spec.of("architect"), SmileStudio::llm, cwd);
            applyDefaultModel(agent);
            return agent;
        } catch (Exception ex) {
            logger.error("Failed to initialize architect agent: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Initializes the desktop operator agent.
     */
    private Agent initDesktopOperator(Path cwd) {
        try {
            Agent agent = new Agent(Agent.Spec.of("desktop-operator"), SmileStudio::llm, cwd);
            applyDefaultModel(agent);
            return agent;
        } catch (Exception ex) {
            logger.error("Failed to initialize desktop operator agent: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Initializes the Java coding agent.
     */
    private Coder initJavaCoder(Path cwd) {
        try {
            Coder coder = new Coder("java-coder", SmileStudio::llm, cwd);
            applyDefaultModel(coder);
            return coder;
        } catch (Exception ex) {
            logger.error("Failed to initialize Java coding agent: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Initializes the Python coding agent.
     */
    private Coder initPythonCoder(Path cwd) {
        try {
            Coder coder = new Coder("pythonista", SmileStudio::llm, cwd);
            applyDefaultModel(coder);
            return coder;
        } catch (Exception ex) {
            logger.error("Failed to initialize Python coding agent: {}", ex.getMessage());
        }
        return null;
    }

    private static void applyDefaultModel(Agent agent) {
        if (agent == null) {
            return;
        }
        var def = SmileStudio.llmServices().defaultModel();
        if (def != null) {
            String existing = agent.conversation().params().getProperty(ioa.llm.client.LLM.MODEL, "");
            if (existing == null || existing.isBlank()) {
                agent.conversation().params().setProperty(ioa.llm.client.LLM.MODEL, def.model().id());
            }
        }
    }

    /**
     * Refreshes model selectors on every agent CLI after settings change.
     */
    public void refreshAgentModelSelectors() {
        for (int i = 0; i < agentTabs.getTabCount(); i++) {
            Component c = agentTabs.getComponentAt(i);
            if (c instanceof AgentCLI cli) {
                cli.refreshModels();
            }
        }
    }

    /**
     * Registers a top-level agent under its call-out name and shows its tab.
     * A queued request selects that tab so the turn's progress is visible.
     */
    private void openAgent(String title, Agent agent, String name, AgentCLI cli) {
        if (agent != null) {
            agent.session().setCallName(name);
            ioa.agent.LocalAgentDirectory.shared().register(agent.session());
            agent.session().addListener(new ioa.agent.AgentListener() {
                @Override
                public void onQueued(ioa.agent.AgentRequest request) {
                    SwingUtilities.invokeLater(() -> agentTabs.setSelectedComponent(cli));
                }
            });
        }
        agentTabs.addTab(title, cli);
    }

    /**
     * Creates a chief of staff agent cli.
     */
    private AgentCLI chiefOfStaffCLI(Agent chiefOfStaff) {
        var cli = new AgentCLI(chiefOfStaff, this);

        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") +
                        bundle.getString("WelcomeSeparator") + '\n' +
                        bundle.getString("ChiefOfStaffWelcome") + "\n\n" +
                        bundle.getString("Tips"),
                MessageFormat.format(bundle.getString("WelcomeOutput"), System.getProperty("user.dir")) +
                        "\n\n" + bundle.getString("ChiefOfStaffOutput"));
        return cli;
    }

    /**
     * Creates a data scientist agent cli.
     */
    private AgentCLI dataScientistCLI(Agent dataScientist) {
        var cli = new AgentCLI(dataScientist, this);

        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") +
                        bundle.getString("WelcomeSeparator") + '\n' +
                        bundle.getString("DataScientistWelcome") + "\n\n" +
                        bundle.getString("Tips"),
                MessageFormat.format(bundle.getString("WelcomeOutput"), System.getProperty("user.dir")) +
                        "\n\n" + bundle.getString("DataScientistOutput"));
        return cli;
    }

    /**
     * Creates a product manager agent cli.
     */
    private AgentCLI productManagerCLI(Agent productManager) {
        var cli = new AgentCLI(productManager, this);

        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") +
                        bundle.getString("WelcomeSeparator") + '\n' +
                        bundle.getString("ProductManagerWelcome") + "\n\n" +
                        bundle.getString("Tips"),
                MessageFormat.format(bundle.getString("WelcomeOutput"), System.getProperty("user.dir")) +
                        "\n\n" + bundle.getString("ProductManagerOutput"));
        return cli;
    }

    /**
     * Creates the architect agent cli.
     */
    private AgentCLI architectCLI(Agent architect) {
        var cli = new AgentCLI(architect, this);
        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") +
                        bundle.getString("WelcomeSeparator") + '\n' +
                        bundle.getString("ArchitectWelcome") + "\n\n" +
                        bundle.getString("Tips"),
                MessageFormat.format(bundle.getString("WelcomeOutput"), System.getProperty("user.dir")) +
                        "\n\n" + bundle.getString("ArchitectOutput"));
        return cli;
    }

    /**
     * Creates a desktop operator agent cli.
     */
    private AgentCLI desktopOperatorCLI(Agent desktopOperator) {
        var cli = new AgentCLI(desktopOperator, this);
        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") +
                        bundle.getString("WelcomeSeparator") + '\n' +
                        bundle.getString("DesktopOperatorWelcome") + "\n\n" +
                        bundle.getString("Tips"),
                MessageFormat.format(bundle.getString("WelcomeOutput"), System.getProperty("user.dir")) +
                        "\n\n" + bundle.getString("DesktopOperatorOutput"));
        return cli;
    }

    /**
     * Creates a Java coding agent cli.
     */
    private AgentCLI javaCoderCLI(Coder coder) {
        var cli = new AgentCLI(coder, this);
        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") +
                        bundle.getString("WelcomeSeparator") + '\n' +
                        bundle.getString("JavaCoderWelcome") + "\n\n" +
                        bundle.getString("Tips"),
                MessageFormat.format(bundle.getString("WelcomeOutput"), System.getProperty("user.dir")) +
                        "\n\n" + bundle.getString("JavaCoderOutput"));
        return cli;
    }

    /**
     * Creates a Python coding agent cli.
     */
    private AgentCLI pythonCoderCLI(Coder coder) {
        var cli = new AgentCLI(coder, this);
        cli.welcome(JShell.logo.replaceAll("(?m)^\\s{3}", "") +
                        bundle.getString("WelcomeSeparator") + '\n' +
                        bundle.getString("PythonCoderWelcome") + "\n\n" +
                        bundle.getString("Tips"),
                MessageFormat.format(bundle.getString("WelcomeOutput"), System.getProperty("user.dir")) +
                        "\n\n" + bundle.getString("PythonCoderOutput"));
        return cli;
    }

    /**
     * Sets the callback for closing file tabs.
     */
    private void setNotebookTabCloseCallback() {
        notebookTabs.putClientProperty("JTabbedPane.tabClosable", true);
        notebookTabs.putClientProperty("JTabbedPane.tabCloseCallback",
                (IntConsumer) tabIndex -> {
                    if (notebookTabs.getComponentAt(tabIndex) instanceof OpenFile openFile) {
                        if (closeFile(openFile)) {
                            notebookTabs.removeTabAt(tabIndex);
                            // openFiles and fileWatcher are already updated inside closeFile().
                        }
                    }
                });
    }

    /**
     * Sets the listener for switching file tabs to refresh the kernel explorer.
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
        // fileWatcher.files() preserves insertion order via the LinkedHashSet.
        List<String> openFiles = fileWatcher.files();
        for (int i = 0; i < openFiles.size(); i++) {
            properties.setProperty("file." + (i + 1), openFiles.get(i));
        }

        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Smile Studio Properties");
            }
        } catch (IOException e) {
            logger.error("Error saving studio properties file: {}", e.getMessage());
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
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
                for (int i = 1; i <= 100; i++) {
                    String file = properties.getProperty("file." + i);
                    if (file != null) {
                        files.add(Path.of(file));
                    } else {
                        break;
                    }
                }
            } catch (IOException ex) {
                logger.error("Error reading studio properties file: {}", ex.getMessage());
            }
        }

        return files;
    }

    /**
     * Resolves a file from the SMILE home directory, with fallback to universal resources
     * for development and testing environments.
     *
     * @param name the file name (e.g., "Welcome", "Release Notes").
     * @return the resolved path, or the default smile.home path if not found.
     */
    public static Path resolveHomeFile(String name) {
        String homeProp = System.getProperty("smile.home");
        Path home = homeProp != null ? Path.of(homeProp) : Path.of(".");
        Path file = home.resolve(name);
        if (Files.exists(file)) return file;
        Path md = home.resolve(name + ".md");
        if (Files.exists(md)) return md;
        Path universal = home.resolve("studio/src/universal").resolve(name);
        if (Files.exists(universal)) return universal;
        Path universalMd = home.resolve("studio/src/universal").resolve(name + ".md");
        if (Files.exists(universalMd)) return universalMd;
        Path rootUniversal = Path.of("studio/src/universal", name);
        if (Files.exists(rootUniversal)) return rootUniversal;
        Path rootUniversalMd = Path.of("studio/src/universal", name + ".md");
        if (Files.exists(rootUniversalMd)) return rootUniversalMd;
        return file;
    }

    /**
     * Returns the opened files, notebooks and plain text alike.
     *
     * @return the opened files.
     */
    public List<OpenFile> openFiles() {
        return openFiles;
    }

    /**
     * Returns the opened notebooks.
     *
     * @return the opened notebooks.
     */
    public List<Notebook> notebooks() {
        return openFiles.stream()
                .filter(Notebook.class::isInstance)
                .map(Notebook.class::cast)
                .toList();
    }

    /**
     * Returns the selected file.
     *
     * @return the selected file.
     */
    public Optional<OpenFile> selectedFile() {
        if (notebookTabs.getSelectedComponent() instanceof OpenFile openFile) {
            return Optional.of(openFile);
        } else {
            return Optional.empty();
        }
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
     * Opens a file as a tab. Notebooks are opened as notebooks; other
     * non-binary files are opened in a plain text editor; binary files are
     * handed over to the desktop.
     *
     * @param path the file path.
     */
    public void openFile(Path path) {
        path = path.toAbsolutePath().normalize();
        var filename = path.getFileName().toString();
        // already opened — just switch to its tab
        if (fileWatcher.isOpen(path)) {
            OpenFile openFile = openFileIndex.get(path.toString());
            if (openFile == null) {
                logger.warn("Tab {} not found", filename);
                return;
            }
            int index = notebookTabs.indexOfComponent((Component) openFile);
            if (index != -1) {
                notebookTabs.setSelectedIndex(index);
            } else {
                logger.warn("Tab {} not found", filename);
            }
            return;
        }

        OpenFile openFile;
        try {
            if (Arrays.asList(SMILE_FILE_EXTENSIONS).contains(Paths.getFileExtension(path))) {
                openFile = new Notebook(path, coders, kernelExplorer::refresh);
            } else if (!Paths.isBinary(path)) {
                openFile = new Notepad(path);
            } else {
                var desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    try {
                        desktop.open(path.toFile());
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this,
                                "Failed to open: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
                return;
            }
        } catch (Exception ex) {
            logger.error("Failed to open file {}: {}", path, ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to open " + filename + ": " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        notebookTabs.addTab(filename, (Component) openFile);
        notebookTabs.setSelectedComponent((Component) openFile);
        openFiles.add(openFile);
        openFileIndex.put(path.toString(), openFile);
        fileWatcher.addFile(path);
        fileWatcher.recordModTime(path);
        fileWatcher.watchDirectory(path.getParent());
    }

    /**
     * Opens a notebook.
     *
     * @param path the notebook file path.
     */
    public void openNotebook(Path path) {
        openFile(path);
    }

    /**
     * Opens a file with file chooser.
     */
    public void openFile() {
        fileChooser.setDialogTitle(bundle.getString("OpenNotebook"));
        fileChooser.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(
                bundle.getString("SmileFile"), SMILE_FILE_EXTENSIONS));
        if (fileChooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            Path file = fileChooser.getSelectedFile().toPath();
            openFile(file);
        }
    }

    /**
     * Closes a file with prompt to save if there are unsaved changes.
     *
     * @param openFile the file to close.
     * @return true if the file is closed, false if the close operation is canceled.
     */
    public boolean closeFile(OpenFile openFile) {
        boolean confirmed = switch (confirmSave(openFile)) {
            case JOptionPane.YES_OPTION -> saveFile(openFile, false);
            case JOptionPane.NO_OPTION -> true;
            default -> false;
        };

        if (confirmed) {
            // Shuts down the execution engines and frees resources.
            openFile.close();
            openFiles.remove(openFile);
            Path absPath = openFile.getFile().toAbsolutePath().normalize();
            openFileIndex.remove(absPath.toString());
            fileWatcher.removeFile(absPath);
        }
        return confirmed;
    }

    /**
     * Prompts if the file is not saved.
     *
     * @return an integer indicating the option selected by the user.
     */
    private int confirmSave(OpenFile openFile) {
        if (openFile.isSaved()) return JOptionPane.NO_OPTION;
        return JOptionPane.showConfirmDialog(this,
                MessageFormat.format(bundle.getString("SaveMessage"), openFile.getFile().getFileName()),
                bundle.getString("SaveTitle"),
                JOptionPane.YES_NO_CANCEL_OPTION);
    }

    /**
     * Saves the file.
     *
     * @param openFile the file to save.
     * @param saveAs   save the file to a new file if true.
     * @return true if the file is saved successfully, false otherwise
     * or the save operation is canceled.
     */
    public boolean saveFile(OpenFile openFile, boolean saveAs) {
        Path oldPath = openFile.getFile() != null
                ? openFile.getFile().toAbsolutePath().normalize() : null;

        if (openFile.getFile() == null || saveAs) {
            fileChooser.setDialogTitle(bundle.getString("SaveNotebook"));
            fileChooser.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(
                    bundle.getString("SmileFile"), SMILE_FILE_EXTENSIONS));
            if (fileChooser.showSaveDialog(this) != SystemFileChooser.APPROVE_OPTION) {
                return false;
            }

            File file = fileChooser.getSelectedFile();
            // Only append a default extension when the chosen name has none.
            if (Paths.getFileExtension(file.toPath()).isEmpty()) {
                file = new File(file.getParentFile(), file.getName() + defaultExtension(oldPath));
            }

            Path path = file.toPath();
            openFile.setFile(path);
        }

        try {
            openFile.save();
            Path newPath = openFile.getFile().toAbsolutePath().normalize();

            // If the path changed (Save As), update index and watcher.
            if (!newPath.equals(oldPath)) {
                if (oldPath != null) {
                    openFileIndex.remove(oldPath.toString());
                    fileWatcher.removeFile(oldPath);
                }
                openFileIndex.put(newPath.toString(), openFile);
                fileWatcher.addFile(newPath);
                fileWatcher.watchDirectory(newPath.getParent());
            }

            // Update the known mod time so our own write is not mistaken
            // for an external change when the WatchService event arrives.
            fileWatcher.recordModTime(newPath);
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    /**
     * Returns the extension to append when a Save-As name has none, derived
     * from the current file's extension.
     *
     * @param oldPath the current file path, may be null.
     * @return the extension including the leading dot.
     */
    private static String defaultExtension(Path oldPath) {
        if (oldPath != null) {
            String extension = Paths.getFileExtension(oldPath);
            if (!extension.isEmpty()) {
                return "." + extension;
            }
        }
        return ".jsh";
    }

    /**
     * Returns the current working directory for the workspace.
     *
     * @return the current working directory.
     */
    public Path cwd() {
        return cwd;
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
     * been detected.  If the tab has no unsaved edits, the file is reloaded
     * silently; otherwise the user is asked, because reloading would discard
     * their in-memory work.
     *
     * @param path the changed file path (absolute, normalized).
     */
    private void handleFileChanged(Path path) {
        OpenFile openFile = openFileIndex.get(path.toString());
        if (openFile == null) return;

        // No in-memory edits to lose, so adopt the disk version without asking.
        if (openFile.isSaved()) {
            reloadFile(openFile, path);
            return;
        }

        String filename = path.getFileName().toString();

        int choice = JOptionPane.showConfirmDialog(
                this,
                MessageFormat.format(bundle.getString("ExternalChangeMessage"), filename),
                bundle.getString("ExternalChangeTitle"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            reloadFile(openFile, path);
        }
    }

    /**
     * Reloads the content of {@code openFile} from disk in place, preserving
     * its position in the tab strip and, for notebooks, its kernel.
     *
     * @param openFile the file to reload.
     * @param path     the file to reload from.
     */
    private void reloadFile(OpenFile openFile, Path path) {
        try {
            openFile.reload();
            // Record updated mod time so the next save isn't mistaken for external change.
            fileWatcher.recordModTime(path);
            logger.info("Reloaded file from disk: {}", path);
            SmileStudio.setStatus(this, MessageFormat.format(
                    bundle.getString("ExternalChangeReloaded"), path.getFileName()));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to reload: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Shuts down the file-change watcher.  Should be called when the
     * workspace is being disposed (e.g. application shutdown).
     */
    public void shutdown() {
        fileWatcher.shutdown();
    }
}
