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
package smile.studio;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.util.SystemInfo;
import ioa.llm.client.LLM;
import ioa.llm.mcp.MCP;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rtextarea.SearchContext;
import smile.studio.workspace.OpenFile;
import smile.studio.workspace.ServeManager;
import smile.studio.workspace.Workspace;
import smile.swing.Button;
import smile.studio.notebook.Cell;
import smile.studio.notebook.Notebook;
import smile.util.lsp.LanguageService;
import static smile.swing.SmileUtilities.scaleImageIcon;

/**
 * Smile Studio is an integrated development environment (IDE) for Smile.
 *
 * @author Haifeng Li
 */
public class SmileStudio extends JFrame implements SearchListener {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SmileStudio.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(SmileStudio.class.getName(), Locale.getDefault());
    /** Application preference and configuration. */
    private static final Preferences prefs = Preferences.userNodeForPackage(SmileStudio.class);
    /** The key for auto save preference. */
    private static final String AUTO_SAVE_KEY = "autoSave";
    /** Client pool and available models. Reloaded on the EDT when settings change. */
    private static final LlmServices llmServices = new LlmServices();
    /** Application icons in different sizes. */
    private final List<Image> icons = new ArrayList<>();
    private final JMenuBar menuBar = new JMenuBar();
    private final JToolBar toolBar = new JToolBar();
    private final StatusBar statusBar = new StatusBar();
    private final FindDialog findDialog = new FindDialog(this, this);
    private final ReplaceDialog replaceDialog = new ReplaceDialog(this, this);
    private final Workspace workspace;
    /** Kept as a field so {@code windowClosing} can stop it before shutdown. */
    private AutoSaveAction autoSaveAction;

