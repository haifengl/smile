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

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Script execution engine.
 *
 * @author Haifeng Li
 */
public class ScriptKernel extends Kernel<Object> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScriptKernel.class);
    private final PrintStream printer = new PrintStream(console, true, StandardCharsets.UTF_8);
    private final PrintWriter writer = new PrintWriter(console, true, StandardCharsets.UTF_8);
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final String name;
    private ScriptEngine engine;

    static {
        // Kotlin Script Engine often fails to inherit the host JVM classpath.
        // To fix this, explicitly add required jars via system property.
        System.setProperty("kotlin.script.classpath", System.getProperty("java.class.path"));
    }

    /**
     * Constructor.
     * @param name the short name of the ScriptEngine implementation,
     *             e.g., "scala", "kotlin".
     */
    public ScriptKernel(String name) {
        this.name = name;
        restart();
    }

    @Override
    public boolean eval(String script, List<Object> values) {
        boolean success;
        // Scala/Kotlin ignore context writers, so we have to
        // redirect System.out and System.err to the console.
        PrintStream out = System.out;
        PrintStream err = System.err;
        System.setOut(printer);
        System.setErr(printer);
        try {
            Object result = engine.eval(script);
            if (result != null) {
                values.add(result);
                process(List.of(result));
            }
            success = true;
        } catch (ScriptException ex) {
            var cause = ex.getCause();
            if (cause != null) {
                writer.append(cause.getClass().getTypeName())
                      .append(": ")
                      .append(cause.getMessage() == null ? "" : ex.getMessage())
                      .append('\n');
                for (StackTraceElement ste : cause.getStackTrace()) {
                    writer.append("  at ")
                          .append(ste.toString())
                          .append('\n');
                }
            } else {
                writer.println("Failed to execute script: " + ex.getMessage());
            }
            success = false;
        }

        // Restore System.out and System.err
        System.setOut(out);
        System.setErr(err);
        return success;
    }

    /**
     * Processes the values returned from the code execution.
     * @param values the values returned from the code execution.
     */
    @Override
    public void process(List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value != null) {
                console.getOutputArea().println(
                        String.format("$result%d: %s = %s", i, value.getClass().getName(), value));
            }
        }
    }

    @Override
    public List<Variable> variables() {
        List<Variable> variables = new ArrayList<>();
        for (var entry : engine.getBindings(ScriptContext.ENGINE_SCOPE).entrySet()) {
            variables.add(new Variable(entry.getKey(), entry.getValue().getClass().getTypeName()));
        }
        return variables;
    }

    /**
     * Retrieves the value of a named variable.
     * @param name the variable name.
     * @return the variable value.
     */
    public Object get(String name) {
        return engine.get(name);
    }

    @Override
    public synchronized void close() {
        if (engine != null) {
            engine = null;
        }
    }

    @Override
    public synchronized void restart() {
        close();
        // Sets System.out/err before creating the engine so that
        // Scala Console picks it up during engine initialization.
        // Otherwise, println won't be redirected properly.
        PrintStream out = System.out;
        PrintStream err = System.err;
        System.setOut(printer);
        System.setErr(printer);

        ScriptEngineManager manager = new ScriptEngineManager();
        engine = manager.getEngineByName(name);
        ScriptContext context = engine.getContext();
        context.setWriter(writer);
        context.setErrorWriter(writer);

        // Restore System.out and System.err
        System.setOut(out);
        System.setErr(err);
    }

    @Override
    public void reset() {
        engine.getBindings(ScriptContext.ENGINE_SCOPE).clear();
    }

    @Override
    public void stop() {
        logger.warn("Script engine does not support stopping execution. Restarting the kernel instead.");
    }
}
