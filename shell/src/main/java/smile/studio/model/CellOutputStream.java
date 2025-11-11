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
package smile.studio.model;

import smile.studio.view.Cell;

import javax.swing.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class CellOutputStream extends OutputStream {
    /** JShell running cell. */
    private Cell cell;
    /** Timestamp of last time updating cell output. */
    private long stamp;

    /**
     * Constructor.
     */
    public CellOutputStream() {

    }

    @Override
    public void write(int b) {
        if (cell != null) {
            StringBuffer buffer = cell.buffer();
            buffer.append((char) b);
        }
    }
    @Override
    public void write(byte[] b, int off, int len) {
        if (cell != null) {
            StringBuffer buffer = cell.buffer();
            buffer.append(new String(b, off, len, StandardCharsets.UTF_8));
            long time = System.currentTimeMillis();
            if (time - stamp > 100) {
                stamp = time;
                SwingUtilities.invokeLater(() -> {
                    if (cell != null) {
                        cell.setOutput(cell.buffer().toString());
                    }
                });
            }
        }
    }

    /**
     * Sets the running cell.
     * @param cell the running cell.
     */
    public void setCell(Cell cell) {
        this.cell = cell;
        stamp = System.currentTimeMillis();
    }

    /**
     * Removes the running cell.
     */
    public void removeCell() {
        cell = null;
    }
}
