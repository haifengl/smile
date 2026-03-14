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
import smile.agent.Command;
import smile.agent.Skill;
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
    private static final String HISTORY_FILE = "conversation.jsonl";
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
    private final List<Tool.Spec> tools = new ArrayList<>();
    /** The MCP tools available for LLM. */
    private final List<McpSchema.Tool> mcp = new ArrayList<>();
    /** The custom slash commands. */
    private final List<Command> commands = new ArrayList<>();
    /** The skills available to agent. */
    private final List<Skill> skills = new ArrayList<>();
    /** The optional system reminder to keep the AI focused, enforce safety, and guide tool usage. */
    private String reminder;
    /** Prompt repetition improves non-reasoning LLMs. */
    private boolean repetition = true;
    /** Whether the plan mode is active. */
    private boolean planMode = false;
    /** The plan file path. */
    private Path planFile = null;
    /** The plan content. */
    private String plan = null;

    /**
     * Constructor.
     * @param path the directory path for conversation sessions.
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
                logger.error("Failed to create folder of conversation session", ex);
            }
        } else {
            logger.warn("Conversation session folder already exists: {}", id);
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
     * Returns the conversation session directory.
     * @return the conversation session directory.
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the agent working directory.
     * @return the agent working directory.
     */
    public Path awd() {
        return path.resolve("../..").normalize();
    }

    /**
     * Returns the current working directory, i.e. project root directory.
     * @return the current working directory, i.e. project root directory.
     */
    public Path cwd() {
        return path.resolve("../../../..").normalize();
    }

    /**
     * Returns the JSON object mapper.
     * @return the JSON object mapper.
     */
    public static ObjectMapper mapper() {
        return mapper;
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
     * Returns the custom slash commands.
     * @return the custom slash commands.
     */
    public List<Command> commands() {
        return commands;
    }

    /**
     * Adds the custom slash commands.
     * @param commands the custom slash commands.
     * @return this object.
     */
    public Conversation addCommands(List<Command> commands) {
        this.commands.addAll(commands);
        return this;
    }

    /**
     * Returns the skills available to agent.
     * @return the skills available to agent.
     */
    public List<Skill> skills() {
        return skills;
    }

    /**
     * Adds the skills available to agent.
     * @param skills the skills available to agent.
     * @return this object.
     */
    public Conversation addSkills(List<Skill> skills) {
        this.skills.addAll(skills);
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
     * Returns whether the plan mode is active.
     * @return true if the plan mode is active, false otherwise.
     */
    public boolean planMode() {
        return planMode;
    }

    /**
     * Returns the plan file path. The plan file will be generated when the
     * plan mode is active, which is used to save the plan presented by AI.
     * After exiting the plan model, the plan file still exists on disk for
     * AI to reference.
     * @return the plan file path if it exists, empty otherwise.
     */
    public Optional<Path> planFile() {
        return Optional.ofNullable(planFile);
    }

    /**
     * Enters the plan mode. When the plan mode is active, AI will
     * only answer the user's query and present a plan without taking any
     * actions, which can be useful for complex tasks that require careful
     * planning before execution.
     *
     * @param reason a brief explanation for entering plan mode, such as
     * describing the bug to be fixed. Its kebab case will be used as plan
     * file name. If null, the file name default to "PLAN.md".
     * @return the plan file path where the plan will be saved.
     */
    public Path enterPlanMode(String reason) throws IOException {
        planMode = true;
        String filename = Strings.isNullOrBlank(reason) ? "PLAN.md" : (Strings.kebab(reason) + ".md");
        planFile = awd().resolve("plans", filename).normalize();
        Files.createDirectories(planFile.getParent());
        return planFile;
    }

    /**
     * Exits the plan mode.
     * @param plan the final plan content.
     */
    public void exitPlanMode(String plan) {
        planMode = false;
        this.plan = plan;
    }

    /**
     * Enriches a prompt with system reminder, prompt repetition, etc.
     *
     * @param prompt the original user prompt.
     * @return the hydrated prompt.
     */
    public String hydrate(String prompt) {
        if (repetition) prompt += prompt;

        String systemReminder = planMode ? planModeSystemReinder() : planFileSystemReminder();

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

    private String planFileSystemReminder() {
        if (Strings.isNullOrBlank(plan) || planFile == null) return "";
        return String.format("""
A plan file exists from plan mode at: %s

Plan contents:

%s

If this plan is relevant to the current work and not already complete, continue working on it.
""", planFile, plan);
    }

    private String planModeSystemReinder() {
        if (planFile == null) return "";

        String planFileInfo = Files.exists(planFile)
                ? String.format("A plan file already exists at %s. You can read it and make incremental edits using the Edit tool.", planFile)
                : String.format("No plan file exists yet. You should create your plan at %s using the Write tool.", planFile);

        return String.format("""
Plan mode is active. The user indicated that they do not want you to execute yet -- you MUST NOT make any edits (with the exception of the plan file mentioned below), run any non-readonly tools (including changing configs or making commits), or otherwise make any changes to the system. This supercedes any other instructions you have received.

## Plan File Info:
%s

## Iterative Planning Workflow

You are pair-planning with the user. Explore the code to build context, ask the user questions when you hit decisions you can't make alone, and write your findings into the plan file as you go. The plan file (above) is the ONLY file you may edit — it starts as a rough skeleton and gradually becomes the final plan.

### The Loop

Repeat this cycle until the plan is complete:

1. **Explore** — Use Read, Grep, Glob tools to read code. Look for existing functions, utilities, and patterns to reuse.
2. **Update the plan file** — After each discovery, immediately capture what you learned. Don't wait until the end.
3. **Ask the user** — When you hit an ambiguity or decision you can't resolve from code alone, use AskUserQuestion. Then go back to step 1.

### First Turn

Start by quickly scanning a few key files to form an initial understanding of the task scope. Then write a skeleton plan (headers and rough notes) and ask the user your first round of questions. Don't explore exhaustively before engaging the user.

### Asking Good Questions

- Never ask what you could find out by reading the code
- Batch related questions together (use multi-question AskUserQuestion calls)
- Focus on things only the user can answer: requirements, preferences, tradeoffs, edge case priorities
- Scale depth to the task — a vague feature request needs many rounds; a focused bug fix may need one or none

### Plan File Structure
Your plan file should be divided into clear sections using markdown headers, based on the request. Fill out these sections as you go.
- Begin with a **Context** section: explain why this change is being made — the problem or need it addresses, what prompted it, and the intended outcome
- Include only your recommended approach, not all alternatives
- Ensure that the plan file is concise enough to scan quickly, but detailed enough to execute effectively
- Include the paths of critical files to be modified
- Reference existing functions and utilities you found that should be reused, with their file paths
- Include a verification section describing how to test the changes end-to-end (run the code, use MCP tools, run tests)

### When to Converge

Your plan is ready when you've addressed all ambiguities and it covers: what to change, which files to modify, what existing code to reuse (with file paths), and how to verify the changes. Call ExitPlanMode when the plan is ready for approval.

### Ending Your Turn

Your turn should only end by either:
- Using AskUserQuestion to gather more information
- Calling ExitPlanMode when the plan is ready for approval

**Important:** Use ExitPlanMode to request plan approval. Do NOT ask about plan approval via text or AskUserQuestion.
""", planFileInfo);
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
