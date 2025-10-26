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
import javax.swing.border.*;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.util.FontUtils;
import com.sun.management.OperatingSystemMXBean;

public class StatusBar extends JPanel {
    /** Status message. */
    final JLabel status = new JLabel("Ready");
    /* OS's MXBean */
    final OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    /* Memory's MXBean */
    final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    /* Timer to refresh CPU/Memory usage. */
    final Timer timer;

    /**
     * Constructor.
     */
    public StatusBar() {
        super(new FlowLayout(FlowLayout.LEFT));

        // Create a MatteBorder with a 2-pixel black border only on the top
        //Border topBorder = new MatteBorder(2, 0, 0, 0, Color.BLACK);
        //setBorder(topBorder);

        Font font = FontUtils.getCompositeFont(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 12);
        status.setFont(font);
        add(status);

        timer = new Timer(1000, e -> {
            double cpuLoad = os.getCpuLoad();
            double usedHeap = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024.0);
            String unit = "MB";
            if (usedHeap >= 1024) {
                usedHeap /= 1024;
                unit = "GB";
            }
            String message = String.format("Heap Memory: %4.1f %s    CPU Usage: %d%%", usedHeap, unit, (int) (cpuLoad * 100));
            status.setText(message);
        });
        timer.setInitialDelay(5000);
        timer.start();
    }

    /**
     * Update the status message.
     * @param message the status message.
     */
    public void setStatus(String message) {
        status.setText(message);
        timer.stop();
        timer.restart();
    }
}
