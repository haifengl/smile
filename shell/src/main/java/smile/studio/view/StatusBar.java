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
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Locale;
import java.util.ResourceBundle;
import com.sun.management.OperatingSystemMXBean;

/**
 * A status bar poses an information area typically found at the window's bottom.
 *
 * @author Haifeng Li
 */
public class StatusBar extends JPanel {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(StatusBar.class.getName(), Locale.getDefault());
    /** Status message. */
    private final JLabel status = new JLabel(bundle.getString("Ready"));
    /** Status message. */
    private final JLabel system = new JLabel();
    /** OS's MXBean */
    private final OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    /** Memory's MXBean */
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

    /**
     * Constructor.
     */
    public StatusBar() {
        super(new BorderLayout());

        // Left-aligned status message
        status.setHorizontalAlignment(SwingConstants.LEFT);
        // Add some padding to the left side
        status.setBorder(new EmptyBorder(0, 8, 0, 0));
        add(status, BorderLayout.WEST);

        // Right-aligned system info
        system.setHorizontalAlignment(SwingConstants.RIGHT);
        // Add some padding to the right side
        system.setBorder(new EmptyBorder(0, 0, 0, 8));
        add(system, BorderLayout.EAST);

        // Timer to refresh CPU/Memory usage
        Timer timer = new Timer(1000, e -> {
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
