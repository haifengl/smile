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
package smile.studio;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import ioa.llm.client.*;
import ioa.llm.mcp.MCP;
import smile.studio.workspace.Workspace;
import smile.swing.Button;
import smile.studio.notebook.Cell;
import smile.studio.notebook.Notebook;
import smile.util.OS;
import smile.util.lsp.LanguageService;
import static smile.swing.SmileUtilities.scaleImageIcon;

/**
 * Smile Studio is an integrated development environment (IDE) for Smile.
 *
 * @author Haifeng Li
 */
public class SmileStudio extends JFrame {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SmileStudio.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(SmileStudio.class.getName(), Locale.getDefault());
    /** Application preference and configuration. */
    private static final Preferences prefs = Preferences.userNodeForPackage(SmileStudio.class);
    /** The key for auto save preference. */
    private static final String AUTO_SAVE_KEY = "autoSave";
    /** The LLM model. Declared volatile so that updates made on the EDT are immediately visible to other threads. */
    private static volatile LLM llm;
    /** Application icons in different sizes. */
    private final List<Image> icons = new ArrayList<>();
    private final JMenuBar menuBar = new JMenuBar();
    private final JToolBar toolBar = new JToolBar();
    private final StatusBar statusBar = new StatusBar();
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

        // Initialize the LLM on the EDT (after Swing is set up) so that any
        // error dialog shown by createLLM() runs on the correct thread.
        llm = createLLM();

        Path cwd = Path.of(System.getProperty("user.dir"));

        // Assign workspace before initMenuAndToolBar() so that AutoSaveAction's
        // timer callback (which captures workspace) is never handed a null reference.
        workspace = new Workspace(cwd);
        initMenuAndToolBar();

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add(toolBar, BorderLayout.NORTH);
        contentPane.add(workspace, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);
        setContentPane(contentPane);

