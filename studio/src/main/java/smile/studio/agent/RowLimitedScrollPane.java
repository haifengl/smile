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
package smile.studio.agent;

import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.border.Border;

/**
 * A scroll pane whose preferred height stops at a fixed number of text rows.
 *
 * <p>A {@code JScrollPane}'s preferred size comes from its view's preferred size,
 * and a text area's preferred height grows with its content. Left alone, a pane
 * placed in a tab therefore asks the tab to grow with every streamed line, no
 * matter what the view's {@code rows} property was set to: {@code rows} only sets
 * the preferred height of an <em>empty</em> view, which the content then exceeds.
 *
 * <p>This pane caps the preferred height at one line per row plus its own chrome
 * (border and viewport insets). It is a preferred height only, not a maximum: a
 * layout that hands the tab more space still gets it.
 *
 * @author Haifeng Li
 */
class RowLimitedScrollPane extends JScrollPane {
    /** The view whose line height defines a row. */
    private final AgentOutputArea area;
    /** The number of rows the preferred height is capped to. */
    private final int rows;

    /**
     * Constructor.
     *
     * @param area the output area, whose line height defines one row.
     * @param content the content panel holding the area and tracking the tab width.
     * @param rows the number of rows the preferred height is capped to.
     */
    RowLimitedScrollPane(AgentOutputArea area, JComponent content, int rows) {
        super(content);
        this.area = area;
        this.rows = Math.max(1, rows);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension pref = super.getPreferredSize();
        int lineHeight = area.getLineHeight();
        if (lineHeight <= 0) {
            return pref;
        }
        int cap = lineHeight * rows + chromeHeight();
        return pref.height <= cap ? pref : new Dimension(pref.width, cap);
    }

    /** Returns the height this pane adds around its view: border and viewport insets. */
    private int chromeHeight() {
        int chrome = 0;
        Border border = getBorder();
        if (border != null) {
            Insets insets = border.getBorderInsets(this);
            chrome += insets.top + insets.bottom;
        }
        Border viewportBorder = getViewportBorder();
        if (viewportBorder != null) {
            Insets insets = viewportBorder.getBorderInsets(this);
            chrome += insets.top + insets.bottom;
        }
        return chrome;
    }
}
