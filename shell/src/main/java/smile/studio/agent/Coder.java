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
package smile.studio.agent;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The coding agent.
 *
 * @author Haifeng Li
 */
public class Coder {
    /** The LLM service. */
    final LLM llm;

    /**
     * Constructor.
     * @param llm the LLM service.
     */
    public Coder(LLM llm) {
        this.llm = llm;
    }

    /**
     * Asynchronously completes the current line of code.
     * @param start the start of current line.
     * @param context the previous lines of code.
     * @return a future of full Line completion.
     */
    public CompletableFuture<String> complete(String start, String context) {
        var input = Prompt.completeCode(start, context);
        return llm.complete(input);
    }

    /**
     * Asynchronously generates code based on prompt in a streaming way.
     * @param task the user prompt of task.
     * @param context the selected or previous lines of code.
     * @param consumer the consumer of completion chunks.
     * @param handler the exception handler.
     */
    public void generate(String task, String context, Consumer<String> consumer, Function<Throwable, ? extends Void> handler) {
        var input = Prompt.generateCode(task, context);
        llm.complete(input, consumer, handler);
    }

    /**
     * Returns a coder agent instance.
     * @return a coder agent instance.
     */
    public static Optional<Coder> getInstance() {
        var model = LLM.getInstance();
        model.ifPresent(llm -> llm.context().setProperty("instructions", Prompt.smileDeveloper()));
        return model.map(Coder::new);
    }
}
