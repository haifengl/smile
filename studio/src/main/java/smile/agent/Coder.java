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
import java.util.Optional;
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
        // low temperature for more predictable, focused, and deterministic code
        params().setProperty(LLM.TEMPERATURE, "0.2");
        params().setProperty(LLM.MAX_OUTPUT_TOKENS, "2048");
    }

    /**
     * Asynchronously completes a line of code.
     * @param prefix the beginning of the line to complete.
     * @param before the code context before the line.
     * @param after the code context after the line.
     * @return a future of full Line completion.
     */
    public CompletableFuture<String> complete(String prefix, String before, String after) {
        var command = command("complete");
        return command.map(cmd -> {
            var prompt = cmd.content
                    .replace("{{prefix}}", prefix)
                    .replace("{{before}}", before)
                    .replace("{{after}}", after);

            // stop at the end of line
            params().setProperty(LLM.STOP, "\n");
            var future = response(prompt);
            params().remove(LLM.STOP);
            return future;
        }
        ).orElse(CompletableFuture.completedFuture("Code completion prompt cannot be found. Check your smile/agents/java-coder/complete.md file."));
    }

    /**
     * Asynchronously generates code snippet in a streaming way.
     * @param task the task prompt.
     * @param before the code context before the line.
     * @param after the code context after the line.
     * @param handler the stream response handler.
     */
    public void generate(String task, String before, String after,
                         StreamResponseHandler handler) {
        var command = command("generate");
        if (command.isPresent()) {
            var prompt = command.get().content
                    .replace("{{args}}", task)
                    .replace("{{before}}", before)
                    .replace("{{after}}", after);

            stream(prompt, handler);
        } else {
            var ex = new IllegalStateException("Code generation prompt cannot be found. Check your smile/agents/java-coder/generate.md file.");
            handler.onComplete(Optional.of(ex));
        }
    }
}
