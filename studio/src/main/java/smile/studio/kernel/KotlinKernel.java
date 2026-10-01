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

    /**
     * Returns true if the given classpath entry path belongs to smile-scala
     * or Scala compiler tooling jars.
     * @param path the classpath entry path.
     * @return true if the entry belongs to smile-scala or Scala compiler tooling.
     */
    public static boolean isScalaClasspathEntry(String path) {
        return ScriptRunnerBridge.isScalaClasspathEntry(path);
    }
}
