/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.swing;

import org.junit.jupiter.api.*;
import smile.swing.table.TableCopyPasteAdapter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TableCopyPasteAdapter}.
 *
 * The adapter registers itself via {@code registerKeyboardAction}, so the
 * action is stored under a {@link KeyStroke} key in the {@code InputMap}, not
 * as a named entry in the {@code ActionMap}. We therefore invoke
 * {@code actionPerformed} on the adapter instance directly.
 */
public class TableCopyPasteAdapterTest {

    /** Creates a simple JTable pre-filled with sequential integers. */
    private static JTable buildTable(int rows, int cols) {
        Object[][] data = new Object[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                data[r][c] = r * cols + c;
            }
        }
        String[] headers = new String[cols];
        for (int c = 0; c < cols; c++) headers[c] = "C" + c;
        return new JTable(new DefaultTableModel(data, headers));
    }

    /** Fires the named action command on the adapter. */
    private static void fire(TableCopyPasteAdapter adapter, JTable table, String command) {
        adapter.actionPerformed(
                new ActionEvent(table, ActionEvent.ACTION_PERFORMED, command));
    }

    private static void putOnClipboard(String text) {
        StringSelection sel = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
    }

    // ── Copy ─────────────────────────────────────────────────────────────────

    @Test
    public void testCopySelectedCellsPutsTabSeparatedDataOnClipboard() throws Exception {
        JTable table = buildTable(3, 3);
        TableCopyPasteAdapter adapter = TableCopyPasteAdapter.apply(table);

        table.setCellSelectionEnabled(true);
        table.setRowSelectionInterval(0, 1);
        table.setColumnSelectionInterval(0, 1);

        fire(adapter, table, "Copy");

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        String content = (String) clipboard.getData(DataFlavor.stringFlavor);

        // Expected: "0\t1\n3\t4\n"
        assertTrue(content.contains("\t"), "Columns should be tab-separated");
        assertTrue(content.contains("\n"), "Rows should be newline-separated");
        String[] lines = content.split("\n");
        assertEquals(2, lines.length);
        assertEquals("0\t1", lines[0]);
        assertEquals("3\t4", lines[1]);
    }

    @Test
    public void testCopyNonContiguousSelectionLeavesClipboardUnchanged() {
        JTable table = buildTable(4, 4);
        TableCopyPasteAdapter adapter = TableCopyPasteAdapter.apply(table);

        // Put a known sentinel value on the clipboard first.
        putOnClipboard("SENTINEL");

        // Build a non-contiguous row selection: rows 0 and 2 are not adjacent.
        table.setCellSelectionEnabled(true);
        table.clearSelection();
        table.addRowSelectionInterval(0, 0);
        table.addRowSelectionInterval(2, 2);
        table.setColumnSelectionInterval(0, 0);

        // The adapter must silently reject the non-contiguous selection.
        fire(adapter, table, "Copy");

        // Clipboard must still contain the original sentinel — not overwritten.
        try {
            String content = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getData(DataFlavor.stringFlavor);
            assertEquals("SENTINEL", content,
                    "Non-contiguous copy must not overwrite the clipboard");
        } catch (Exception ex) {
            fail("Unexpected exception reading clipboard: " + ex.getMessage());
        }
    }

    @Test
    public void testCopySingleCellProducesValueWithNewline() throws Exception {
        JTable table = buildTable(2, 2);
        TableCopyPasteAdapter adapter = TableCopyPasteAdapter.apply(table);

        table.setCellSelectionEnabled(true);
        table.setRowSelectionInterval(0, 0);
        table.setColumnSelectionInterval(0, 0);

        fire(adapter, table, "Copy");

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        String content = (String) clipboard.getData(DataFlavor.stringFlavor);
        // Single cell: value "0" followed by a newline
        assertEquals("0\n", content);
    }

    // ── Paste guard ──────────────────────────────────────────────────────────

    @Test
    public void testPasteWithNoSelectionDoesNotThrow() {
        JTable table = buildTable(3, 3);
        TableCopyPasteAdapter adapter = TableCopyPasteAdapter.apply(table);
        table.clearSelection();

        assertDoesNotThrow(() -> fire(adapter, table, "Paste"));
    }

    @Test
    public void testPasteWritesClipboardDataIntoTable() {
        JTable table = buildTable(4, 4);
        TableCopyPasteAdapter adapter = TableCopyPasteAdapter.apply(table);

        putOnClipboard("99\t88\n77\t66\n");

        table.setCellSelectionEnabled(true);
        table.setRowSelectionInterval(1, 1);
        table.setColumnSelectionInterval(1, 1);

        fire(adapter, table, "Paste");

        assertEquals("99", table.getValueAt(1, 1).toString());
        assertEquals("88", table.getValueAt(1, 2).toString());
        assertEquals("77", table.getValueAt(2, 1).toString());
        assertEquals("66", table.getValueAt(2, 2).toString());
    }

    @Test
    public void testPasteClipsAtTableBoundary() {
        JTable table = buildTable(2, 2);
        TableCopyPasteAdapter adapter = TableCopyPasteAdapter.apply(table);

        // Data that would overflow a 2×2 table pasted at (0, 0)
        putOnClipboard("A\tB\tC\nD\tE\tF\n");

        table.setCellSelectionEnabled(true);
        table.setRowSelectionInterval(0, 0);
        table.setColumnSelectionInterval(0, 0);

        assertDoesNotThrow(() -> fire(adapter, table, "Paste"));

        // Only cells within bounds should be modified
        assertEquals("A", table.getValueAt(0, 0).toString());
        assertEquals("B", table.getValueAt(0, 1).toString());
    }
}
