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
package smile.studio.notebook;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.awt.event.*;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextScrollPane;
import smile.studio.text.Editor;
import smile.util.OS;

/**
 * Notebook cell editor that automatically sets the height
 * based on the text rows.
 *
 * @author Haifeng Li
 */
public class CellEditor extends Editor implements DocumentListener {
    /**
     * The minimum number of rows to display. On Windows, the last line is always
     * counted even without a newline character, so we set it to 0. On Unix/Linux/macOS,
     * the last line is not counted without a newline character, so we
     * set it to 1.
     */
    private static final int MIN_ROWS = OS.isWindows() ? 0 : 1;

    /**
     * Constructor.
     * @param rows the number of rows.
     * @param cols the number of columns.
     * @param style language highlighting style.
     */
    public CellEditor(int rows, int cols, String style) {
        super(rows, cols, style);
        setHighlightCurrentLine(false);
        setPreferredRows();
        getDocument().addDocumentListener(this);
    }

    /**
     * Gets the number of rows to fit the content.
     */
    public int getPreferredRows() {
        // getLineCount returns the number of logical lines,
        // where a new logical line is defined by the presence
        // of a newline character (\n).
        // Therefore, the last line without newline character
        // is not counted. To compenstate, we always add MIN_ROWS.
        return getLineCount() + MIN_ROWS;
    }

    /**
     * Sets the number of rows to fit the content.
     */
    public void setPreferredRows() {
        setRows(getPreferredRows());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    /**
     * Returns the scroll pane for this editor. The scroll pane is used to
     * handle mouse wheel events and adjust the height of the editor based
     * on the content.
     * @return the scroll pane for this editor.
     */
    public RTextScrollPane scroll() {
        RTextScrollPane scroll = new RTextScrollPane(this);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setWheelScrollingEnabled(false);
        scroll.addMouseWheelListener(e -> {
            Cell cell = (Cell) SwingUtilities.getAncestorOfClass(Cell.class, scroll);
            if (cell == null) return;
            JScrollPane notebookScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, cell);
            if (notebookScroll == null) return;

            JScrollBar bar = notebookScroll.getVerticalScrollBar();
            double rotation = e.getPreciseWheelRotation();
            if (rotation != 0.0) {
                int direction = rotation > 0 ? 1 : -1;
                int base = bar.getUnitIncrement(direction);
                int delta = (int) Math.round(base * rotation * 3.0);
                if (delta == 0) {
                    delta = direction;
                }
                bar.setValue(bar.getValue() + delta);
            } else {
                int units = e.getUnitsToScroll();
                if (units != 0) {
                    int direction = units > 0 ? 1 : -1;
                    int delta = bar.getUnitIncrement(direction) * units;
                    bar.setValue(bar.getValue() + delta);
                }
            }
            e.consume();
        });

        return scroll;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        setPreferredRows();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        setPreferredRows();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // attribute changes. no needs to set preferred rows.
    }
}
