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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import smile.llm.client.LLM;
import smile.llm.client.Message;
import smile.llm.client.StreamResponseHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An LLM agent is an advanced AI system using an LLM as its brain
 * to autonomously reason, plan, and execute complex, multi-step tasks
 * by interacting with tools and data, going beyond single-prompt
 * responses to maintain context and achieve goals.
 *
 * @author Haifeng Li
 */
public class Agent {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Agent.class);
    /** The supplier of LLM service. */
    private final Supplier<LLM> llm;
    /** Global context for system instructions, skills, tools, etc. */
    private final Context global;
    /** User context for user preferences, history, etc. */
    private final Context user;
    /** The project-specific context. */
    private final Context context;
    /** The parameters for LLM inference. */
    private final Properties params = new Properties();
    /** The JSON object mapper. */
    private final ObjectMapper mapper;
    /** The conversation history. */
    private final List<Message> conversations = new ArrayList<>();
    /** The file path for conversation history. */
    private Path history;
    /** The number of recent conversations to keep in context. */
    private int window = 5;

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
        // Ignore null fields
        mapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
                .build();
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
     * Returns the number of recent conversations to keep in context.
     * @return the number of recent conversations to keep in context.
     */
    public int getWindow() {
        return window;
    }

    /**
     * Sets the number of recent conversations to keep in context.
     * @param size the number of recent conversations to keep in context.
     */
    public void setWindow(int size) {
        window = size;
    }

    /**
     * Loads the conversation history from the file if it exists.
     * New messages will be saved to the file after each conversation.
     * @param path the file path for conversation history.
     */
    public void loadHistory(Path path) {
        this.history = path;
        if (!Files.exists(path)) {
            // Creates all parent directories
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException ex) {
                logger.error("Failed to create folder of conversation history", ex);
            }
        } else {
            try (Stream<String> lines = Files.lines(path)) {
                lines.forEach(line -> {
                    if (!line.trim().isEmpty()) {
                        Message message = mapper.readValue(line, Message.class);
                        conversations.add(message);
                    }
                });
            } catch (IOException ex) {
                logger.error("Failed to load conversation history", ex);
            }
        }
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
     * Adds a message to the conversation history and saves it to the file
     * if history is enabled.
     * @param message the message to add.
     */
    private void addConversation(Message message) {
        conversations.add(message);
        if (history != null) {
            try {
                String jsonLine = mapper.writeValueAsString(message) + System.lineSeparator();
                Files.writeString(history, jsonLine, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            } catch (Exception ex) {
                logger.error("Failed to save conversation", ex);
            }
        }
    }

    /**
     * Sets the conversation history in the parameters for LLM inference.
     * Only the recent conversations within the window size will be kept.
     */
    private void setHistory() {
        if (window > 0) {
            // keeps only the recent conversations within the window size
            int fromIndex = Math.max(conversations.size() - window, 0);
            params.put(LLM.HISTORY, conversations.subList(fromIndex, conversations.size()));
        }
    }

    /**
     * Asynchronously response.
     * @param prompt the user prompt of task.
     * @return a future of full Line completion.
     */
    public CompletableFuture<String> response(String prompt) {
        setHistory();
        addConversation(new Message(prompt));

        return llm.get().complete(prompt, params)
                .handle((response, ex) -> {
                    String error = Optional.ofNullable(ex).map(Throwable::getMessage).orElse(null);
                    addConversation(new Message(response, error));
                    return response;
                });
    }

    /**
     * Asynchronously response in a streaming way.
     * @param prompt the user prompt of task.
     * @param handler the stream response handler.
     */
    public void stream(String prompt, StreamResponseHandler handler) {
        setHistory();
        addConversation(new Message(prompt));

        StringBuilder sb = new StringBuilder();
        var accumulator = new StreamResponseHandler() {
            @Override
            public void onNext(String chunk) {
                sb.append(chunk);
                handler.onNext(chunk);
            }

            @Override
            public void onComplete(Optional<Throwable> ex) {
                String error = ex.map(Throwable::getMessage).orElse(null);
                addConversation(new Message(sb.toString(), error));
                handler.onComplete(ex);
            }
        };

        llm.get().complete(prompt, params, accumulator);
    }
}
