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
package smile.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import io.modelcontextprotocol.spec.McpSchema;
import smile.llm.tool.Tool;
import smile.util.Strings;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The conversation session.
 *
 * @author Haifeng Li
 */
public class Conversation {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Conversation.class);
    /** The file name for conversation history. */
    private static final String HISTORY_FILE = "history.jsonl";
    /** The file name of conversation summary. */
    private static final String MEMORY_MD = "MEMORY.md";
    /** The JSON object mapper with sneak case and ignoring null fields. */
    private static final ObjectMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
            .build();

    /** The parameters for LLM. */
    private final Properties params = new Properties();
    /** The conversation history. */
    private final List<Message> messages = new ArrayList<>();
    /** The directory path for conversation history and summary. */
    private final Path path;
    /** The built-in tools available for LLM. */
    private List<Tool.Spec> tools = new ArrayList<>();
    /** The MCP tools available for LLM. */
    private List<McpSchema.Tool> mcp = new ArrayList<>();
    /** The optional system reminder to keep the AI focused, enforce safety, and guide tool usage. */
    private String reminder;
    /** Prompt repetition improves non-reasoning LLMs. */
    private boolean repetition = true;
    /** The plan file path if in the plan mode. */
    private Path planFile = null;

    /**
     * Constructor. New messages will be saved to history file.
     * @param path the directory path for conversations.
     */
    public Conversation(Path path) {
        var formatter = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");
        String id = formatter.format(LocalDateTime.now());
        this.path = path.resolve(id);
        if (!Files.exists(this.path)) {
            // Creates all parent directories
            try {
                Files.createDirectories(this.path);
            } catch (IOException ex) {
                logger.error("Failed to create folder of conversation history", ex);
            }
        }
    }

    /**
     * Returns the parameters for LLM.
     * @return the parameters for LLM.
     */
    public Properties params() {
        return params;
    }

    /**
     * Returns the agent working directory.
     * @return the agent working directory.
     */
    public Path path() {
        return path.resolve("../..").normalize();
    }

    /**
     * Returns the MCP services available for LLM.
     * @return the MCP services available for LLM.
     */
    public List<McpSchema.Tool> mcp() {
        return mcp;
    }

    /**
     * Adds the MCP services available for LLM.
     * @param mcp the MCP services available for LLM.
     * @return this object.
     */
    public Conversation addMcp(List<McpSchema.Tool> mcp) {
        this.mcp.addAll(mcp);
        return this;
    }

    /**
     * Returns the built-in tools available for LLM.
     * @return the built-in tools available for LLM.
     */
    public List<Tool.Spec> tools() {
        return tools;
    }

    /**
     * Adds the built-in tools available for LLM.
     * @param tools the built-in tools available for LLM.
     * @return this object.
     */
    public Conversation addTools(List<Tool.Spec> tools) {
        this.tools.addAll(tools);
        return this;
    }

    /**
     * Returns the optional system reminder.
     *
     * @return the system reminder.
     */
     public Optional<String> reminder() {
        return Optional.ofNullable(reminder);
    }

    /**
     * Sets the system reminder to keep the AI focused, enforce safety,
     * and guide tool usage, which will be injected into the user message.
     * These injected messages appear before user messages to prevent drift.
     *
     * @return this object.
     */
    public Conversation withReminder(String reminder) {
        this.reminder = reminder;
        return this;
    }

   /**
    * Returns whether prompt repetition is enabled.
    *
    * @return true if prompt repetition is enabled, false otherwise.
    */
    public boolean repetition() {
        return repetition;
    }

    /**
     * Enables or disables prompt repetition. When enabled, the user prompt
     * will be repeated in the hydrated prompt, which can improve non-reasoning LLMs
     *
     * @param repetition true to enable prompt repetition, false to disable.
     * @return this object.
     */
    public Conversation withRepetition(boolean repetition) {
        this.repetition = repetition;
        return this;
    }

    /**
     * Returns the plan file path if in the plan mode.
     * @return the plan file path if in the plan mode, empty otherwise.
     */
    public Optional<Path> planFile() {
        return Optional.ofNullable(planFile);
    }

    /**
     * Sets whether plan mode is active. When plan mode is active, AI will
     * only answer the user's query and present a plan without taking any
     * actions, which can be useful for complex tasks that require careful
     * planning before execution.
     *
     * @param name the plan file name. If null, it will default to "PLAN.md".
     *             The file will be saved in the "plans" subdirectory of the
     *             conversation path.
     */
    public void planMode(String name) {
        String file = Strings.isNullOrBlank(name) ? "PLAN.md" : (Strings.kebab(name) + ".md");
        planFile = path().resolve("plans", name).normalize();
    }

    /**
     * Saves a plan to the disk and exits the plan mode.
     * @param plan the plan content to save.
     * @return the plan file path.
     */
    public Path exitPlanMode(String plan) throws IOException {
        var file = planFile;
        if (planFile != null) {
            Files.createDirectories(planFile.getParent());
            Files.writeString(planFile, plan);
            planFile = null;
        }
        return file;
    }

    /**
     * Exits the plan mode.
     */
    public void exitPlanMode() {
        planFile = null;
    }

    /**
     * Enriches a prompt with system reminder, prompt repetition, etc.
     *
     * @param prompt the original user prompt.
     * @return the hydrated prompt.
     */
    public String hydrate(String prompt) {
        if (repetition) prompt += prompt;

        String systemReminder = planFile == null ? "" : """
Plan mode is active. The user indicated that they do not want you to execute yet -- you MUST NOT make any edits, run any non-readonly tools (including changing configs or making commits), or otherwise make any changes to the system. This supersedes any other instructions you have received (for example, to make edits). Instead, you should:
1. Answer the user's query comprehensively
2. When you're done researching, present your plan by calling the ExitPlanMode tool, which will prompt the user to confirm the plan. Do NOT make any file changes or run any tools that modify the system state in any way until the user has confirmed the plan.
""";

        if (reminder != null) {
            if (!systemReminder.isBlank()) systemReminder += "\n\n";
            systemReminder += reminder;
        }

        if (!systemReminder.isBlank()) {
            prompt = String.format("""
<system-reminder>
%s
</system-reminder>
%s
""", systemReminder, prompt);
        }

        return prompt;
    }

    /**
     * Returns the conversation messages.
     * @return the conversation messages.
     */
    public List<Message> messages() {
        return messages;
    }

    /**
     * Adds a message to the conversation session and saves it to the file.
     * @param message the message to add.
     */
    public void add(Message message) {
        messages.add(message);
        try {
            String jsonLine = mapper.writeValueAsString(message) + System.lineSeparator();
            Files.writeString(path.resolve(HISTORY_FILE), jsonLine, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (Exception ex) {
            logger.error("Failed to save conversation", ex);
        }
    }

    /**
     * Clears the in-memory conversation session.
     */
    public void clear() {
        messages.clear();
    }

    /**
     * Compacts the conversation with a summary to set context space.
     * @param summary the summary of conversation.
     */
    public void compact(String summary) {
        try {
            clear();
            add(Message.assistant(summary));
            Files.writeString(path.resolve(MEMORY_MD), summary);
        } catch (IOException ex) {
            logger.error("Failed to save conversation summary", ex);
        }
    }
}
