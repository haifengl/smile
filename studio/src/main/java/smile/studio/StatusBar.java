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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.sun.management.OperatingSystemMXBean;
import smile.license.LicenseKey;
import smile.studio.license.LicenseState;

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
    /** System CPU/Heap message. */
    private final JLabel system = new JLabel();
    /** Clickable license mode / customer badge on the far right. */
    private final JLabel licenseBadge = new JLabel();
    /** Optional action executed when the user clicks the license badge. */
    private Runnable onLicenseClick;
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
        status.setBorder(new EmptyBorder(0, 8, 0, 0));
        add(status, BorderLayout.WEST);

        // Right-aligned container for system info + license badge
        JPanel eastPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        eastPane.setOpaque(false);

        system.setHorizontalAlignment(SwingConstants.RIGHT);
        eastPane.add(system);

        licenseBadge.setHorizontalAlignment(SwingConstants.RIGHT);
        licenseBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        licenseBadge.setBorder(new EmptyBorder(0, 6, 0, 4));
        licenseBadge.setVisible(false);
        licenseBadge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && onLicenseClick != null) {
                    onLicenseClick.run();
                }
            }
        });
        eastPane.add(licenseBadge);

        add(eastPane, BorderLayout.EAST);

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
     * Sets the callback invoked when the user clicks the right-hand license badge.
     *
     * @param action the callback to invoke.
     */
    public void setOnLicenseClick(Runnable action) {
        this.onLicenseClick = action;
    }

    /**
     * Updates the right-hand license badge text, warning color, and tooltip from the
     * given {@link LicenseState}.
     *
     * @param state the active license state.
     */
    public void setLicenseState(LicenseState state) {
        setLicenseState(state, LocalDate.now());
    }

    /**
     * Updates the right-hand license badge text, warning color, and tooltip from the
     * given {@link LicenseState} as of {@code today}.
     *
     * @param state the active license state.
     * @param today the date used to compute remaining days.
     */
    public void setLicenseState(LicenseState state, LocalDate today) {
        if (state == null) {
            licenseBadge.setVisible(false);
            return;
        }
        Color defaultFg = UIManager.getColor("Label.foreground");
        Color warningFg = new Color(0xD9, 0x77, 0x06); // amber warning for <14 days or trial

        switch (state.mode()) {
            case COMMERCIAL_LICENSED -> {
                if (state.key().isPresent()) {
                    LicenseKey k = state.key().get();
                    String text = "[" + MessageFormat.format(bundle.getString("BadgeLicensed"), k.customer()) + "]";
                    String tooltip = MessageFormat.format(
                            bundle.getString("TooltipLicensed"),
                            k.tier().name(),
                            k.seats(),
                            k.expiresAt().toString());
                    licenseBadge.setText(text);
                    licenseBadge.setToolTipText(tooltip);
                    licenseBadge.setForeground(k.daysRemaining(today) < 14 ? warningFg : defaultFg);
                    licenseBadge.setVisible(true);
                } else {
                    setEvaluationBadge(defaultFg);
                }
            }
            case COMMERCIAL_TRIAL -> {
                long daysLeft = state.trialDaysRemaining(today);
                String expStr = state.trialExpiresAt().map(LocalDate::toString).orElse("");
                String text = "[" + MessageFormat.format(bundle.getString("BadgeTrial"), daysLeft) + "]";
                String tooltip = MessageFormat.format(bundle.getString("TooltipTrial"), daysLeft, expStr);
                licenseBadge.setText(text);
                licenseBadge.setToolTipText(tooltip);
                licenseBadge.setForeground(daysLeft < 7 ? warningFg : defaultFg);
                licenseBadge.setVisible(true);
            }
            default -> setEvaluationBadge(defaultFg);
        }
        revalidate();
        repaint();
    }

    private void setEvaluationBadge(Color fg) {
        licenseBadge.setText("[" + bundle.getString("BadgeEvaluation") + "]");
        licenseBadge.setToolTipText(bundle.getString("TooltipEvaluation"));
        licenseBadge.setForeground(fg);
        licenseBadge.setVisible(true);
    }

    /**
     * Returns the current license badge text (for testing).
     *
     * @return the license badge text.
     */
    public String getLicenseBadgeText() {
        return licenseBadge.getText();
    }

    /**
     * Returns the current license badge tooltip (for testing).
     *
     * @return the license badge tooltip.
     */
    public String getLicenseBadgeTooltip() {
        return licenseBadge.getToolTipText();
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
