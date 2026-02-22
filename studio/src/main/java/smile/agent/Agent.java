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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import smile.llm.client.LLM;
import smile.llm.client.Message;

/**
 * An LLM agent is an advanced AI system using an LLM as its brain
 * to autonomously reason, plan, and execute complex, multi-step tasks
 * by interacting with tools and data, going beyond single-prompt
 * responses to maintain context and achieve goals.
 *
 * @author Haifeng Li
 */
public class Agent {
    /** The supplier of LLM service. */
    private final Supplier<LLM> llm;
    /** Global context for system instructions, skills, tools, etc. */
    private final Context global;
    /** User context for user preferences, history, etc. */
    private final Context user;
    /** The project-specific context. */
    private final Context context;
    /** The conversation history. */
    private final List<Message> conversations = new ArrayList<>();
    /** The parameters for LLM inference. */
    private final Properties params = new Properties();

    /**
     * Constructor.
     * @param llm the supplier of LLM service.
     * @param context the project-specific context.
     * @param user the user context for user preferences, history, etc.
     * @param global the global context for system instructions, skills, tools, etc.
     */
    public Agent(Supplier<LLM> llm, Context context, Context user, Context global) {
        this.llm = llm;
        this.context = context;
        this.user = user;
        this.global = global;
        params.setProperty(LLM.SYSTEM_PROMPT, system());
    }

    /**
     * Constructor.
     * @param llm the supplier of LLM service.
     * @param context the project-specific context.
     */
    public Agent(Supplier<LLM> llm, Context context) {
        this(llm, context, null, null);
    }

    /**
     * Constructor.
     * @param llm the supplier of LLM service.
     * @param path the directory path for agent context.
     */
    public Agent(Supplier<LLM> llm, Path path) {
        this(llm, new Context(path));
    }

    /**
     * Returns the LLM service.
     * @return the LLM service.
     */
    public LLM llm() {
        return llm.get();
    }

    /**
     * Returns the parameters for LLM inference.
     * @return the parameters for LLM inference.
     */
    public Properties params() {
        return params;
    }

    /**
     * Returns the system prompt.
     * @return the system prompt.
     */
    public String system() {
        String prompt = context.instructions().content();
        if (user != null) {
            prompt = user.instructions().content() + "\n\n" + prompt;
        }
        if (global != null) {
            prompt = global.instructions().content() + "\n\n" + prompt;
        }
        return prompt;
    }

    /**
     * Asynchronously response.
     * @param prompt the user prompt of task.
     * @return a future of full Line completion.
     */
    public CompletableFuture<String> response(String prompt) {
        return llm.get().complete(prompt, params);
    }

    /**
     * Asynchronously response in a streaming way.
     * @param prompt the user prompt of task.
     * @param consumer the consumer of completion chunks.
     * @param handler the exception handler.
     */
    public void stream(String prompt, Consumer<String> consumer, Function<Throwable, ? extends Void> handler) {
        llm.get().complete(prompt, params, consumer, handler);
    }
}
