/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.kernel;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Kotlin code execution engine.
 *
 * <p>It runs the scripts with the Kotlin scripting host (see
 * {@link ScriptRunnerBridge}), which compiles each snippet against the
 * declarations of the previously evaluated ones. As the scripting engine
 * runs in-process, the code shares the JVM of Studio and thus can reference
 * the SMILE libraries directly.
 *
 * @author Haifeng Li
 */
public class KotlinKernel extends Kernel<ScriptResult> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KotlinKernel.class);
    /** The scripting engine of the Kotlin session. */
    private ScriptRunnerBridge bridge;
    /** The stream that redirects the script's standard output to the console. */
    private final PrintStream output = new PrintStream(console, true, StandardCharsets.UTF_8);

    /**
     * Constructor.
     */
    public KotlinKernel() {
        restart();
    }

    @Override
    public synchronized void restart() {
        close();
        bridge = new ScriptRunnerBridge();
    }

    @Override
    public synchronized void close() {
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
    }

    @Override
    public void reset() {
        if (bridge != null) {
            bridge.reset();
        }
    }

    @Override
    public void stop() {
        // The Kotlin scripting engine runs in-process and does not expose a
        // way to interrupt a running snippet. The kernel is restarted instead
        // by the caller when the user stops the execution.
        logger.warn("Kotlin scripting engine does not support stopping execution. Restarting the kernel instead.");
    }

    @Override
    public boolean eval(String code, List<Object> values) {
        if (bridge == null) {
            logger.error("Kotlin scripting engine is not running.");
            return false;
        }

        // The scripting engine evaluates the snippet in the current JVM, so
        // redirect the standard streams to capture the script's output. They
        // are restored afterward even if the script throws.
        PrintStream out = System.out;
        PrintStream err = System.err;
        System.setOut(output);
        System.setErr(output);
        try {
            ScriptResult result = bridge.eval(code);
            process(List.of(result));
            if (result.success() && result.value() != null) {
                values.add(result.value());
            }
            return result.success();
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
    }

    /**
     * Prints the results of the code evaluation to the console.
     * @param results the results caused by the code evaluation.
     */
    @Override
    public void process(List<ScriptResult> results) {
        var area = console.getOutputArea();
        if (area == null) return;

        for (ScriptResult result : results) {
            if (!result.success()) {
                if (result.error() != null) area.println(result.error());
            } else if (result.value() != null) {
                String typeName = result.typeName();
                if (result.name() != null && typeName != null) {
                    area.print("⇒ " + typeName + " " + result.name() + " = ");
                }
                area.println(String.valueOf(result.value()));
            }
        }
    }

    /**
     * Returns the variables defined by the evaluated snippets.
     * @return the list of named variables.
     */
    @Override
    public List<Variable> variables() {
        if (bridge == null) return List.of();
        return bridge.variables().stream()
                .map(v -> new Variable(v.name(), v.typeName()))
                .toList();
    }
}
