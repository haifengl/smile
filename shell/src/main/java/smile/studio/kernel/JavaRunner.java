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

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import jdk.jshell.*;
import com.formdev.flatlaf.util.SystemInfo;

/**
 * Java code execution engine.
 *
 * @author Haifeng Li
 */
public class JavaRunner extends Runner {
    /** JShell instance. */
    private final JShell jshell;
    /** Analysis utilities for source code input. */
    private final SourceCodeAnalysis sourceAnalyzer;

    /**
     * Constructor.
     */
    public JavaRunner() {
        PrintStream shellOut = new PrintStream(delegatingOut, true, StandardCharsets.UTF_8);
        PrintStream shellErr = new PrintStream(delegatingOut, true, StandardCharsets.UTF_8);
        var builder = JShell.builder().out(shellOut).err(shellErr)
                .remoteVMOptions("--class-path", System.getProperty("java.class.path"))
                .remoteVMOptions("-XX:MaxMetaspaceSize=1024M")
                .remoteVMOptions("-Xss4M")
                .remoteVMOptions("--add-opens=java.base/java.nio=ALL-UNNAMED")
                .remoteVMOptions("--enable-native-access=ALL-UNNAMED")
                .remoteVMOptions("-Dsmile.home=" + System.getProperty("smile.home", "."));

        if (SystemInfo.isWindows) {
            // Set to 1.0 for no scaling
            builder = builder.remoteVMOptions("-Dsun.java2d.uiScale=1.0");
        }
        jshell = builder.build();
        sourceAnalyzer = jshell.sourceCodeAnalysis();
    }

    /**
     * Closes this engine and frees resources.
     */
    public void close() {
        jshell.close();
    }

    @Override
    public void stop() {
        jshell.stop();
    }

    /**
     * Evaluates a code block.
     * @param code a code block.
     */
    public void eval(String code) {
        eval(code, null);
    }

    /**
     * Evaluates a code block.
     * @param code a code block.
     * @param callback the callback function for each snippet evaluation results,
     *                 which returns the number of execution errors.
     * @return true if no errors reported by callback.
     */
    public boolean eval(String code, ToIntFunction<List<SnippetEvent>> callback) {
        SourceCodeAnalysis.CompletionInfo info = sourceAnalyzer.analyzeCompletion(code);
        while (info.completeness().isComplete()) {
            List<SnippetEvent> events = jshell.eval(info.source());
            if (callback != null && callback.applyAsInt(events) > 0) {
                return false;
            }
            info = sourceAnalyzer.analyzeCompletion(info.remaining());
        }

        if (info.completeness() != SourceCodeAnalysis.Completeness.EMPTY) {
            throw new RuntimeException(info.completeness() + ": " + info.remaining().trim());
        }

        return true;
    }

    /**
     * Diagnoses a code snippet.
     * @param snippet a code snippet.
     * @return the diagnostics result stream.
     */
    public Stream<Diag> diagnostics(Snippet snippet) {
        return jshell.diagnostics(snippet);
    }

    /**
     * Returns the active variable snippets.
     * @return the active variable snippets.
     */
    public Stream<VarSnippet> variables() {
        return jshell.variables();
    }
}