        // Starts the Ty server in a background thread.
        Thread.ofPlatform().name("ty-server-starter").daemon(true).start(() -> {
            try {
                var handler = new LspServerNotificationHandler("Ty", statusBar);
                var ty = LanguageService.of(cwd, "ty server");
                ty.start(handler);
                if (ty.isInitialized()) {
                    LanguageService.put("python", ty);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        logger.info("Shutting down Ty server...");
                        ty.close();
                    }));
                }
            } catch (Exception ex) {
                logger.error("Failed to start Ty server", ex);
            }
        });

        // Starts the JDT LS server in a background thread.
        Thread.ofPlatform().name("jdt-server-starter").daemon(true).start(() -> {
            try {
                var handler = new LspServerNotificationHandler("JDT", statusBar);
                var command = (OS.isWindows() ? "cmd.exe /c " : "bash -c ")
                        + System.getProperty("smile.home") + "/jdtls/bin/jdtls";
                var jdtls = LanguageService.of(cwd, command);
                jdtls.start(handler);
                if (jdtls.isInitialized()) {
                    LanguageService.put("java", jdtls);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        logger.info("Shutting down JDT LS server...");
                        jdtls.close();
                    }));
                }
            } catch (Exception ex) {
                logger.error("Failed to start JDT LS server", ex);
            }
        });

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
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    logger.info("Shutting down MCP servers...");
                    MCP.close();
                }));
            } catch (Throwable ex) {
                logger.error("Failed to start MCP services", ex);
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Iterate over a snapshot — closeNotebook() removes from the list.
                List<Notebook> notebooks = new ArrayList<>(workspace.notebooks());
                for (Notebook notebook : notebooks) {
                    if (!workspace.closeNotebook(notebook)) {
                        // User canceled saving — abort the close operation.
                        return;
                    }
                }

                // All notebooks confirmed closed: persist the (now-empty) open-file
                // list, stop the auto-save timer, and shut down the workspace.
                workspace.saveOpenFilePaths();
                if (autoSaveAction != null) autoSaveAction.timer.stop();
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
        if (SwingUtilities.getWindowAncestor(comp) instanceof SmileStudio studio) {
            studio.statusBar.setStatus(status);
        }
    }

    /**
     * Returns an LLM instance if initialized successfully.
     * @return an LLM instance if initialized successfully.
     */
    public static LLM llm() {
        return llm;
    }

    /**
     * Re-creates the LLM from the current preferences and stores it in the
     * shared {@code llm} field.  Must be called on the Event Dispatch Thread.
     *
     * @return the newly created {@link LLM}, or {@code null} if none is
     *         configured or initialization fails.
     */
    public static LLM updateLLM() {
        llm = createLLM();
        return llm;
    }

    /**
     * Creates an LLM instance specified by app settings.
     *
     * <p>Must be called on the Event Dispatch Thread so that any error dialog
     * is shown on the correct thread.
     *
     * @return a new {@link LLM} instance, or {@code null} if none is configured
     *         or initialization fails.
     */
    public static LLM createLLM() {
        var service = prefs.get("aiService", "");
        if (service.isBlank()) {
            return null;
        }

        // Propagate stored API keys / base URLs to system properties so that
        // LLM clients that call fromEnv() can pick them up automatically.
        setSystemPropertyFromPrefs("openai.apiKey", "openaiApiKey");
        setSystemPropertyFromPrefs("openai.baseUrl", "openaiBaseUrl");
        setSystemPropertyFromPrefs("anthropic.apiKey", "anthropicApiKey");
        setSystemPropertyFromPrefs("anthropic.baseUrl", "anthropicBaseUrl");

        try {
            return switch (service) {
                case "OpenAI" -> {
                    var openai = new OpenAI(prefs.get("openaiModel", "gpt-5.3-codex"));
                    var apiKey = prefs.get("openaiApiKey", "");
                    if (!apiKey.isBlank()) {
                        openai.withApiKey(apiKey);
                    }
                    var baseUrl = prefs.get("openaiBaseUrl", "");
                    if (!baseUrl.isBlank()) {
                        openai.withBaseUrl(baseUrl);
                    }
                    yield openai;
                }

                case "Azure OpenAI" -> OpenAI.azure(
                        prefs.get("azureOpenAIApiKey", ""),
                        prefs.get("azureOpenAIBaseUrl", ""),
                        prefs.get("azureOpenAIModel", "gpt-5.3-codex"));

                case "Anthropic" -> {
                    var anthropic = new Anthropic(prefs.get("anthropicModel", "claude-sonnet-4-6"));
                    var apiKey = prefs.get("anthropicApiKey", "");
                    if (!apiKey.isBlank()) {
                        anthropic.withApiKey(apiKey);
                    }
                    var baseUrl = prefs.get("anthropicBaseUrl", "");
                    if (!baseUrl.isBlank()) {
                        anthropic.withBaseUrl(baseUrl);
                    }
                    yield anthropic;
                }

                case "Google Gemini" ->
                    new GoogleGemini(
                            prefs.get("googleGeminiApiKey", ""),
                            prefs.get("googleGeminiModel", "gemini-3-flash-preview"));

                case "Google VertexAI" ->
                    GoogleGemini.vertex(
                            prefs.get("googleVertexAIApiKey", ""),
                            prefs.get("googleVertexAIBaseUrl", ""),
                            prefs.get("googleVertexAIModel", "gemini-3.1-pro-preview"));

                default -> {
                    // Many AI services are compatible with OpenAI ChatCompletions API,
                    // so we try to initialize OpenAI client.
                    var baseUrl = prefs.get("chatCompletionsBaseUrl", "");
                    if (baseUrl.isBlank()) {
                        throw new RuntimeException("missing base URL");
                    }
                    var apiKey = prefs.get("chatCompletionsApiKey", "");
                    if (apiKey.isBlank()) {
                        throw new RuntimeException("missing API Key");
                    }
                    yield new ChatCompletions(
                            baseUrl,
                            apiKey,
                            prefs.get("chatCompletionsModel", service));
                }
            };
        } catch (Throwable t) {
            // It is often a rethrow exception
            var cause = t.getCause() != null ? t.getCause() : t;
            JOptionPane.showMessageDialog(
                    null,
                    "Failed to initialize AI service: " + cause.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }

    /**
     * Sets the icon images for the frame.
     */
    private void setFrameIcon() {
        try (InputStream input = SmileStudio.class.getResourceAsStream("images/robot.png")) {
            if (input == null) {
                logger.error("Resource not found: images/robot.png");
                return;
            }

            BufferedImage icon = ImageIO.read(input);
            if (icon == null) {
                logger.error("Could not decode image: images/robot.png");
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
            logger.error("Error loading image from resource: images/robot.png", e);
        }
    }

    /** Initializes the menubar and the toolbar. */
    private void initMenuAndToolBar() {
        var newNotebook = new NewNotebookAction();
        var openNotebook = new OpenNotebookAction();
        var saveNotebook = new SaveNotebookAction();
        var saveAsNotebook = new SaveAsNotebookAction();
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
        fileMenu.add(new JMenuItem(openNotebook));
        fileMenu.add(new JMenuItem(saveNotebook));
        fileMenu.add(new JMenuItem(saveAsNotebook));
        fileMenu.add(autoSaveMenuItem);
        fileMenu.add(new JMenuItem(settings));
        fileMenu.add(new JMenuItem(exit));
        menuBar.add(fileMenu);

        JMenu cellMenu = new JMenu(bundle.getString("Cell"));
        cellMenu.add(new JMenuItem(addCell));
        cellMenu.add(new JMenuItem(runAll));
        cellMenu.add(new JMenuItem(clearAll));
        cellMenu.add(new JMenuItem(restart));
        cellMenu.add(new JMenuItem(stop));
        menuBar.add(cellMenu);

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
        toolBar.add(new Button(openNotebook));
        toolBar.add(new Button(saveNotebook));
        toolBar.add(new Button(saveAsNotebook));
        toolBar.addSeparator();
        toolBar.add(new Button(addCell));
        toolBar.add(new Button(runAll));
        toolBar.add(new Button(clearAll));
        toolBar.add(new Button(restart));
        toolBar.add(new Button(stop));
    }

    private class NewNotebookAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/notebook.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public NewNotebookAction() {
            super(bundle.getString("New"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N,
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
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
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O,
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            workspace.openNotebook();
        }
    }

    private class SaveNotebookAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/save.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public SaveNotebookAction() {
            super(bundle.getString("Save"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            workspace.notebook().ifPresent(book -> workspace.saveNotebook(book, false));
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
            workspace.notebook().ifPresent(book -> workspace.saveNotebook(book, true));
        }
    }

    private class AutoSaveAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/refresh.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        final Timer timer = new Timer(60000, e -> {
            for (var notebook : workspace.notebooks()) {
                if (notebook.getFile() != null && !notebook.isSaved()) {
                    workspace.saveNotebook(notebook, false);
                }
            }
        });

        public AutoSaveAction() {
            super(bundle.getString("AutoSave"));
            // Without icon, menu items won't align well on Mac.
            // However, FlatLaf won't show check mark on Windows
            // if we set the icon.
            if (SystemInfo.isMacFullWindowContentSupported) {
                putValue(SMALL_ICON, icon16);
                putValue(LARGE_ICON_KEY, icon24);
            }
            timer.setInitialDelay(1000);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof JCheckBoxMenuItem autoSave) {
                prefs.putBoolean(AUTO_SAVE_KEY, autoSave.isSelected());
                if (autoSave.isSelected()) {
                    timer.start();
                } else {
                    timer.stop();
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

    private class TutorialAction extends AbstractAction {
        public TutorialAction() {
            super(bundle.getString("Tutorials"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            browse(SmileStudio.this, "https://haifengl.github.io/quickstart.html",
                    bundle.getString("Tutorials"));
        }
    }

    private class JavaDocAction extends AbstractAction {
        public JavaDocAction() {
            super(bundle.getString("JavaDocs"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            browse(SmileStudio.this, "https://haifengl.github.io/api/java/index.html",
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
                    For commercial use, please contact smile.sales@outlook.com
                    """, version);
            JOptionPane.showMessageDialog(SmileStudio.this,
                    message,
                    bundle.getString("About"),
                    JOptionPane.INFORMATION_MESSAGE,
                    icons.size() > 4 ? new ImageIcon(icons.get(4)) : null);
        }
    }

    /**
     * Sets a system property from a stored preference value if the system
     * property is not already set.
     *
     * @param sysProp  the system-property name to set.
     * @param prefKey  the preference key to read the value from.
     */
    private static void setSystemPropertyFromPrefs(String sysProp, String prefKey) {
        if (System.getProperty(sysProp, "").isBlank()) {
            String value = prefs.get(prefKey, "").trim();
            if (!value.isEmpty()) {
                System.setProperty(sysProp, value);
            }
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
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("""
                    Cannot start Smile Studio as JVM is running in headless mode.
                    Run 'smile shell' for smile shell with Java.
                    Run 'smile scala' for smile shell with Scala.""");
            System.exit(1);
        }

        // macOS global settings
        // Must be set on main thread and before AWT/Swing is initialized
        if (SystemInfo.isMacOS) {
            // To move the menu bar out of the main window to the top of the screen on macOS.
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            // Appearance of window title bars: use current macOS appearance
            System.setProperty("apple.awt.application.appearance", "system");
            // Application name used in screen menu bar (in first menu after the "Apple" menu)
            System.setProperty("apple.awt.application.name", bundle.getString("AppName"));
        }

        if (SystemInfo.isWindows) {
            // Icons may become blurry due to desktop scaling.
            // Set to 1.0 for no scaling.
            System.setProperty("sun.java2d.uiScale", "1.0");
        }

        if (SystemInfo.isLinux) {
            // enable custom window decorations
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }


        // Install font
        FlatJetBrainsMonoFont.install();
        // Application specific UI defaults
        FlatLaf.registerCustomDefaultsSource("smile.studio");
        // FlatLaf.setup() must be called in the main method, before creating
        // any Swing components or the Event Dispatch Thread (EDT).
        String theme = SmileStudio.prefs.get("Theme",
                SystemInfo.isMacOS ? "macLight" : "Light");
        switch (theme) {
            case "Light" -> FlatLightLaf.setup();
            case "Dark" -> FlatDarkLaf.setup();
            case "IntelliJ" -> FlatIntelliJLaf.setup();
            case "Darcula" -> FlatDarculaLaf.setup();
            case "macLight" -> FlatMacLightLaf.setup();
            case "macDark" -> FlatMacDarkLaf.setup();
            default -> {
                logger.warn("Unknown theme '{}', falling back to Light", theme);
                FlatLightLaf.setup();
            }
        }

        // Creating and showing GUI in EDT.
        SwingUtilities.invokeLater(() -> {
            if (args != null && args.length > 0) {
                logger.warn("Smile Studio doesn't take arguments. Please start Smile Studio in your project directory.");
            }
            createAndShowGUI();
        });
    }
}
