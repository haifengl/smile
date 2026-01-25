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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.kernel;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Script execution engine.
 *
 * @author Haifeng Li
 */
public class ScriptRunner extends Runner {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScriptRunner.class);
    private final PrintWriter writer = new PrintWriter(console, true, StandardCharsets.UTF_8);
    private final ScriptEngine engine;

    /**
     * Constructor.
     * @param name the short name of the ScriptEngine implementation.
     */
    public ScriptRunner(String name) {
        ScriptEngineManager manager = new ScriptEngineManager();
        engine = manager.getEngineByName(name);
        ScriptContext context = engine.getContext();
        context.setWriter(writer);
        context.setErrorWriter(writer);
    }

    /**
     * Executes the specified script.
     * @param script the source code to be executed.
     * @return the value returned from the execution of the script.
     */
    public Object eval(String script) {
        try {
            return engine.eval(script);
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
        }
        return null;
    }

    /**
     * Returns the set of named variables.
     * @return the set of named variables.
     */
    public Set<String> variables() {
        return engine.getBindings(ScriptContext.ENGINE_SCOPE).keySet();
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
    public void stop() {
        throw new UnsupportedOperationException();
    }
}
