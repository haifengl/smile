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
package smile.studio;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.util.*;
import java.util.prefs.Preferences;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.util.SystemInfo;
import smile.studio.view.*;
import smile.swing.Button;
import smile.swing.FileChooser;
import static smile.swing.SmileUtilities.scaleImageIcon;

/**
 * Smile Studio is an integrated development environment (IDE) for Smile.
 *
 * @author Haifeng Li
 */
public class SmileStudio extends JFrame {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(SmileStudio.class.getName(), Locale.getDefault());
    /** Source code file name extensions. */
    private static final String[] fileNameExtensions = {"java", "jsh"};
    private static final Preferences prefs = Preferences.userNodeForPackage(SmileStudio.class);
    private static final String AUTO_SAVE_KEY = "autoSave";
    /** Each window has its own FileChooser so that it points to its own recent directory. */
    private final JFileChooser fileChooser = new FileChooser();
    private final JMenuBar menuBar = new JMenuBar();
    private final JToolBar toolBar = new JToolBar();
    private final StatusBar statusBar = new StatusBar();
    private final Analyst analyst = new Analyst();
    private final Workspace workspace;

    /**
     * Constructor.
     * @param file the notebook file. If null, a new notebook will be created.
     */
    public SmileStudio(File file) {
        super(bundle.getString("AppName"));
        setFrameIcon();
        setJMenuBar(menuBar);
        initMenuAndToolBar();

        workspace = new Workspace(file);
        if (file != null) setTitle(file);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(workspace);
        splitPane.setRightComponent(analyst);
        splitPane.setResizeWeight(0.7);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add(toolBar, BorderLayout.NORTH);
        contentPane.add(splitPane, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);
        setContentPane(contentPane);

        // Initialized as true so that we won't try to save sample code.
        workspace.notebook().setSaved(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmSaveNotebook()) {
                    saveNotebook(false);
                }
                workspace.close();

                // Exit the app if this is the last window.
                int count = 0;
                for (Window window : Window.getWindows()) {
                    if (window.isVisible() && window instanceof SmileStudio) {
                        count++;
                    }
                }
                if (count <= 1) {
                    System.exit(0);
                }
            }

