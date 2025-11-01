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
import java.awt.image.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import smile.studio.view.*;

/**
 * Smile Studio is an integrated development environment (IDE) for Smile.
 *
 * @author Haifeng Li
 */
public class SmileStudio extends JFrame {
    @Serial
    private static final long serialVersionUID = 1L;
    /** The message resource bundle. */
    static final ResourceBundle bundle = ResourceBundle.getBundle(SmileStudio.class.getName(), Locale.getDefault());
    final JToolBar toolBar = new JToolBar();
    final StatusBar statusBar = new StatusBar();
    final Workspace workspace = new Workspace();
    final Chat chat = new Chat();

    public SmileStudio() {
        super(bundle.getString("AppName"));
        setIcon();
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        initToolBar();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(workspace);
        splitPane.setRightComponent(chat);
        splitPane.setResizeWeight(0.85);

        contentPane.add(toolBar, BorderLayout.NORTH);
        contentPane.add(splitPane, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);
    }

    /**
     * Sets the icon images for the frame.
     */
    private void setIcon() {
        try (InputStream input = SmileStudio.class.getResourceAsStream("images/robot.png")) {
            if (input == null) {
                System.err.println("Resource not found: images/robot.png");
                return;
            }

            BufferedImage icon = ImageIO.read(input);
            ArrayList<Image> icons = new ArrayList<>();
            int[] sizes = {16, 24, 32, 48, 64, 128, 256, 512};
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

    /** Initializes the toolbar. */
    private void initToolBar() {
        // Don't allow the toolbar to be dragged and undocked
        toolBar.setFloatable(false);
        // Show a border only when the mouse hovers over a button
        toolBar.setRollover(true);
        //toolBar.add(button("New", e -> newNotebook()));
        toolBar.add(button("Open…", e -> openNotebook()));
        toolBar.add(button("Save", e -> saveNotebook(false)));
        toolBar.add(button("Save As…", e -> saveNotebook(true)));
        toolBar.addSeparator();
        toolBar.add(button("Add Cell", e -> workspace.notebook().addCell(null)));
        toolBar.add(button("Run All ▶▶", e -> workspace.notebook().runAllCells()));
        toolBar.add(button("Clear Outputs", e -> workspace.notebook().clearAllOutputs()));
    }

    private JButton button(String text, AbstractAction action) {
        JButton b = new JButton(action);
        b.setText(text);
        return b;
    }

    private JButton button(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        return b;
    }

    private boolean confirmDiscardIfUnsaved() {
        // This minimal sample doesn’t track dirty state.
        // Prompt anyway when opening/new.
        int res = JOptionPane.showConfirmDialog(this,
                "This will clear the current notebook. Continue?",
                "Confirm", JOptionPane.OK_CANCEL_OPTION);
        return res == JOptionPane.OK_OPTION;
    }

    private void openNotebook() {
        /*
        if (!confirmDiscardIfUnsaved()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Notebook");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Simple Notebook (*.snb)", "snb"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                List<String> cells = parseCells(lines);
                cellsPanel.removeAll();
                for (String src : cells) {
                    CodeCellPanel cell = new CodeCellPanel();
                    cell.codeArea.setText(src);
                    cellsPanel.add(cell);
                }
                if (cells.isEmpty()) addCell(null);
                cellsPanel.revalidate();
                cellsPanel.repaint();
                currentFile = f;
                setTitle("Simple Java Notebook - " + f.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to open: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }*/
    }

    private void saveNotebook(boolean saveAs) {
        /*
        if (currentFile == null || saveAs) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Notebook");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Simple Notebook (*.snb)", "snb"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                if (!f.getName().toLowerCase().endsWith(".snb")) {
                    f = new File(f.getParentFile(), f.getName() + ".snb");
                }
                currentFile = f;
            } else {
                return;
            }
        }
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# SimpleNotebook v1");
            lines.add("created: " + ZonedDateTime.now());
            for (int i = 0; i < cellsPanel.getComponentCount(); i++) {
                CodeCellPanel c = getCell(i);
                lines.add("CELL");
                lines.addAll(codeToLines(c.codeArea.getText()));
                lines.add("ENDCELL");
            }
            Files.write(currentFile.toPath(), lines, StandardCharsets.UTF_8);
            setTitle("Simple Java Notebook - " + currentFile.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        */
    }

    private static List<String> parseCells(List<String> lines) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = null;
        boolean inCell = false;
        for (String line : lines) {
            if (line.equals("CELL")) {
                inCell = true;
                current = new StringBuilder();
            } else if (line.equals("ENDCELL")) {
                if (current != null) cells.add(current.toString());
                current = null;
                inCell = false;
            } else if (inCell) {
                current.append(line).append("\n");
            }
        }
        return cells;
    }

    private static List<String> codeToLines(String code) throws IOException {
        List<String> l = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new StringReader(code))) {
            String s;
            while ((s = br.readLine()) != null) l.add(s);
        }
        return l;
    }

    /**
     * Creates and shows the GUI. For thread safety, this method should be
     * invoked from the event dispatch thread.
     * @param exitOnClose the behavior when the user attempts to close the window.
     */
    public static void createAndShowGUI(boolean exitOnClose) {
        // Set application monospaced font before setting up FlatLaf
        FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
        FlatLightLaf.setup();

        // Create and set up the window.
        SmileStudio studio = new SmileStudio();
        studio.setSize(new Dimension(1200, 800));

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

        if (exitOnClose) {
            studio.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } else {
            studio.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
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

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Cannot start Smile Studio as JVM is running in headless mode.");
            System.exit(1);
        }

        // Install font in main
        FlatJetBrainsMonoFont.install();
        // Schedule a job for the event dispatch thread:
        // creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(() -> createAndShowGUI(true));
    }
}
