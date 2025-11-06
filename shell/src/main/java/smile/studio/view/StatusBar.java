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
package smile.studio.view;

import javax.swing.*;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ResourceBundle;
import com.sun.management.OperatingSystemMXBean;

/**
 * A status bar poses an information area typically found at the window's bottom.
 *
 * @author Haifeng Li
 */
public class StatusBar extends JPanel {
    /** The message resource bundle. */
    final ResourceBundle bundle = ResourceBundle.getBundle(StatusBar.class.getName(), getLocale());
    /** Status message. */
    final JLabel status = new JLabel(bundle.getString("Ready"));
    /** Status message. */
    final JLabel system = new JLabel();
    /** OS's MXBean */
    final OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    /** Memory's MXBean */
    final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    /** Timer to refresh CPU/Memory usage. */
    final Timer timer;

    /**
     * Constructor.
     */
    public StatusBar() {
        super(new BorderLayout());
        //status.putClientProperty("FlatLaf.styleClass", "monospaced");

        // Left-aligned status message
        status.setHorizontalAlignment(SwingConstants.LEFT);
        // Add some padding to the left side
        status.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        add(status, BorderLayout.WEST);

        // Right-aligned system info
        system.setHorizontalAlignment(SwingConstants.RIGHT);
        // Add some padding to the right side
        system.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        add(system, BorderLayout.EAST);


        timer = new Timer(1000, e -> {
            double cpuLoad = os.getCpuLoad();
            double usedHeap = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024.0);
            String unit = "MB";
            if (usedHeap >= 1024) {
                usedHeap /= 1024;
                unit = "GB";
            }
            String info = String.format(bundle.getString("SystemInfo"), usedHeap, unit, (int) (cpuLoad * 100));
            system.setText(info);
        });
        timer.setInitialDelay(5000);
        timer.start();
    }

    /**
     * Updates the status message.
     * @param message the status message.
     */
    public void setStatus(String message) {
        status.setText(message);
    }
}
