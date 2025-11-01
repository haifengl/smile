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

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import jdk.jshell.*;

/**
 * Java code execution engine.
 */
public class Runner {
    /** JShell instance. */
    private JShell jshell;
    /** JShell out buffer. */
    private final ThreadLocal<StringBuilder> outBuffer = new ThreadLocal<>();
    /** JShell out stream. */
    private final PrintStream shellOut;
    /** JShell err stream. */
    private final PrintStream shellErr;
    /** JShell running state. */
    private volatile boolean isRunning = false;

    /**
     * Constructor.
     */
    public Runner() {
        // Build JShell with custom output capture
        OutputStream delegatingOut = new OutputStream() {
            @Override public void write(int b) {
                StringBuilder sb = outBuffer.get();
                if (sb != null) sb.append((char) b);
            }
            @Override public void write(byte[] b, int off, int len) {
                StringBuilder sb = outBuffer.get();
                if (sb != null) sb.append(new String(b, off, len, StandardCharsets.UTF_8));
            }
        };
        
        shellOut = new PrintStream(delegatingOut, true, StandardCharsets.UTF_8);
        shellErr = new PrintStream(delegatingOut, true, StandardCharsets.UTF_8);
        jshell = JShell.builder().out(shellOut).err(shellErr).build();
        jshell.eval("import java.util.*;");
        jshell.eval("import java.math.*;");
        jshell.eval("import java.util.stream.*;");
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
     * Sets the output buffer.
     * @param sb the output buffer.
     */
    public void setOutBuffer(StringBuilder sb) {
        outBuffer.set(sb);
    }

    /**
     * Removes the cell output buffer.
     */
    public void removeOutBuffer() {
        outBuffer.remove();
    }

    /**
     * Evaluates a code snippet.
     * @param code a code snippet.
     * @return the evaluation results.
     */
    public List<SnippetEvent> eval(String code) {
        return jshell.eval(code);
    }

    /**
     * Diagonstics a code snippet.
     * @param snippet a code snippet.
     * @return the diagnostics result stream.
     */
    public Stream<Diag> diagnostics(Snippet snippet) {
        return jshell.diagnostics(snippet);
    }
}
