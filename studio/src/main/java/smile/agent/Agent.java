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
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import smile.llm.Conversation;
import smile.llm.Message;
import smile.llm.client.LLM;
import smile.llm.client.StreamResponseHandler;

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
    /** The conversation session. */
    private final Conversation conversation;
    /** The number of recent conversations to keep in context. */
    private int window = 5;

    /**
     * Constructor.
     * @param llm the supplier of LLM service.
     * @param conversation the conversation session.
     * @param context the project-specific context.
     * @param user the user context for user rules, skills, etc.
     * @param global the global context for system instructions, skills, tools, etc.
     */
    public Agent(Supplier<LLM> llm, Conversation conversation,
                 Context context, Context user, Context global) {
        this.llm = llm;
        this.conversation = conversation;
        this.context = context;
        this.user = user;
        this.global = global;
        params.setProperty(LLM.SYSTEM_PROMPT, system());
    }

    /**
     * Constructor.
     * @param llm the supplier of LLM service.
     * @param conversation the conversation session.
     * @param context the project-specific context.
     */
    public Agent(Supplier<LLM> llm, Conversation conversation, Context context) {
        this(llm, conversation, context, null, null);
    }

    /**
     * Constructor.
     * @param llm the supplier of LLM service.
     * @param session the directory path for conversations.
     * @param context the directory path for agent context.
     */
    public Agent(Supplier<LLM> llm, Path session, Path context) {
        this(llm, new Conversation(session), new Context(context));
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
     * Saves the project instructions.
     * @param instructions the project instructions.
     */
    public void initMemory(String instructions) throws IOException {
        Path path = context.path().resolve(Context.SMILE_MD);
        var metadata = Memory.mapper.createObjectNode();
        metadata.put("name", context.path().getFileName().toString().toLowerCase().replaceAll("[\\s_]+", "-"));
        metadata.put("description", "Project instruction manual.");
        Rule rule = new Rule(instructions, metadata, path);
        rule.save();
        context.setInstructions(rule);
    }

    /**
     * Saves the project instructions.
     * @param instructions the project instructions.
     */
    public void addMemory(String instructions) throws IOException {
        Memory memory = context.getInstructions();
        var content = memory.content() + "\n\n" + instructions;
        memory = new Rule(content, memory.metadata(), memory.path());
        memory.save();
        context.setInstructions(memory);
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
     * Returns the project instructions.
     * @return the project instructions.
     */
    public String instructions() {
        return context.getInstructions().content();
    }

    /**
     * Returns the custom commands.
     * @return the custom commands.
     */
    public List<Command> commands() {
        List<Command> commands = new ArrayList<>(context.commands());
        if (user != null) {
            commands.addAll(user.commands());
        }
        if (global != null) {
            commands.addAll(global.commands());
        }
        return commands;
    }

    /**
     * Returns the rules.
     * @return the rules.
     */
    public List<Rule> rules() {
        List<Rule> rules = new ArrayList<>(context.rules());
        if (user != null) {
            rules.addAll(user.rules());
        }
        if (global != null) {
            rules.addAll(global.rules());
        }
        return rules;
    }

    /**
     * Returns the skills.
     * @return the skills.
     */
    public List<Skill> skills() {
        List<Skill> skills = new ArrayList<>(context.skills());
        if (user != null) {
            skills.addAll(user.skills());
        }
        if (global != null) {
            skills.addAll(global.skills());
        }
        return skills;
    }

    /**
     * Clears the in-memory conversation session.
     */
    public void clear() {
        conversation.clear();
    }

    /**
     * Returns the constitution, which will be injected into the system prompt.
     * @return the constitution.
     */
    public String constitution() {
        return "";
    }

    /**
     * Returns the system reminder, which will be injected into the user message.
     * @return the system reminder.
     */
    public String reminder() {
        return "";
    }

    /**
     * Returns the system prompt.
     * @return the system prompt.
     */
    public String system() {
        String prompt = constitution();
        if (global != null) {
            prompt += "\n\n" + global.getInstructions().content();
        }
        if (user != null) {
            prompt += "\n\n" + user.getInstructions().content();
        }
        prompt += "\n\n" + context.getInstructions().content();

        var rules = rules();
        if (!rules.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (var rule : rules) {
                sb.append("\n\n");
                sb.append(rule.content());
            }
            prompt += sb.toString();
        }

        var skills = skills();
        if (!skills.isEmpty()) {
            StringBuilder sb = new StringBuilder("\n\nHere are the skills you can use:\n");
            sb.append("<skills>\n");
            for (var skill : skills) {
                sb.append(String.format("""
                        <skill name="%s">
                        %s
                        </skill>
                        """, skill.name(), skill.description()));
            }
            sb.append("</skills>\n");
            prompt += sb.toString();
        }

        prompt += "\n\n" + String.format("""
                Here is useful information about the environment you are running in:
                <env>
                Working directory: %s
                Is directory a git repo: %s
                Platform: %s
                OS Version: %s
                Current time: %s
                Time zone: %s
                </env>
                """,
                System.getProperty("user.dir"),
                Files.exists(Path.of(System.getProperty("user.dir"), ".git")) ? "Yes" : "No",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                date(),
                ZoneId.systemDefault());

        return prompt;
    }

    /** Returns the current date and time in ISO-8601 format, truncated to milliseconds. */
    private String date() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString() + " is the date. ";
    }

    /**
     * Asynchronously response to a user prompt.
     * @param prompt the user prompt of task.
     * @return a future of response.
     */
    public CompletableFuture<String> response(String prompt) {
        var history = conversation.getLast(window);
        conversation.add(Message.user(prompt));

        return llm.get().complete(prompt, history, params)
                .handle((response, ex) -> {
                    var message = Optional.ofNullable(ex).map(t -> Message.error(t.getMessage()))
                            .orElse(Message.assistant(response));
                    conversation.add(message);
                    return response;
                });
    }

    /**
     * Asynchronously response to a user prompt in a streaming way.
     * @param command the command name associated with the prompt, may be null.
     * @param prompt the user prompt of task.
     * @param handler the stream response handler.
     */
    public void stream(String command, String prompt, StreamResponseHandler handler) {
        boolean compact = "compact".equals(command);
        var history = conversation.getLast(compact ? 20 : window);
        conversation.add(Message.user(prompt));

        StringBuilder sb = new StringBuilder();
        var accumulator = new StreamResponseHandler() {
            @Override
            public void onNext(String chunk) {
                sb.append(chunk);
                handler.onNext(chunk);
            }

            @Override
            public void onComplete(Optional<Throwable> ex) {
                var response = sb.toString();
                logger.debug("assistant: {}", response);

                var message = ex.map(t -> Message.error(t.getMessage()))
                        .orElse(Message.assistant(response));
                conversation.add(message);

                if (compact) {
                    conversation.setSummary(response);
                }
                handler.onComplete(ex);
            }
        };

        String message = reminder();
        var summary = conversation.getSummary();
        if (!compact && !summary.isBlank()) {
            message += String.format("""
                Here is the analysis and summary of previous conversations:
                %s
                """, summary);
        }
        message += "\n\n" + prompt;
        logger.debug("user: {}", message);
        llm.get().complete(message, history, params, accumulator);
    }
}
