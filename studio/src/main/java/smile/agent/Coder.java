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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import smile.llm.client.LLM;

/**
 * The coding assistant agent.
 *
 * @author Haifeng Li
 */
public class Coder {
    /**
     * The instructions (developer prompt) for coding with SMILE.
     */
    private static final String developer = """
            You are a highly skilled Java programming assistant.
            Your task is to complete code snippets, adhering to
            the provided context and best practices. Ensure the
            completed code is syntactically correct and logically
            sound.""";

    /** The LLM service. */
    private final LLM llm;

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
        String template = """
            Complete the next line of Java code based on the provided context.
            Returns the whole line of generated code, without explanations or markdown annotations.%n%n
            Context:%n%s%n%n
            Current line start: %s""";

        var prompt = String.format(template, context, start);
        return llm.complete(prompt);
    }

    /**
     * Asynchronously generates code based on prompt in a streaming way.
     * @param task the user prompt of task.
     * @param context the selected or previous lines of code.
     * @param consumer the consumer of completion chunks.
     * @param handler the exception handler.
     */
    public void generate(String task, String context, Consumer<String> consumer, Function<Throwable, ? extends Void> handler) {
        String template = """
            Generate Java code based on the provided context and task.
            Returns the generated code only, without explanations or markdown annotations.%n%n
            Context:%n%s%n%n
            Task:%n%s%n%n""";

        var prompt = String.format(template, context, task);
        llm.complete(prompt, consumer, handler);
    }

    /**
     * Returns a coder agent instance.
     * @return a coder agent instance.
     */
    public static Optional<Coder> getInstance() {
        var model = LLM.getInstance();
        model.ifPresent(llm -> llm.options().setProperty(LLM.SYSTEM_PROMPT, developer));
        return model.map(Coder::new);
    }
}
