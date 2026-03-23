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
package smile.studio.kernel;

import smile.studio.view.OutputArea;

/**
 * A kernel is an execution engine that runs the user's code in a specific
 * programming language. It may run as a standalone process (e.g. JShell).
 *
 * @author Haifeng Li
 */
public abstract class Kernel implements AutoCloseable {
    /** Output capture. */
    final ConsoleOutputStream console = new ConsoleOutputStream();
    /** Running state. */
    volatile boolean isRunning = false;

    /**
     * Constructor.
     */
    public Kernel() {

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
     * Sets the output area.
     * @param area the output area for redirected stream.
     */
    public void setOutputArea(OutputArea area) {
        console.setOutputArea(area);
    }

    /**
     * Removes the output area.
     */
    public void removeOutputArea() {
        console.removeOutputArea();
    }

    /**
     * Evaluates a code block.
     * @param code a code block.
     * @return the value returned by execution engine. Maybe null.
     */
    public abstract Object eval(String code);

    /**
     * Restarts the execution engine.
     */
    public abstract void restart();

    /**
     * Resets the internal state of execution engine.
     */
    public abstract void reset();

    /**
     * Attempts to stop currently running code.
     */
    public abstract void stop();
}
