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

import javax.swing.JPanel;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

public class StatusBar extends JPanel {
    public StatusBar() {
        // Get the operating system's MXBean
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        // CPU Usage
        double cpuLoad = os.getSystemLoadAverage();
        System.out.println("CPU Usage: " + (cpuLoad * 100) + "%");

        // Memory Usage
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long usedMemory = memory.getHeapMemoryUsage().getUsed();
        long maxMemory = memory.getHeapMemoryUsage().getMax();
        System.out.println("Memory Usage: " + (usedMemory * 100.0 / maxMemory) + "%");
    }
}
