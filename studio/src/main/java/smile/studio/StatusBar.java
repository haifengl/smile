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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.text.MessageFormat;
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
    private static final String READY = bundle.getString("Ready");
    /** Status message. */
    private final JLabel status = new JLabel(READY);
    /** Status message. */
    private final JLabel system = new JLabel();
    /** OS's MXBean */
    private final OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    /** Memory's MXBean */
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    /** The timer to reset status message. */
    private final Timer timer = new Timer(60000, e -> status.setText(READY));

    /**
     * Constructor.
     */
    public StatusBar() {
        super(new BorderLayout());

        // One-time execution.
        // Timer will be restarted every time a new status message is set.
        timer.setRepeats(false);

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
        var refresher = createRefresher();
        refresher.setInitialDelay(5000);
        refresher.start();
    }

    /**
     * Creates a timer that updates the system information every second.
     * @return the timer.
     */
    private Timer createRefresher() {
        return new Timer(1000, e -> {
            double cpuLoad = os.getCpuLoad();
            double usedHeap = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024.0);
            String unit = "MB";
            if (usedHeap >= 1024) {
                usedHeap /= 1024;
                unit = "GB";
            }
            String heapStr = String.format("%.1f %s", usedHeap, unit);
            // getCpuLoad() returns -1.0 when the value is not available.
            String cpuStr = cpuLoad < 0 ? "N/A" : (int) (cpuLoad * 100) + "%";
            String info = MessageFormat.format(bundle.getString("SystemInfo"), heapStr, cpuStr);
            system.setText(info);
        });
    }

    /**
     * Updates the status message.
     * @param message the status message.
     */
    public void setStatus(String message) {
        status.setText(message);
        timer.restart();
    }

    /**
     * Returns the current status message.
     * @return the status message.
     */
    public String getStatus() {
        return status.getText();
    }
}
