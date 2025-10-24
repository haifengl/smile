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
import java.io.Serial;
import javax.swing.*;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import smile.studio.view.Explorer;
import smile.studio.view.LogArea;
import smile.studio.view.Notebook;

/**
 * Smile Studio is an integrated development environment (IDE) for Smile.
 *
 * @author Haifeng Li
 */
public class SmileStudio extends JFrame {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Explorer explorer;
    private final Notebook notebook;
    private final LogArea logArea;
    private final JSplitPane workspace;

    public SmileStudio() {
        super("Smile Studio");
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        explorer = new Explorer();
        notebook = new Notebook();
        logArea = new LogArea();

        workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        workspace.setTopComponent(notebook);
        workspace.setBottomComponent(logArea);
        workspace.setDividerLocation(700);

        // Add the scroll panes to a split pane.
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(explorer);
        splitPane.setRightComponent(workspace);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension minimumSize = new Dimension(100, 50);
        notebook.setMinimumSize(minimumSize);
        workspace.setMinimumSize(minimumSize);
        explorer.setMinimumSize(minimumSize);
        splitPane.setDividerLocation(300);
        splitPane.setPreferredSize(new Dimension(1200, 800));

        contentPane.add(splitPane, BorderLayout.CENTER);
        LogStreamAppender.setStaticOutputStream(logArea.getOutputStream());
    }

    /**
     * Creates and shows the GUI. For thread safety, this method should be
     * invoked from the event dispatch thread.
     */
    public static void createAndShowGUI(boolean exitOnClose) {
        // Set application monospaced font before setting up FlatLaf
        FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
        FlatLightLaf.setup();

        // Create and set up the window.
        JFrame frame = new SmileStudio();
        frame.setSize(new Dimension(1200, 800));

        // macOS window settings
        if (SystemInfo.isMacFullWindowContentSupported) {
            // Full window content
            frame.getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
            // Transparent title bar
            frame.getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
            // The window title is painted using the system appearance, and it overlaps
            // Swing components. Hide the window title.
            frame.getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
            // macOS red/orange/green buttons overlap Swing components (e.g. toolbar).
            // Add some space to avoid the overlapping.
            // toolBar.add(Box.createHorizontalStrut( 70 ), 0);
        }

        if (exitOnClose) {
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } else {
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        }

        // Set the frame at the center of screen
        frame.setLocationRelativeTo(null);
        // Display the window.
        frame.pack();
        frame.setVisible(true);
        // Maximize the frame. Must be after setVisible(true).
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    public static void main(String[] args) {
        // macOS global settings
        // Must be set on main thread and before AWT/Swing is initialized
        if (SystemInfo.isMacOS) {
            // To move the menu bar out of the main window to the top of the screen on macOS.
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            // Appearance of window title bars: use current macOS appearance
            System.setProperty("apple.awt.application.appearance", "system");
            // Application name used in screen menu bar (in first menu after the "Apple" menu)
            System.setProperty("apple.awt.application.name", "Smile Studio");
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