            @Override
            public void windowOpened(WindowEvent e) {
                // JSplitPane.setDividerLocation() set the location based on
                // current pane size. We should set it after window is opened.
                splitPane.setDividerLocation(0.7);
                // Invoker later so that splitPane.invalidate() be done
                SwingUtilities.invokeLater(() -> workspace.setDividerLocation(0.15));
            }
        });
    }

    /**
     * Sets the icon images for the frame.
     */
    private void setFrameIcon() {
        try (InputStream input = SmileStudio.class.getResourceAsStream("images/robot.png")) {
            if (input == null) {
                System.err.println("Resource not found: images/robot.png");
                return;
            }

            BufferedImage icon = ImageIO.read(input);
            ArrayList<Image> icons = new ArrayList<>();
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
            System.err.println("Error loading image from resource: images/robot.png");
        }
    }

    /** Initializes the menubar and the toolbar. */
    private void initMenuAndToolBar() {
        var newNotebook = new NewNotebookAction();
        var openNotebook = new OpenNotebookAction();
        var saveNotebook = new SaveNotebookAction();
        var saveAsNotebook = new SaveAsNotebookAction();
        var autoSave = new AutoSaveAction();
        var addCell = new AddCellAction();
        var runAll = new RunAllAction();
        var clearAll = new ClearAllAction();
        var stop = new StopAction();
        var settings = new SettingsAction();
        var exit = new ExitAction();

        var autoSaveMenuItem = new JCheckBoxMenuItem(autoSave);
        if (prefs.getBoolean(AUTO_SAVE_KEY, false)) {
            SwingUtilities.invokeLater(() -> autoSaveMenuItem.doClick());
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
        cellMenu.add(new JMenuItem(stop));
        menuBar.add(cellMenu);

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
        toolBar.add(new Button(stop));
    }

    private class NewNotebookAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/notebook.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public NewNotebookAction() {
            super(bundle.getString("New"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
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
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            openNotebook();
        }
    }

    private class SaveNotebookAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/save.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public SaveNotebookAction() {
            super(bundle.getString("Save"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            saveNotebook(false);
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
            saveNotebook(true);
        }
    }

    private class AutoSaveAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/refresh.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        final Timer timer = new Timer(60000, e -> {
            if (workspace.notebook().getFile() != null && !workspace.notebook().isSaved()) {
                saveNotebook(false);
            }
        });

        public AutoSaveAction() {
            super(bundle.getString("AutoSave"));
            // Without icon, menu items wont' align well on Mac.
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
            workspace.notebook().addCell(insertAfter);
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
            workspace.notebook().runAllCells();
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
            workspace.notebook().clearAllOutputs();
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
            workspace.runner().stop();
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

    private class ExitAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(SmileStudio.class.getResource("images/exit.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        public ExitAction() {
            super(bundle.getString("Exit"), icon16);
            putValue(LARGE_ICON_KEY, icon24);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int count = 0;
            for (Window window : Window.getWindows()) {
                if (window.isVisible() && window instanceof SmileStudio studio) {
                    // Simulates a user clicking the close button to trigger WindowListener.
                    studio.dispatchEvent(new WindowEvent(studio, WindowEvent.WINDOW_CLOSING));
                }
            }
        }
    }

    /**
     * Prompts if the notebook is not saved.
     * @return true if ok is clicked.
     */
    private boolean confirmSaveNotebook() {
        if (workspace.notebook().isSaved()) return false;
        int choice = JOptionPane.showConfirmDialog(this,
                bundle.getString("SaveMessage"),
                bundle.getString("SaveTitle"),
                JOptionPane.OK_CANCEL_OPTION);
        return choice == JOptionPane.OK_OPTION;
    }

    /**
     * Creates a new notebook.
     */
    private void newNotebook() {
        SwingUtilities.invokeLater(() -> createAndShowGUI(null));
    }

    /**
     * Sets the frame title with notebook file name.
     * @param file the notebook file.
     */
    private void setTitle(File file) {
        setTitle(bundle.getString("AppName") + " - " + file.getName());
    }

    /**
     * Opens a notebook.
     */
    private void openNotebook() {
        fileChooser.setDialogTitle(bundle.getString("OpenNotebook"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(bundle.getString("SmileFile"), fileNameExtensions));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            createAndShowGUI(file);
        }
    }

    /**
     * Saves the notebook.
     * @param saveAs save the notebook to a new file if true.
     */
    private void saveNotebook(boolean saveAs) {
        if (workspace.notebook().getFile() == null || saveAs) {
            fileChooser.setDialogTitle(bundle.getString("SaveNotebook"));
            fileChooser.setFileFilter(new FileNameExtensionFilter(bundle.getString("SmileFile"), fileNameExtensions));
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                String name = file.getName().toLowerCase();
                if (!(name.endsWith(".java") || name.endsWith(".jsh"))) {
                    file = new File(file.getParentFile(), file.getName() + ".java");
                }
                workspace.notebook().setFile(file);
                setTitle(file);
            } else {
                return;
            }
        }

        try {
            workspace.notebook().save();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Creates and shows the GUI. For thread safety, this method should be
     * invoked from the event dispatch thread.
     * @param file the notebook file.
     */
    public static void createAndShowGUI(File file) {
        // Create and set up the window.
        SmileStudio studio = new SmileStudio(file);
        studio.setSize(new Dimension(1200, 800));
        studio.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

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

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Cannot start Smile Studio as JVM is running in headless mode.");
            System.err.println("Run 'smile shell' for smile shell with Java.");
            System.err.println("Run 'smile scala' for smile shell with Scala.");
            System.exit(1);
        }

        // If user doesn't set system property for api key,
        // we will try to set it from preferences if it exists.
        if (System.getProperty("openai.apiKey", "").isBlank()) {
            String apiKey = SmileStudio.prefs.get(SettingsDialog.OPENAI_API_KEY, "").trim();
            if (!apiKey.isEmpty()) {
                System.setProperty("openai.apiKey", apiKey);
            }
        }
        if (System.getProperty("anthropic.apiKey", "").isBlank()) {
            String apiKey = SmileStudio.prefs.get(SettingsDialog.ANTHROPIC_API_KEY, "").trim();
            if (!apiKey.isEmpty()) {
                System.setProperty("anthropic.apiKey", apiKey);
            }
        }

        // Schedule a job for the event dispatch thread:
        // creating and showing this application's GUI.
        SwingUtilities.invokeLater(() -> {
            // Install font
            FlatJetBrainsMonoFont.install();
            // Set application monospaced font before setting up FlatLaf
            FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
            // Application specific UI defaults
            FlatLaf.registerCustomDefaultsSource("smile.studio");
            FlatLightLaf.setup();

            // Start the GUI
            if (args == null || args.length == 0) {
                createAndShowGUI(null);
            } else {
                for (int i = 0; i < args.length; i++) {
                    File file = new File(args[i]);
                    if (file.isDirectory()) {
                        JOptionPane.showMessageDialog(
                                null,
                                args[i] + bundle.getString("DirectoryError"),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                        return;
                    }

                    if (!file.exists()) {
                        try {
                            file.createNewFile();
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(
                                    null,
                                    ex.getMessage(),
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE
                            );
                            return;
                        }
                    }

                    createAndShowGUI(file);
                }
            }
        });
    }
}
