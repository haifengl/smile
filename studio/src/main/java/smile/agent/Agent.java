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
    /**
     * The supplier of LLM service.
     */
    private final Supplier<LLM> llm;
    /**
     * Global context for system instructions, skills, tools, etc.
     */
    private final Context global;
    /**
     * User context for user preferences, history, etc.
     */
    private final Context user;
    /**
     * The project-specific context.
     */
    private final Context context;
    /**
     * The conversation session.
     */
    private final Conversation conversation;

    /**
     * Constructor.
     * @param name the agent name.
     * @param path the project directory path.
     * @param llm  the supplier of LLM service.
     */
    public Agent(String name, Path path, Supplier<LLM> llm) {
        this.llm = llm;
        this.conversation = new Conversation(path.resolve(".smile", name, "sessions"));
        this.context = new Context(path.resolve(".smile", name));
        this.user = new Context(Path.of(System.getProperty("user.home"), "/agents", name));
        this.global = new Context(Path.of(System.getProperty("smile.home"), "/agents", name));
        conversation.params().setProperty(LLM.SYSTEM_PROMPT, system());
    }

    /**
     * Returns the project context.
     * @return the project context.
     */
    public Context context() {
        return context;
    }

    /** Reloads the agent context from disk. */
    public void refresh() {
        context.refresh();
        if (user != null) {
            user.refresh();
        }
        if (global != null) {
            global.refresh();
        }
    }

    /**
     * Saves the project instructions.
     *
     * @param instructions the project instructions.
     */
    public void initMemory(String instructions) throws IOException {
        context.addInstructions(instructions);
    }

    /**
     * Saves the project instructions.
     *
     * @param instructions the project instructions.
     */
    public void addMemory(String instructions) throws IOException {
        context.addInstructions(instructions);
    }

    /**
     * Returns the LLM service.
     *
     * @return the LLM service.
     */
    public LLM llm() {
        return llm.get();
    }

    /**
     * Returns the project instructions.
     *
     * @return the project instructions.
     */
    public String instructions() {
        return context.instructions();
    }

    /**
     * Returns the conversation session.
     *
     * @return the conversation session.
     */
    public Conversation conversation() {
        return conversation;
    }

    /**
     * Returns the custom commands.
     *
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
     * Finds a command by name.
     *
     * @param name the command name.
     * @return the command if it exists in the context.
     */
    public Optional<Command> command(String name) {
        var command = context.commands().stream()
                .filter(cmd -> cmd.name().equals(name))
                .findFirst();
        if (command.isPresent()) return command;

        if (user != null) {
            command = user.commands().stream()
                    .filter(cmd -> cmd.name().equals(name))
                    .findFirst();
            if (command.isPresent()) return command;
        }

        if (global != null) {
            command = global.commands().stream()
                    .filter(cmd -> cmd.name().equals(name))
                    .findFirst();
        }

        return command;
    }

    /**
     * Returns the rules.
     *
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
     *
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
     * Returns the constitution, which is a set of principles to guide LLMs.
     * Note that the constitution is not meant to be changed by users.
     *
     * @return the constitution.
     */
    public final String constitution() {
        return """
Use the instructions below and the tools available to you to assist the user.

IMPORTANT: Assist with defensive security tasks only. Refuse to create, modify, or improve code that may be used maliciously. Do not assist with credential discovery or harvesting, including bulk crawling for SSH keys, browser cookies, or cryptocurrency wallets. Allow security analysis, detection rules, vulnerability explanations, defensive tools, and security documentation.
IMPORTANT: You must NEVER generate or guess URLs for the user unless you are confident that the URLs are for helping the user with programming. You may use URLs provided by the user in their messages or local files.

## Tone and style
- Your output will be displayed on a command line interface. Your responses should be short and concise. You can use Github-flavored markdown for formatting, and will be rendered in a monospace font using the CommonMark specification.
- Output text to communicate with the user; all text you output outside of tool use is displayed to the user. Only use tools to complete tasks. Never use tools like Bash or code comments as means to communicate with the user during the session.
- NEVER create files unless they're absolutely necessary for achieving your goal. ALWAYS prefer editing an existing file to creating a new one. This includes markdown files.

## Professional objectivity
Prioritize technical accuracy and truthfulness over validating the user's beliefs. Focus on facts and problem-solving, providing direct, objective technical info without any unnecessary superlatives, praise, or emotional validation. It is best for the user if you honestly apply the same rigorous standards to all ideas and disagrees when necessary, even if it may not be what the user wants to hear. Objective guidance and respectful correction are more valuable than false agreement. Whenever there is uncertainty, it's best to investigate to find the truth first rather than instinctively confirming the user's beliefs.

## Task Management
You have access to the TodoWrite tools to help you manage and plan tasks. Use these tools VERY frequently to ensure that you are tracking your tasks and giving the user visibility into your progress.
These tools are also EXTREMELY helpful for planning tasks, and for breaking down larger complex tasks into smaller steps. If you do not use this tool when planning, you may forget to do important tasks - and that is unacceptable.

It is critical that you mark todos as completed as soon as you are done with a task. Do not batch up multiple tasks before marking them as completed.

Examples:

<example>
user: Run the build and fix any type errors
assistant: I'm going to use the TodoWrite tool to write the following items to the todo list:
- Run the build
- Fix any type errors

I'm now going to run the build using Bash.

Looks like I found 10 type errors. I'm going to use the TodoWrite tool to write 10 items to the todo list.

marking the first todo as in_progress

Let me start working on the first item...

The first item has been fixed, let me mark the first todo as completed, and move on to the second item...
..
..
</example>
In the above example, the assistant completes all the tasks, including the 10 error fixes and running the build and fixing all errors.

<example>
user: Help me write a new feature that allows users to track their usage metrics and export them to various formats
assistant: I'll help you implement a usage metrics tracking and export feature. Let me first use the TodoWrite tool to plan this task.
Adding the following todos to the todo list:
1. Research existing metrics tracking in the codebase
2. Design the metrics collection system
3. Implement core metrics tracking functionality
4. Create export functionality for different formats

Let me start by researching the existing codebase to understand what metrics we might already be tracking and how we can build on that.

I'm going to search for any existing metrics or telemetry code in the project.

I've found some existing telemetry code. Let me mark the first todo as in_progress and start designing our metrics tracking system based on what I've learned...

[Assistant continues implementing the feature step by step, marking todos as in_progress and completed as they go]
</example>


## Tool usage policy
- When doing file search, prefer to use the Task tool in order to reduce context usage.
- You should proactively use the Task tool with specialized agents when the task at hand matches the agent's description.
- When WebFetch returns a message about a redirect to a different host, you should immediately make a new WebFetch request with the redirect URL provided in the response.
- You can call multiple tools in a single response. If you intend to call multiple tools and there are no dependencies between them, make all independent tool calls in parallel. Maximize use of parallel tool calls where possible to increase efficiency. However, if some tool calls depend on previous calls to inform dependent values, do NOT call these tools in parallel and instead call them sequentially. For instance, if one operation must complete before another starts, run these operations sequentially instead. Never use placeholders or guess missing parameters in tool calls.
- If the user specifies that they want you to run tools "in parallel", you MUST send a single message with multiple tool use content blocks. For example, if you need to launch multiple agents in parallel, send a single message with multiple Task tool calls.
- Use specialized tools instead of bash commands when possible, as this provides a better user experience. For file operations, use dedicated tools: Read for reading files instead of cat/head/tail, Edit for editing instead of sed/awk, and Write for creating files instead of cat with heredoc or echo redirection. Reserve bash tools exclusively for actual system commands and terminal operations that require shell execution. NEVER use bash echo or other command-line tools to communicate thoughts, explanations, or instructions to the user. Output all communication directly in your response text instead.

You can use the following tools without requiring user approval: Read(//workspace/*), WebFetch(domain:*)
""";
    }

    /**
     * Returns the system prompt.
     *
     * @return the system prompt.
     */
    public String system() {
        String prompt = constitution();
        if (global != null) {
            prompt += "\n\n" + global.instructions();
        }
        if (user != null) {
            prompt += "\n\n" + user.instructions();
        }
        prompt += "\n\n" + context.instructions();

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
                        Current date time: %s
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

    /**
     * Returns the current date and time in ISO-8601 format, truncated to milliseconds.
     */
    private String date() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }

    /**
     * Asynchronously responses to a user prompt without conversation history.
     *
     * @param prompt the user prompt of task.
     * @return a future of response.
     */
    public CompletableFuture<String> response(String prompt) {
        conversation.add(Message.user(prompt));

        return llm.get().complete(conversation.hydrate(prompt), conversation.params())
                .handle((response, ex) -> {
                    var message = Optional.ofNullable(ex)
                            .map(t -> Message.error(t.getMessage()))
                            .orElse(Message.assistant(response));
                    conversation.add(message);
                    return response;
                });
    }

    /**
     * Asynchronously responses to a user prompt in a streaming way.
     *
     * @param prompt  the user prompt of task.
     * @param handler the stream response handler.
     */
    public void stream(String prompt, StreamResponseHandler handler) {
        logger.debug("user: {}", prompt);
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

                if (ex.isEmpty()) {
                    if (response.contains("<summary>") && response.contains("</summary>")) {
                        conversation.compact(response);
                    }
                }

                handler.onComplete(ex);
            }
        };

        llm.get().complete(prompt, conversation, accumulator);
    }
}
