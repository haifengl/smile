/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.event.*;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * Notebook cell editor that automatically sets the height
 * based on the text rows.
 *
 * @author Haifeng Li
 */
public class CellEditor extends Editor {
    /**
     * Constructor.
     * @param rows the number of rows.
     * @param cols the number of columns.
     * @param style language highlighting style.
     */
    public CellEditor(int rows, int cols, String style) {
        super(rows, cols, style);
    }

    /**
     * Gets the number of rows to fit the content.
     */
    public int getPreferredRows() {
        return Math.max(1, getLineCount());
    }

    /**
     * Sets the number of rows to fit the content.
     */
    public void setPreferredRows() {
        setRows(getPreferredRows());
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        var insets = getInsets();
        int lines = Math.max(getLineCount(), 1);
        int lineHeight = getFontMetrics(getFont()).getHeight();
        int height = lines * lineHeight + insets.top + insets.bottom + 4;
        return new Dimension(d.width, height);
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
}
