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
package smile.studio.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import com.formdev.flatlaf.util.SystemInfo;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import smile.agent.Agent;
import smile.agent.Memory;
import smile.llm.client.StreamResponseHandler;
import smile.llm.tool.Question;
import smile.plot.swing.Palette;
import smile.studio.kernel.ShellRunner;
import smile.studio.model.IntentType;
import smile.swing.ScrollablePanel;
import smile.util.OS;
import smile.util.Strings;

/**
 * The conversation interface for agent.
 *
 * @author Haifeng Li
 */
public class AgentCLI extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AgentCLI.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(AgentCLI.class.getName(), Locale.getDefault());
    /**
     * The threshold for compacting conversation session by summarization.
     * If the total tokens of conversation session exceeds this threshold,
     * a compact command will be automatically executed to free up space
     * in the context window.
     */
    private static final int COMPACT_THRESHOLD = Integer.parseInt(System.getProperty("smile.agent.auto.compact", "180000"));
    /** The container of conversation. */
    private final JPanel intents = new ScrollablePanel();
    /** The agent. */
    private final Agent agent;
    /** The provider of slash command hints. */
    private final CompletionProvider hints;

    /**
     * Constructor.
     * @param agent the agent.
     */
    public AgentCLI(Agent agent) {
        super(new BorderLayout());
        this.agent = agent;
        this.hints = createCompletionProvider();

        setBorder(new EmptyBorder(0, 0, 0, 8));
        intents.setLayout(new BoxLayout(intents, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(intents);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        intents.add(new Intent(this));
        intents.add(Box.createVerticalGlue());

        Monospaced.addListener((e) ->
                SwingUtilities.invokeLater(() -> {
                    Font font = (Font) e.getNewValue();
                    for (int i = 0; i < intents.getComponentCount(); i++) {
                        if (intents.getComponent(i) instanceof Intent cmd) {
                            cmd.indicator().setFont(font);
                            cmd.editor().setFont(font);
                            cmd.output().setFont(font);
                        }
                    }
                })
        );
    }

    /** Append a new intent box. */
    public void addIntent() {
        Intent intent = new Intent(this);
        intents.add(intent, intents.getComponentCount() - 1);
        SwingUtilities.invokeLater(() -> intent.editor().requestFocusInWindow());
    }

    /**
     * Executes an intent.
     * @param intent the intent widget.
     * @param intentType the type of the intent.
     */
    public void run(Intent intent, IntentType intentType) {
        String instructions = intent.editor().getText();
        switch (intentType) {
            case Command -> runSlashCommand(intent, instructions);
            case Shell, Python -> runShellCommand(intent, intentType, instructions);
            case Instructions -> {
                intent.setStatus("Thinking...");
                chat(intent, instructions);
            }
            default -> logger.debug("Ignore intent type: {}", intentType);
        }
    }

    /**
     * Adds the welcome banner.
     * @param banner the welcome banner.
     * @param text the welcome text.
     */
    public void welcome(String banner, String text) {
        Intent welcome = new Intent(this);
        welcome.setIntentType(IntentType.Raw);
        welcome.setEditable(false);
        welcome.setInputForeground(Palette.DARK_GRAY);
        welcome.editor().setText(banner);
        welcome.output().setText(text);
        intents.add(welcome, 0);
    }

    /**
     * Creates a provider of slash command argument hint.
     */
    private CompletionProvider createCompletionProvider() {
        DefaultCompletionProvider provider = new DefaultCompletionProvider();

        provider.addCompletion(new BasicCompletion(provider,
                "/init [project goals, requirements, tasks, instructions, etc]"));
        provider.addCompletion(new BasicCompletion(provider,
                "/memory [show|add|edit|refresh]"));
        provider.addCompletion(new BasicCompletion(provider,
                "/compact [instructions]"));
        provider.addCompletion(new BasicCompletion(provider,
                "/plan [off|short description of goals or tasks]"));
        provider.addCompletion(new BasicCompletion(provider,
                "/open [file path]"));
        provider.addCompletion(new BasicCompletion(provider,
                "/train [-h for helps]"));
        provider.addCompletion(new BasicCompletion(provider,
                "/predict [-h for helps]"));
        provider.addCompletion(new BasicCompletion(provider,
                "/serve [-h for helps]"));
        for (var cmd : agent.commands()) {
            cmd.hint().ifPresent(hint -> provider.addCompletion(
                    new BasicCompletion(provider, "/" + cmd.name() + " " + hint)));
        }

        for (var skill : agent.skills()) {
            skill.hint().ifPresent(hint -> provider.addCompletion(
                    new BasicCompletion(provider, "/" + skill.name() + " " + hint)));
        }

        return provider;
    }

    /**
     * Returns a provider of slash command argument hint.
     */
    public CompletionProvider hints() {
        return hints;
    }

    /**
     * Executes shell commands.
     */
    private void runShellCommand(Intent intent, IntentType intentType, String instructions) {
        var output = intent.output();
        List<String> command = new ArrayList<>();
        switch (intentType) {
            case Python -> {
                command.add("python");
                command.add("-c");
            }
            case Shell -> {
                if (SystemInfo.isWindows) {
                    command.add("powershell.exe");
                    command.add("-Command");
                } else {
                    command.add("bash");
                    command.add("-c");
                }
            }
            case Command -> {
                var smile = System.getProperty("smile.home", ".") + "/bin/smile";
                if (SystemInfo.isWindows) smile += ".bat";
                command.add(smile);
            }
            default -> {
                logger.debug("Invalid intent type: {}", intentType);
                return;
            }
        }

        command.addAll(OS.parse(instructions));
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                var shell = new ShellRunner();
                shell.setOutputArea(output);
                int ret = shell.exec(command);
                if (ret != 0) output.appendLine("\nCommand failed with error code " + ret);
                return null;
            }

            @Override
            protected void done() {
                SwingUtilities.invokeLater(output::flush);
            }
        };
        worker.execute();
    }

    /** Executes slash commands. */
    private void runSlashCommand(Intent intent, String instructions) {
        try {
            String[] args = instructions.split("\\s+");
            switch (args[0]) {
                case "help" -> help(intent.output());
                case "open" -> open(args, intent.output());
                case "train", "predict", "serve" -> runShellCommand(intent, IntentType.Command, instructions);
                case "init" -> initMemory(instructions, intent.output());
                case "memory" -> memory(args, instructions, intent);
                case "system" -> showSystemPrompt(intent.output()); // for debugging
                case "clear" -> clear(intent.output());
                case "compact" -> compact(instructions, intent);
                case "plan" -> plan(args, instructions, intent.output());
                default -> runCustomCommand(args[0], instructions, intent);
            }
        } catch (Throwable t) {
            intent.output().appendLine("Error: " + t.getMessage());
        }
    }

    private void help(OutputArea output) {
        StringBuilder sb = new StringBuilder("""
                The following commands are available:
                
                /init\t\tInitialize the project with your tasks and requirements
                /memory show\tDisplay the content of long-term memory
                /memory add\tAdd facts or notes to long-term memory
                /memory edit\tOpen a notepad to edit to long-term memory
                /memory refresh\tReload the context from disk
                /plan\t\tEnter the plan mode.
                /plan off\tExit the plan mode.
                /clear\t\tClear the current conversation session.
                /compact\tSummarize the conversation and retain critical details.
                /open\t\tOpen a text file to edit.
                /train\t\tTrain a machine learning model
                /predict\tRun batch inference
                /serve\t\tStart an inference service""");

        for (var cmd : agent.commands()) {
            sb.append("\n/")
              .append(cmd.name())
              .append(cmd.name().length() > 6 ? "\t" : "\t\t")
              .append(cmd.description());
        }

        for (var skill : agent.skills()) {
            sb.append("\n/")
                    .append(skill.name())
                    .append(skill.name().length() > 6 ? "\t" : "\t\t")
                    .append(skill.description());
        }

        output.setText(sb.toString());
    }

    /** Generates a starter SMILE.md. */
    private void initMemory(String instructions, OutputArea output) throws IOException {
        String md = instructions.substring(5).trim();
        if (md.isBlank()) {
            output.appendLine("/init should be followed with the project instructions.");
        } else {
            agent.initMemory(md);
            output.appendLine("SMILE.md created with instructions.");
        }
    }

    /** Enters the plan mode. */
    private void plan(String[] args, String instructions, OutputArea output) {
        if (args.length < 2) {
            output.appendLine("Usage: /plan [short description of goals or tasks]");
            return;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
            agent.conversation().exitPlanMode(null);
            output.setText("Exit the plan mode.");
            return;
        }

        try {
            agent.conversation().enterPlanMode(instructions.substring(5).trim());
            output.appendLine("Enter the plan mode.");
        } catch (IOException e) {
            output.appendLine("Failed to enter the plan mode: " + e.getMessage());
        }
    }

    /** Executes memory commands. */
    private void memory(String[] args, String instructions, Intent intent) throws IOException {
        var output = intent.output();
        if (args.length < 2) {
            output.appendLine("Usage: /memory [show|add|edit|refresh]");
            return;
        }

        switch (args[1]) {
            case "show" -> showMemory(intent);
            case "add" -> addMemory(instructions, output);
            case "edit" -> editMemory(output);
            case "refresh" -> refreshMemory(output);
            default -> output.appendLine("Unknown subcommand for /memory: " + args[1]);
        }
    }

    /** Appends notes to SMILE.md. */
    private void addMemory(String instructions, OutputArea output) throws IOException {
        String md = instructions.substring(instructions.indexOf("add") + 3).trim();
        if (md.isBlank()) {
            output.appendLine("/memory add should be followed with notes.");
        } else {
            agent.addMemory(md);
            output.appendLine("SMILE.md appended with notes.");
        }
    }

    /** Open a notepad to edit the project long-term memory. */
    private void editMemory(OutputArea output) {
        Notepad.open(agent.context().path().getParent().resolve("SMILE.md"));
        output.setText("SMILE.md is opened in a notepad window. Edit and save the file to update the long-term memory.");
    }

    /** Displays the project long-term memory. */
    private void showMemory(Intent intent) {
        intent.renderMarkdown(agent.instructions());
    }

    /** Displays the system prompt. */
    private void showSystemPrompt(OutputArea output) {
        output.setText(agent.system());
    }

    /** Opens a notepad to edit file. */
    private void open(String[] args, OutputArea output) {
        if (args.length < 2) {
            output.appendLine("Usage: /open [file path]");
            return;
        }

        String path = args[1];
        Notepad.open(Path.of(path));
    }

    /** Reloads the context from disk. */
    private void refreshMemory(OutputArea output) {
        agent.refresh();
        output.appendLine("Long-term memory was reloaded.");
    }

    /** Clears the current conversation session. */
    private void clear(OutputArea output) {
        agent.clear();
        output.appendLine("Current conversation session was cleared.");
    }

    private void runCustomCommand(String command, String instructions, Intent intent) {
        Optional<? extends Memory> cmd = agent.command(command);
        if (cmd.isEmpty()) {
            cmd = agent.skills().stream()
                    .filter(s -> s.name().equals(command))
                    .findFirst();
        }

        if (cmd.isEmpty()) {
            intent.output().setText("Error: Unknown command /" + command);
            return;
        }

        var args = instructions.substring(command.length()).trim();
        var memory = cmd.get();
        var description = memory.description();
        if (!Strings.isNullOrBlank(description)) {
            intent.setStatus(description);
        }

        if (memory.content().contains("{{args}}")) {
            if (args.isBlank()) {
                intent.output().setText("Error: /" + command + " requires arguments.");
            } else {
                var prompt = memory.prompt(args);
                chat(intent, prompt);
            }
        } else {
            // append instructions to command without {{args}} placeholder.
            var prompt = memory.content() + "\n\n" + args;
            chat(intent, prompt);
        }
    }

    /** Compacts conversation session by summarization. */
    private void compact(String instructions, Intent intent) {
        var prompt = """
Your task is to create a detailed summary of the conversation so far, paying close attention to the user's explicit requests and your previous actions.
This summary should be thorough in capturing technical details, code patterns, and architectural decisions that would be essential for continuing development work without losing context.

Before providing your final summary, wrap your analysis in <analysis> tags to organize your thoughts and ensure you've covered all necessary points. In your analysis process:

1. Chronologically analyze each message and section of the conversation. For each section thoroughly identify:
    - The user's explicit requests and intents
    - Your approach to addressing the user's requests
    - Key decisions, technical concepts and code patterns
    - Specific details like file names, full code snippets, function signatures, file edits, etc
2. Double-check for technical accuracy and completeness, addressing each required element thoroughly.

Your summary should include the following sections:

1. Primary Request and Intent: Capture all of the user's explicit requests and intents in detail
2. Key Technical Concepts: List all important technical concepts, technologies, and frameworks discussed.
3. Files and Code Sections: Enumerate specific files and code sections examined, modified, or created. Pay special attention to the most recent messages and include full code snippets where applicable and include a summary of why this file read or edit is important.
4. Problem Solving: Document problems solved and any ongoing troubleshooting efforts.
5. Pending Tasks: Outline any pending tasks that you have explicitly been asked to work on.
6. Current Work: Describe in detail precisely what was being worked on immediately before this summary request, paying special attention to the most recent messages from both user and assistant. Include file names and code snippets where applicable.
7. Optional Next Step: List the next step that you will take that is related to the most recent work you were doing. IMPORTANT: ensure that this step is DIRECTLY in line with the user's explicit requests, and the task you were working on immediately before this summary request. If your last task was concluded, then only list next steps if they are explicitly in line with the users request. Do not start on tangential requests without confirming with the user first.
8. If there is a next step, include direct quotes from the most recent conversation showing exactly what task you were working on and where you left off. This should be verbatim to ensure there's no drift in task interpretation.

Here's an example of how your output should be structured:

<example>
<analysis>
[Your thought process, ensuring all points are covered thoroughly and accurately]
</analysis>

<summary>
1. Primary Request and Intent:
   [Detailed description]

2. Key Technical Concepts:
    - [Concept 1]
    - [Concept 2]
    - [...]

3. Files and Code Sections:
    - [File Name 1]
        - [Summary of why this file is important]
        - [Summary of the changes made to this file, if any]
        - [Important Code Snippet]
    - [File Name 2]
        - [Important Code Snippet]
    - [...]

4. Problem Solving:
   [Description of solved problems and ongoing troubleshooting]

5. Pending Tasks:
    - [Task 1]
    - [Task 2]
    - [...]

6. Current Work:
   [Precise description of current work]

7. Optional Next Step:
   [Optional Next step to take]

</summary>
</example>

Please provide your summary based on the conversation so far, following this structure and ensuring precision and thoroughness in your response.""";

        if (!instructions.isBlank()) {
            prompt = prompt + "\n\n" + instructions;
        }

        intent.setStatus("Compacting...");
        chat(intent, prompt);
    }

    private void chat(Intent intent, String prompt) {
        if (prompt.isBlank()) {
            intent.output().setText(bundle.getString("Hello"));
            return;
        }

        if (agent.llm() == null) {
            intent.output().setText(bundle.getString("NoAIServiceError"));
            return;
        }

        intent.setProgress(true);

        // Stream processing runs in a background thread so that we don't
        // need to create a SwingWorker thread.
        agent.stream(prompt, new StreamResponseHandler() {
            @Override
            public void onNext(String chunk) {
                SwingUtilities.invokeLater(() -> {
                    intent.output().append(chunk);
                });
            }

            @Override
            public void onComplete(Throwable ex, long totalTokens, long outputTokens, long inputTokens) {
                SwingUtilities.invokeLater(() -> intent.setProgress(false));

                if (ex != null) {
                    SwingUtilities.invokeLater(() ->
                            intent.output().append("\nError: " + ex.getMessage()));
                } else {
                    if (outputTokens > 0) {
                        onStatus(outputTokens + " output tokens");
                    }

                    // Auto compact if total tokens exceed the threshold, otherwise render Markdown if applicable.
                    if (totalTokens > COMPACT_THRESHOLD) {
                        SwingUtilities.invokeLater(() ->
                                intent.output().append("\n\n[The conversation session is too long, a compact command will be executed to summarize conversation.]\n"));
                        compact("", intent);
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            var text = intent.output().getText();
                            // simple heuristic to detect Markdown syntax
                            if (text.contains("##") || text.contains("**")) {
                                // commonmark doesn't render table nicely
                                if (!text.contains("|--")) {
                                    intent.renderMarkdown(text);
                                }
                            }
                        });
                    }
                }
            }

            @Override
            public void onStatus(String status) {
                if (!Strings.isNullOrBlank(status)) {
                    SwingUtilities.invokeLater(() -> intent.setStatus(status));
                }
            }

            @Override
            public void onQuestion(Question question) {
                SwingUtilities.invokeLater(() -> intent.addQuestion(question));
            }
        });
    }
}
