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
package smile.studio.kernel;

import smile.studio.view.Cell;

/**
 * Base class of code execution engines.
 *
 * @author Haifeng Li
 */
public abstract class Runner {
    /** Output capture. */
    final CellOutputStream delegatingOut = new CellOutputStream();
    /** Running state. */
    volatile boolean isRunning = false;

    /**
     * Constructor.
     */
    public Runner() {

    }

    /**
     * Returns the running state of the execution engine.
     * @return the running state of the execution engine.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Sets the running state.
     * @param flag the running state.
     */
    public void setRunning(boolean flag) {
        isRunning = flag;
    }

    /**
     * Sets the running cell.
     * @param cell the running cell.
     */
    public void setCell(Cell cell) {
        delegatingOut.setCell(cell);
    }

    /**
     * Removes the running cell.
     */
    public void removeCell() {
        delegatingOut.removeCell();
    }

    /**
     * Attempt to stop currently running code.
     */
    public abstract void stop();
}
