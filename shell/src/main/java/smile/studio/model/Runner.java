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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.*;
import jdk.jshell.*;
import smile.studio.view.Cell;

/**
 * Java code execution engine.
 */
public class Runner {
    /** JShell instance. */
    private final JShell jshell;
    /** Analysis utilities for source code input. */
    private final SourceCodeAnalysis sourceAnalyzer;
    /** JShell out stream. */
    private final PrintStream shellOut;
    /** JShell err stream. */
    private final PrintStream shellErr;
    /** JShell running state. */
    private volatile boolean isRunning = false;
    /** JShell running cell. */
    private Cell cell;
    /** Timestamp of last time updating cell output. */
    private long stamp;

    /**
     * Constructor.
     */
    public Runner() {
        // Build JShell with custom output capture
        OutputStream delegatingOut = new OutputStream() {
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
        };

        shellOut = new PrintStream(delegatingOut, true, StandardCharsets.UTF_8);
        shellErr = new PrintStream(delegatingOut, true, StandardCharsets.UTF_8);
        jshell = JShell.builder().out(shellOut).err(shellErr)
                .remoteVMOptions("--class-path", System.getProperty("java.class.path"))
                .remoteVMOptions("-Dsmile.home=" + System.getProperty("smile.home", "."))
                .build();
        sourceAnalyzer = jshell.sourceCodeAnalysis();
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
        this.cell = cell;
        stamp = System.currentTimeMillis();
    }

    /**
     * Removes the running cell.
     */
    public void removeCell() {
        cell = null;
    }

    /**
     * Evaluates a code snippet.
     * @param code a code snippet.
     * @return the evaluation results.
     */
    public List<SnippetEvent> eval(String code) {
        List<SnippetEvent> results = new ArrayList<>();
        SourceCodeAnalysis.CompletionInfo info;
        for (info = sourceAnalyzer.analyzeCompletion(code); info.completeness().isComplete(); info = sourceAnalyzer.analyzeCompletion(info.remaining())) {
            results.addAll(jshell.eval(info.source()));
        }

        if (info.completeness() != SourceCodeAnalysis.Completeness.EMPTY) {
            throw new RuntimeException(info.completeness() + ": " + info.remaining().trim());
        }

        return results;
    }

    /**
     * Diagnoses a code snippet.
     * @param snippet a code snippet.
     * @return the diagnostics result stream.
     */
    public Stream<Diag> diagnostics(Snippet snippet) {
        return jshell.diagnostics(snippet);
    }
}
