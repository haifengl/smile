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
package smile.agent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import smile.llm.client.LLM;
import smile.llm.client.StreamResponseHandler;

/**
 * The coding assistant agent.
 *
 * @author Haifeng Li
 */
public class Coder extends Agent {
    /**
     * Constructor.
     * @param llm the supplier of LLM service.
     * @param history the directory path for conversation history.
     * @param context the directory path for agent context.
     */
    public Coder(Supplier<LLM> llm, Path history, Path context) {
        super(llm, history, context);
        // no window for code generation and completion
        setWindow(0);
        // low temperature for more predictable, focused, and deterministic code
        params().setProperty(LLM.TEMPERATURE, "0.2");
        params().setProperty(LLM.MAX_OUTPUT_TOKENS, "2048");
    }

    /**
     * Asynchronously completes the current line of code.
     * @param start the start of current line.
     * @param context the previous lines of code.
     * @return a future of full Line completion.
     */
    public CompletableFuture<String> complete(String start, String context) {
        String template = """
            Complete the next line of code based on the provided context.
            Context:%n%s%n%n
            Current line start: %s""";

        var prompt = String.format(template, context, start);
        // stop at the end of line
        params().setProperty(LLM.STOP, "\n");
        var future = response(prompt);
        params().remove(LLM.STOP);
        return future;
    }

    /**
     * Asynchronously generates code based on prompt in a streaming way.
     * @param task the user prompt of task.
     * @param context the selected or previous lines of code.
     * @param handler the stream response handler.
     */
    public void generate(String task, String context, StreamResponseHandler handler) {
        String template = """
            Generate code based on the provided context and task.
            Context:%n%s%n%n
            Task:%n%s%n%n""";

        var prompt = String.format(template, context, task);
        stream(prompt, handler);
    }
}
