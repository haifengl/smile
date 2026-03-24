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

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Script execution engine.
 *
 * @author Haifeng Li
 */
public class ScriptKernel extends Kernel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScriptKernel.class);
    private final PrintWriter writer = new PrintWriter(console, true, StandardCharsets.UTF_8);
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final String name;
    private ScriptEngine engine;

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
    public boolean eval(String script, List<Object> values, Consumer<Object> eventListener) {
        try {
            Object result = engine.eval(script);
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) result;
                for (var entry : map.entrySet()) {
                    engine.put(entry.getKey(), entry.getValue());
                }
            }

            if (result != null) {
                values.add(result);
                eventListener.accept(result);
            }
            return true;
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
            return false;
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
        ScriptEngineManager manager = new ScriptEngineManager();
        engine = manager.getEngineByName(name);
        ScriptContext context = engine.getContext();
        context.setWriter(writer);
        context.setErrorWriter(writer);
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