    /**
     * Constructor.
     */
    public SmileStudio() {
        super(bundle.getString("AppName"));
        setFrameIcon();
        setJMenuBar(menuBar);
        // Tie the properties of the two dialogs together (match case, regex, etc.).
        SearchContext context = findDialog.getSearchContext();
        replaceDialog.setSearchContext(context);

        // Initialize LLM clients on the EDT so error dialogs run on the right thread.
        llmServices.reload(prefs);

        // Assign workspace before initMenuAndToolBar() so that AutoSaveAction,
        // whose timer lives on the workspace's auto saver, is never handed a null
        // reference — including from the deferred doClick() that restores the
        // persisted menu state.
        Path cwd = Path.of(System.getProperty("user.dir"));
        workspace = new Workspace(cwd);
        initMenuAndToolBar();

        JPanel contentPane = new JPanel(new BorderLayout());
        // Don't show toolbar on Windows/Linux to reduce UI clutter.
        // However, macOS red/orange/green buttons overlap Swing components
        // without toolbar, which we adjust the location.
        if (SystemInfo.isMacOS) contentPane.add(toolBar, BorderLayout.NORTH);
        contentPane.add(workspace, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);
        setContentPane(contentPane);

        // Starts the Ty server in a background thread.
        Thread.ofPlatform().name("ty-server-starter").daemon(true).start(() -> {
            try {
                var handler = new LspServerNotificationHandler("Ty", statusBar);
                var ty = LanguageService.of(cwd, "ty server");
                ty.start(handler, null);
                if (ty.isInitialized()) {
                    LanguageService.put("python", ty);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        logger.info("Shutting down Ty server...");
                        ty.close();
                    }));
                }
            } catch (Exception ex) {
                logger.error("Failed to start Ty server: {}", ex.getMessage());
            }
        });

        // Starts the JDT LS server in a background thread.
        if (Files.exists(Path.of(System.getProperty("smile.home"), "jdtls"))) {
            Thread.ofPlatform().name("jdt-server-starter").daemon(true).start(() -> {
                try {
                    var handler = new LspServerNotificationHandler("JDT", statusBar);
                    var command = (SystemInfo.isWindows ? "cmd.exe /c " : "bash -c ")
                            + System.getProperty("smile.home") + "/jdtls/bin/jdtls";
                    var jdtls = LanguageService.of(cwd, command);
                    jdtls.start(handler, getJtdInitOptions());
                    if (jdtls.isInitialized()) {
                        LanguageService.put("java", jdtls);
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            logger.info("Shutting down JDT LS server...");
                            jdtls.close();
                        }));
                    }
                } catch (Exception ex) {
                    logger.error("Failed to start JDT LS server: {}", ex.getMessage());
                }
            });
        }

        // Load enabled plugins before MCP starts: the loader stages subagents and
        // computes the effective MCP fragment paths. It never runs plugin code.
        var pluginLoader = smile.studio.plugin.PluginLoader.bootstrapShared(cwd);

        // Starts MCP services in background
        Thread.ofPlatform().name("mcp-service-starter").daemon(true).start(() -> {
            try {
                var handler = new McpServerNotificationHandler(statusBar);
                var path = Path.of(System.getProperty("smile.home"), "conf", "mcp.json");
                if (Files.exists(path)) MCP.connect(path, handler);
                path = Path.of(System.getProperty("user.home"), ".smile", "mcp.json");
                if (Files.exists(path)) MCP.connect(path, handler);
                path = Path.of(System.getProperty("user.dir"), ".smile", "mcp.json");
                if (Files.exists(path)) MCP.connect(path, handler);
                // Plugin MCP servers: each fragment already carries `disabled: true`
                // on every server the user has not opted into, so ioa skips them.
                for (Path fragment : pluginLoader.mcpFragments()) {
                    if (Files.exists(fragment)) MCP.connect(fragment, handler);
                }
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    logger.info("Shutting down MCP servers...");
                    MCP.close();
                }));
            } catch (Throwable ex) {
                logger.error("Failed to start MCP services: {}", ex.getMessage());
            }
        });

        // Optionally start the shared inference service (conf/studio.json).
        // Only start when there is actually a model to serve: a service with an
        // empty catalog has nothing to do, and the user can start it later from
        // the Inference menu or by double-clicking a model in the Kernel tree.
        var inference = StudioConfig.inferenceServer();
        if (inference.autoStart()) {
            var modelPath = Path.of(inference.modelPath());
            if (!Files.exists(modelPath)) {
                logger.info("Not auto-starting the inference service: model path '{}' does not exist",
                        modelPath);
            } else {
                Thread.ofPlatform().name("serve-autostart").daemon(true).start(() -> {
                    var manager = ServeManager.getInstance();
                    if (!manager.start(inference.host(), inference.port())) {
                        logger.error("Failed to auto-start the inference service");
                        return;
                    }
                    try {
                        manager.loadModel(inference.modelPath());
                    } catch (Exception ex) {
                        logger.error("Failed to load model '{}': {}", inference.modelPath(), ex.getMessage());
                    }
                });
            }
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Persist the open-file list *before* closing files so that
                // fileWatcher.files() still contains all paths at save time.
                // closeFile() calls fileWatcher.removeFile() for each file,
                // so saving after the loop would always write an empty list.
                workspace.saveOpenFilePaths();

                // Iterate over a snapshot — closeFile() removes from the list.
                List<OpenFile> openFiles = new ArrayList<>(workspace.openFiles());
                for (OpenFile openFile : openFiles) {
                    if (!workspace.closeFile(openFile)) {
                        // User canceled saving — abort the close operation.
                        return;
                    }
                }

                workspace.autoSaver().stop();
                workspace.shutdown();
                System.exit(0);
            }

            @Override
            public void windowOpened(WindowEvent e) {
                // JSplitPane.setDividerLocation() set the location based on
                // current pane size. We should set it after window is opened.
                workspace.setDividerLocation(0.55);
                // Invoke later so that splitPane.invalidate() be done
                SwingUtilities.invokeLater(() -> workspace.project().setDividerLocation(0.2));
            }
        });
    }

    private Map<String, Object> getJtdInitOptions() {
        // 1. Construct the absolute glob pattern for your app's lib directory
        // Using forward slashes works consistently across JDTLS target platforms
        var smileHome = System.getProperty("smile.home");
        String libGlobPattern = smileHome.replace("\\", "/") + "/lib/*.jar";
        // 2. Build the settings structure
        var referencedLibraries = List.of(libGlobPattern);
        // Disable automatic build file generation and supply local Jars
        var projectSettings = Map.of("referencedLibraries", referencedLibraries);
        // Optional settings for standalone script optimization
        Map<String, Object> formatSettings = new HashMap<>();
        formatSettings.put("enabled", true);

        var javaSettings = Map.of(
                "project", projectSettings,
                "format", formatSettings);
        var settings = Map.of("java", javaSettings);
        return Map.of("settings", settings);
    }

    /**
     * Returns the application preference and configuration.
     * @return the application preference and configuration.
     */
    public static Preferences preferences() {
        return prefs;
    }

    /**
     * Sets the status in the StatusBar of the Studio containing the component.
     * @param comp the component that sets the status message.
     * @param status the status message.
     */
    public static void setStatus(Component comp, String status) {
        Runnable task = () -> {
            if (SwingUtilities.getWindowAncestor(comp) instanceof SmileStudio studio) {
                studio.statusBar.setStatus(status);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /**
     * Returns the status bar.
     * @return the status bar.
     */
    public StatusBar statusBar() {
        return statusBar;
    }

    /**
     * Returns the shared LLM services manager (client pool and model list).
     * @return the services manager.
     */
    public static LlmServices llmServices() {
        return llmServices;
    }

    /**
     * Returns the default service's LLM client if configured.
     * @return an LLM instance, or null.
     */
    public static LLM llm() {
        return llmServices.defaultClient();
    }

    /**
     * Reloads clients and available models from preferences.
     * Must be called on the Event Dispatch Thread.
     *
     * @return the default service's client, or {@code null} if none is configured.
     */
    public static LLM updateLLM() {
        llmServices.reload(prefs);
        return llmServices.defaultClient();
    }

    /**
     * Refreshes model/effort combos on every open agent CLI after settings change.
     */
    public static void refreshModelSelectors() {
        for (Window window : Window.getWindows()) {
            if (window instanceof SmileStudio studio) {
                studio.workspace.refreshAgentModelSelectors();
            }
        }
    }

    /**
     * Reloads LLM services from preferences. Prefer {@link #updateLLM()}.
     *
     * @return the default service's client, or {@code null}.
     * @deprecated use {@link #updateLLM()}.
     */
    @Deprecated
    public static LLM createLLM() {
        return updateLLM();
    }

    /**
     * Sets the icon images for the frame.
     */
    private void setFrameIcon() {
        try (InputStream input = SmileStudio.class.getResourceAsStream("images/smile.png")) {
            if (input == null) {
                logger.error("Resource not found: images/smile.png");
                return;
            }

            BufferedImage icon = ImageIO.read(input);
            if (icon == null) {
                logger.error("Could not decode image: images/smile.png");
                return;
            }
            int[] sizes = {16, 24, 32, 48, 64, 128, 256};
            for (int size : sizes) {
                BufferedImage image = new BufferedImage(size, size, Transparency.TRANSLUCENT);
                Graphics2D g2 = image.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(icon, 0, 0, size, size, null);
                g2.dispose();
                icons.add(image);
            }
            setIconImages(icons);
        } catch (IOException e) {
            logger.error("Error loading image smile.png from resource: {}", e.getMessage());
        }
    }

    /** Initializes the menubar and the toolbar. */
    private void initMenuAndToolBar() {
        var newNotebook = new NewNotebookAction();
        var openFile = new OpenNotebookAction();
        var saveFile = new SaveNotebookAction();
        var saveAsFile = new SaveAsNotebookAction();
        autoSaveAction = new AutoSaveAction();
        var addCell = new AddCellAction();
        var runAll = new RunAllAction();
        var clearAll = new ClearAllAction();
        var restart = new RestartKernelAction();
        var stop = new StopAction();
        var settings = new SettingsAction();
        var exit = new ExitAction();

        var autoSaveMenuItem = new JCheckBoxMenuItem(autoSaveAction);
        if (prefs.getBoolean(AUTO_SAVE_KEY, false)) {
            SwingUtilities.invokeLater(autoSaveMenuItem::doClick);
        }

        JMenu fileMenu = new JMenu(bundle.getString("File"));
        fileMenu.add(new JMenuItem(newNotebook));
        fileMenu.add(new JMenuItem(openFile));
        fileMenu.add(new JMenuItem(saveFile));
        fileMenu.add(new JMenuItem(saveAsFile));
        fileMenu.add(autoSaveMenuItem);
        fileMenu.add(new JMenuItem(settings));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem(new PluginsAction()));
        fileMenu.add(new JMenuItem(exit));
        menuBar.add(fileMenu);

        JMenu cellMenu = new JMenu(bundle.getString("Cell"));
        cellMenu.add(new JMenuItem(addCell));
        cellMenu.add(new JMenuItem(runAll));
        cellMenu.add(new JMenuItem(clearAll));
        cellMenu.add(new JMenuItem(restart));
        cellMenu.add(new JMenuItem(stop));
        menuBar.add(cellMenu);

        // Find and Replace work on the selected tab, notebook or plain text,
        // so they belong in a Find menu rather than the Cell menu.
        JMenu findMenu = new JMenu(bundle.getString("FindMenu"));
        findMenu.add(new JMenuItem(new ShowFindDialogAction()));
        findMenu.add(new JMenuItem(new ShowReplaceDialogAction()));
        menuBar.add(findMenu);

        JMenu inferenceMenu = new JMenu(bundle.getString("Inference"));
        inferenceMenu.add(new JMenuItem(new StartInferenceAction()));
        inferenceMenu.add(new JMenuItem(new StopInferenceAction()));
        inferenceMenu.add(new JMenuItem(new RestartInferenceAction()));
        inferenceMenu.addSeparator();
        inferenceMenu.add(new JMenuItem(new InferenceHealthAction()));
        inferenceMenu.add(new JMenuItem(new InferenceMetricsAction()));
        inferenceMenu.add(new JMenuItem(new OpenInferenceUiAction()));
        menuBar.add(inferenceMenu);

        JMenu helpMenu = new JMenu(bundle.getString("Help"));
        helpMenu.add(new JMenuItem(new TutorialAction()));
        helpMenu.add(new JMenuItem(new JavaDocAction()));
        helpMenu.add(new JMenuItem(new AboutAction()));
        menuBar.add(helpMenu);

        // Don't allow the toolbar to be dragged and undocked
        toolBar.setFloatable(false);
        // Show a border only when the mouse hovers over a button
        toolBar.setRollover(true);
        toolBar.add(new Button(newNotebook));
        toolBar.add(new Button(openFile));
        toolBar.add(new Button(saveFile));
        toolBar.add(new Button(saveAsFile));
        toolBar.addSeparator();
        toolBar.add(new Button(addCell));
        toolBar.add(new Button(runAll));
        toolBar.add(new Button(clearAll));
        toolBar.add(new Button(restart));
        toolBar.add(new Button(stop));
    }

    @Override
    public String getSelectedText() {
        return workspace.selectedFile()
                .map(OpenFile::getSelectedText)
                .orElse(null);
    }

    @Override
    public void searchEvent(SearchEvent e) {
        var opt = workspace.selectedFile();
        if (opt.isEmpty()) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            this,
                            bundle.getString("NoActiveFile"),
                            bundle.getString("Search"),
                            JOptionPane.INFORMATION_MESSAGE
                    ));
            return;
        }

        // The selected tab owns the search: a notebook searches every cell,
        // a plain text file searches its single editor.
        opt.get().searchEvent(e);
    }

    /** Opens the plugin marketplace panel. */
    private class PluginsAction extends AbstractAction {
        public PluginsAction() {
            super(java.util.ResourceBundle.getBundle(
                    "smile.studio.plugin.Plugin", java.util.Locale.getDefault()).getString("Plugins"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var panel = new smile.studio.plugin.PluginPanel(
                    SmileStudio.this, workspace.cwd());
            panel.setVisible(true);
        }
    }

    private class NewNotebookAction extends AbstractAction {        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/notebook.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public NewNotebookAction() {
            super(bundle.getString("New"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_N, c));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            newNotebook();
        }
    }

    private class OpenNotebookAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/open.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public OpenNotebookAction() {
            super(bundle.getString("Open"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, c));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            workspace.openFile();
        }
    }

    private class SaveNotebookAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/save.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public SaveNotebookAction() {
            super(bundle.getString("Save"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, c));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            workspace.selectedFile().ifPresent(file -> workspace.saveFile(file, false));
        }
    }

    private class SaveAsNotebookAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/save-as.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public SaveAsNotebookAction() {
            super(bundle.getString("SaveAs"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            workspace.selectedFile().ifPresent(file -> workspace.saveFile(file, true));
        }
    }

    private class AutoSaveAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/refresh.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);

        public AutoSaveAction() {
            super(bundle.getString("AutoSave"));
            // Without icon, menu items won't align well on Mac.
            // However, FlatLaf won't show check mark on Windows
            // if we set the icon.
            if (SystemInfo.isMacFullWindowContentSupported) {
                putValue(SMALL_ICON, icon16);
                putValue(LARGE_ICON_KEY, icon24);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof JCheckBoxMenuItem autoSave) {
                prefs.putBoolean(AUTO_SAVE_KEY, autoSave.isSelected());
                if (autoSave.isSelected()) {
                    workspace.autoSaver().start();
                } else {
                    workspace.autoSaver().stop();
                }
            }
        }
    }

    private class AddCellAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/add-cell.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public AddCellAction() {
            super(bundle.getString("AddCell"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            Cell insertAfter = (Cell) SwingUtilities.getAncestorOfClass(Cell.class, focus);
            workspace.notebook().ifPresent(book -> book.addCell(insertAfter));
        }
    }

    private class RunAllAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/run.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public RunAllAction() {
            super(bundle.getString("RunAll"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            workspace.notebook().ifPresent(Notebook::runAllCells);
        }
    }

    private class ClearAllAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/clear.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public ClearAllAction() {
            super(bundle.getString("ClearAll"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            workspace.notebook().ifPresent(Notebook::clearAllOutputs);
        }
    }

    private class StopAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/cancel.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public StopAction() {
            super(bundle.getString("Stop"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            workspace.notebook().ifPresent(Notebook::stop);
        }
    }

    private class RestartKernelAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/refresh.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public RestartKernelAction() {
            super(bundle.getString("RestartKernel"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int option = JOptionPane.showConfirmDialog(
                    SmileStudio.this,
                    bundle.getString("RestartKernelMessage"),
                    bundle.getString("RestartKernelTitle"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (option == JOptionPane.OK_OPTION) {
                workspace.restart();
                statusBar.setStatus(bundle.getString("RestartKernelDone"));
            }
        }
    }

    /** Starts the shared inference service on a background thread. */
    private class StartInferenceAction extends AbstractAction {
        public StartInferenceAction() {
            super(bundle.getString("StartInference"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var config = StudioConfig.inferenceServer();
            Thread.ofPlatform().name("serve-start").daemon(true).start(() -> {
                boolean ok = ServeManager.getInstance().start(config.host(), config.port());
                SwingUtilities.invokeLater(() -> statusBar.setStatus(ok
                        ? java.text.MessageFormat.format(bundle.getString("InferenceStarted"),
                                ServeManager.getInstance().baseUrl())
                        : bundle.getString("InferenceStartFailed")));
            });
        }
    }

    /** Stops the shared inference service. */
    private class StopInferenceAction extends AbstractAction {
        public StopInferenceAction() {
            super(bundle.getString("StopInference"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ServeManager.getInstance().stop();
            statusBar.setStatus(bundle.getString("InferenceStopped"));
        }
    }

    /** Restarts the shared inference service. */
    private class RestartInferenceAction extends AbstractAction {
        public RestartInferenceAction() {
            super(bundle.getString("RestartInference"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var config = StudioConfig.inferenceServer();
            Thread.ofPlatform().name("serve-restart").daemon(true).start(() -> {
                var manager = ServeManager.getInstance();
                manager.stop();
                boolean ok = manager.start(config.host(), config.port());
                SwingUtilities.invokeLater(() -> statusBar.setStatus(ok
                        ? java.text.MessageFormat.format(bundle.getString("InferenceStarted"),
                                manager.baseUrl())
                        : bundle.getString("InferenceStartFailed")));
            });
        }
    }

    /** Opens the inference service health endpoint in the browser. */
    private class InferenceHealthAction extends AbstractAction {
        public InferenceHealthAction() {
            super(bundle.getString("InferenceHealth"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            openInferencePage("/q/health");
        }
    }

    /** Opens the inference service metrics endpoint in the browser. */
    private class InferenceMetricsAction extends AbstractAction {
        public InferenceMetricsAction() {
            super(bundle.getString("InferenceMetrics"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            openInferencePage("/q/metrics");
        }
    }

    /** Opens the inference service web UI in the browser. */
    private class OpenInferenceUiAction extends AbstractAction {
        public OpenInferenceUiAction() {
            super(bundle.getString("OpenInferenceUI"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            openInferencePage("");
        }
    }

    /**
     * Opens a path on the running inference service in the default browser.
     *
     * @param path the path to append to the base URL (may be empty).
     */
    private void openInferencePage(String path) {
        String base = ServeManager.getInstance().baseUrl();
        if (base == null) {
            JOptionPane.showMessageDialog(this, bundle.getString("InferenceNotRunning"),
                    bundle.getString("Inference"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(base + path));
            }
        } catch (Exception ex) {
            logger.error("Failed to open browser: {}", ex.getMessage());
        }
    }

    private class SettingsAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/settings.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public SettingsAction() {
            super(bundle.getString("Settings"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            SettingsDialog dialog = new SettingsDialog(SmileStudio.this, prefs);
            dialog.setVisible(true);
        }
    }

    private static class ExitAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/exit.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public ExitAction() {
            super(bundle.getString("Exit"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            for (Window window : Window.getWindows()) {
                if (window.isVisible() && window instanceof SmileStudio studio) {
                    // Simulates a user clicking the close button to trigger WindowListener.
                    studio.dispatchEvent(new WindowEvent(studio, WindowEvent.WINDOW_CLOSING));
                }
            }
        }
    }

    private class ShowFindDialogAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/find.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        ShowFindDialogAction() {
            super(bundle.getString("Find"), icon16);
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F, c));
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (replaceDialog.isVisible()) {
                replaceDialog.setVisible(false);
            }
            findDialog.setVisible(true);
        }
    }

    private class ShowReplaceDialogAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/replace.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        ShowReplaceDialogAction() {
            super(bundle.getString("Replace"), icon16);
            int c = getToolkit().getMenuShortcutKeyMaskEx();
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_H, c));
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (findDialog.isVisible()) {
                findDialog.setVisible(false);
            }
            replaceDialog.setVisible(true);
        }
    }

    private class TutorialAction extends AbstractAction {
        public TutorialAction() {
            super(bundle.getString("Tutorials"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            browse(SmileStudio.this, "https://www.aihalo.dev//quickstart.html",
                    bundle.getString("Tutorials"));
        }
    }

    private class JavaDocAction extends AbstractAction {
        public JavaDocAction() {
            super(bundle.getString("JavaDocs"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            browse(SmileStudio.this, "https://www.aihalo.dev//api/java/index.html",
                    bundle.getString("JavaDocs"));
        }
    }

    private class AboutAction extends AbstractAction {
        public AboutAction() {
            super(bundle.getString("About"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String version = SmileStudio.class.getPackage().getImplementationVersion();
            if (version == null) version = "DEV";
            String message = String.format("""
                    Smile Studio %s
                    Copyright (c) 2010-2026 Haifeng Li.
                    All rights reserved.
                    
                    Smile Studio is free for research and educational use.
                    For commercial use, please contact sales@aihalo.dev
                    """, version);
            JOptionPane.showMessageDialog(SmileStudio.this,
                    message,
                    bundle.getString("About"),
                    JOptionPane.INFORMATION_MESSAGE,
                    icons.size() > 4 ? new ImageIcon(icons.get(4)) : null);
        }
    }

    /**
     * Opens the given URL in the default system browser.  Falls back to a
     * plain {@link JOptionPane} message when the Desktop API is unavailable.
     *
     * @param parent  the parent component for any error dialog.
     * @param url     the URL to browse.
     * @param title   the dialog title used in the fallback message.
     */
    private static void browse(Component parent, String url, String title) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(url));
                    return;
                }
            }
        } catch (Exception ex) {
            logger.warn("Could not open browser for URL: {}", url, ex);
        }
        JOptionPane.showMessageDialog(parent,
                String.format("See %s at %s", title, url),
                title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Returns a path for a new notebook that does not collide with any
     * existing file.  If {@code Untitled.jsh} already exists the method
     * appends a numeric suffix: {@code Untitled1.jsh}, {@code Untitled2.jsh}, …
     *
     * @return a non-existing path under the current workspace directory.
     */
    private Path uniqueUntitledPath() {
        Path base = workspace.cwd().resolve("Untitled.jsh");
        if (!Files.exists(base)) return base;
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            Path candidate = workspace.cwd().resolve("Untitled" + i + ".jsh");
            if (!Files.exists(candidate)) return candidate;
        }
        // Practically unreachable
        return base;
    }

    /**
     * Creates a new notebook.
     */
    private void newNotebook() {
        workspace.openNotebook(uniqueUntitledPath());
    }

    /**
     * Creates and shows the GUI. For thread safety, this method should be
     * invoked from the event dispatch thread.
     */
    public static void createAndShowGUI() {
        // Create and set up the window.
        SmileStudio studio = new SmileStudio();
        studio.setMinimumSize(new Dimension(800, 600));
        studio.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // macOS window settings
        if (SystemInfo.isMacFullWindowContentSupported) {
            // Full window content
            studio.getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
            // Transparent title bar
            studio.getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
            // The window title is painted using the system appearance, and it overlaps
            // Swing components. Hide the window title.
            studio.getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
            // macOS red/orange/green buttons overlap Swing components (e.g. toolbar).
            // Add some space to avoid the overlapping.
            studio.toolBar.add(Box.createHorizontalStrut(70), 0);
        }

        // Set the frame at the center of screen
        studio.setLocationRelativeTo(null);
        // Display the window.
        studio.pack();
        studio.setVisible(true);
        // Maximize the frame. Must be after setVisible(true).
        studio.setExtendedState(JFrame.MAXIMIZED_BOTH);
        // Set a preferred size to maintain a consistent height of status bar.
        studio.statusBar.setPreferredSize(new Dimension(studio.getWidth(), 24));
    }

    /**
     * Starts Studio UI.
     * @param args command-line arguments.
     */
    public static void start(String[] args) {
        // macOS global settings
        // Must be set on main thread and before AWT/Swing is initialized
        if (SystemInfo.isMacOS) {
            // Disable Metal rendering pipeline to avoid CoreVideo CVDisplayLink crash
            // on display sleep/wake/reconfiguration (JDK-8357418, JBR-5145).
            // AWT falls back to the hardware-accelerated OpenGL pipeline.
            if (System.getProperty("sun.java2d.metal") == null) {
                System.setProperty("sun.java2d.metal", "false");
            }
            // To move the menu bar out of the main window to the top of the screen on macOS.
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            // Appearance of window title bars: use current macOS appearance
            System.setProperty("apple.awt.application.appearance", "system");
            // Application name used in screen menu bar (in first menu after the "Apple" menu)
            System.setProperty("apple.awt.application.name", bundle.getString("AppName"));
        }

        if (SystemInfo.isWindows) {
            // Icons may become blurry due to desktop scaling with standard JDK.
            // Set to 1.0 for no scaling if running with standard JDK.
            // However, JBR optimizes HiDPI scaling.
            //System.setProperty("sun.java2d.uiScale", "1.0");
        }

        if (SystemInfo.isLinux) {
            // enable custom window decorations
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("""
                    Cannot start Smile Studio as JVM is running in headless mode.
                    Run 'smile shell' for smile shell with Java.
                    Run 'smile scala' for smile shell with Scala.""");
            System.exit(1);
        }

        // Install font
        FlatJetBrainsMonoFont.install();
        // Application specific UI defaults
        FlatLaf.registerCustomDefaultsSource("smile.studio");
        // FlatLaf.setup() must be called in the main method, before creating
        // any Swing components or the Event Dispatch Thread (EDT).
        String theme = SmileStudio.prefs.get(SettingsDialog.UI_THEME_KEY, "Dark");
        switch (theme) {
            case "Light", "IntelliJ" -> FlatIntelliJLaf.setup();
            case "Dark", "Darcula" -> FlatDarculaLaf.setup();
            default -> {
                logger.warn("Unknown theme '{}', falling back to Light", theme);
                FlatLightLaf.setup();
            }
        }

        // Creating and showing GUI in EDT.
        SwingUtilities.invokeLater(() -> {
            if (args != null && args.length > 0) {
                logger.warn("Smile Studio doesn't take arguments: {}", String.join(" ", args));
            }
            createAndShowGUI();
        });
    }
}
