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
package smile.studio.kernel;

import java.util.ArrayList;
import java.util.List;
import smile.studio.text.OutputArea;

/**
 * A kernel is an execution engine that runs the user's code in a specific
 * programming language. It may run as a standalone process (e.g. JShell).
 *
 * @param <T> the type of code evaluation events.
 * @author Haifeng Li
 */
public abstract class Kernel<T> {
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
     * @return the value of last variable snippet. Or null if no variables.
     */
    public Object eval(String code) {
        List<Object> values = new ArrayList<>();
        eval(code, values);
        return values.isEmpty() ? null : values.getLast();
    }

    /**
     * Evaluates a code block.
     * @param code a code block, which may be split into multiple snippets
     *             to be evaluated by execution engine.
     * @param values the container for the values returned by execution engine.
     * @return true if no errors detected.
     */
    public abstract boolean eval(String code, List<Object> values);

    /**
     * Processes the events caused by the code evaluation,
     * or the returned values by execution engine.
     * @param events the events caused by the code evaluation.
     */
    public abstract void process(List<T> events);

    /**
     * Returns the list of named variables.
     * @return the list of named variables.
     */
    public abstract List<Variable> variables();

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

    /**
     * Shuts down the execution engine and frees resources.
     */
    public abstract void close();
}
