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
package smile.studio.cli;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.formdev.flatlaf.util.SystemInfo;
import ioa.agent.Context;
import ioa.llm.client.LLM;
import ioa.agent.Agent;
import ioa.agent.memory.Skill;
import ioa.llm.client.StreamResponseHandler;
import ioa.llm.tool.Question;
import smile.plot.swing.Palette;
import smile.studio.*;
import smile.studio.text.HintWindow;
import smile.studio.text.Notepad;
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
    /** The container of conversation. */
    private final JPanel intents = new ScrollablePanel();
    /** The agent. */
    private final Agent agent;
    /** The reasoning effort level. */
    private String reasoningEffort = LLM.DEFAULT_REASONING_EFFORT;
    /** The hint window for showing argument hints of slash commands. */
    private final HintWindow hintWindow;

    /**
     * Constructor.
     * @param agent the agent.
     */
    public AgentCLI(Agent agent) {
        super(new BorderLayout());
        this.agent = agent;
        var frame = Arrays.stream(Window.getWindows())
              .filter(win -> win instanceof SmileStudio)
              .findFirst();
        var hints = createHintMap();
        this.hintWindow = new HintWindow(frame.orElse(null), hints);

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

    /**
     * Returns the agent.
     * @return the agent.
     */
    public Agent agent() {
        return agent;
    }

    /**
     * Returns the hint window for showing argument hints of slash commands.
     * @return the hint window.
     */
    public HintWindow hintWindow() {
        return hintWindow;
    }

    /**
     * Returns the reasoning effort level.
     * @return the reasoning effort level.
     */
    public String getReasoningEffort() {
        return reasoningEffort;
    }

    /**
     * Sets the reasoning effort level.
     * @param reasoningEffort the reasoning effort level.
     */
    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
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
            case Shell -> runShellCommand(intent, intentType, instructions);
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
     * Creates a map from slash command to argument hint.
     */
    private Map<String, String> createHintMap() {
        Map<String, String> hints = new HashMap<>();
        hints.put("/memory", "[show|add|edit|refresh]");
        hints.put("/memory show", "[ENTER to display the long term memory]");
        hints.put("/memory add", "[additional instructions]");
        hints.put("/memory edit", "[ENTER to open a notepad to edit the long term memory]");
        hints.put("/memory refresh", "[ENTER to reload the context from disk]");
        hints.put("/compact", "[instructions]");
        hints.put("/plan", "[off|short description of goals or tasks]");
        hints.put("/edit", "[file path]");
        hints.put("/train", "[ENTER for helps]");
        hints.put("/predict", "[ENTER for helps]");
        hints.put("/serve", "[ENTER for helps]");

        if (agent != null) {
            for (var skill : agent.skills()) {
                if (skill.isUserInvocable()) {
                    skill.hint().ifPresent(hint -> hints.put("/" + skill.name(), hint));
                }
            }
        }

        return hints;
    }

    /**
     * Executes shell commands.
     */
    private void runShellCommand(Intent intent, IntentType intentType, String instructions) {
        var output = intent.output();
        List<String> command = new ArrayList<>();
        switch (intentType) {
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
        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                try {
                    Process process = new ProcessBuilder(command)
                            .redirectErrorStream(true)
                            .start();

                    intent.setProgress(true);
                    intent.setStopAction(process::destroyForcibly);
                    // Read output from the command
                    var reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        publish(line);
                    }

                    try {
                        // Wait for the process to complete and return the exit code
                        if (process.waitFor(600_000, TimeUnit.MILLISECONDS)) {
                            int exitCode = process.exitValue();
                            if (exitCode != 0) {
                                publish("\nCommand failed with error code " + exitCode);
                            }
                            return exitCode;
                        } else {
                            publish("\nTimeout waiting for process to exit.");
                            return -1;
                        }
                    } catch (InterruptedException e) {
                        publish("Error waiting for process to exit: " + e.getMessage());
                        return -1;
                    }
                } catch (IOException ex) {
                    publish("Failed to execute '" + String.join(" " , command) + "': " + ex.getMessage());
                    return -1;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    output.append(line + "\n");
                }

                // Auto scroll to the bottom
                output.setCaretPosition(output.getDocument().getLength());
            }

            @Override
            protected void done() {
                intent.setProgress(false);
                // process() and done() are called in EDT, so we can safely update the UI here.
                output.highlight();
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
                case "edit" -> edit(args, intent.output());
                case "train", "predict", "serve" -> runShellCommand(intent, IntentType.Command, instructions);
                case "memory" -> memory(args, instructions, intent);
                case "system" -> showSystemPrompt(intent.output()); // for debugging
                case "clear" -> clear(intent.output());
                case "compact" -> compact(instructions, intent);
                case "plan" -> plan(args, instructions, intent.output());
                default -> runSkill(args[0], instructions, intent);
            }
        } catch (Throwable t) {
            intent.output().println("Error: " + t.getMessage());
        }
    }

    /** Opens a notepad to edit file. */
    private void edit(String[] args, OutputArea output) {
        if (args.length < 2) {
            output.println("Usage: /edit [file path]");
            return;
        }

        String path = args[1];
        Notepad.open(Path.of(path));
    }

    private boolean isAgentAvailable(OutputArea output) {
        if (agent == null || agent.llm() == null) {
            if (output.getLineCount() > 0) output.append("\n\n");
            output.println(bundle.getString("NoAIServiceError"));
            return false;
        }
        return true;
    }

    private void help(OutputArea output) {
        StringBuilder sb = new StringBuilder("""
                The following commands are available:
                
                /memory show        Display the content of long term memory
                /memory add         Add facts or notes to long term memory
                /memory edit        Open a notepad to edit the long term memory
                /memory refresh     Reload the context from disk
                /plan               Enter the plan mode.
                /plan off           Exit  the plan mode.
                /clear              Clear the current conversation session.
                /compact            Summarize the conversation and retain critical details.
                /edit               Edit a file with notepad.
                /train              Train a machine learning model
                /predict            Run batch inference
                /serve              Start an inference service""");

        if (agent != null && agent.llm() != null) {
            for (var skill : agent.skills()) {
                if (skill.isUserInvocable()) {
                    sb.append(String.format("\n/%-18s ", skill.name()))
                      .append(skill.description().split("\\.", 2)[0]);
                }
            }
        }

        output.setText(sb.toString());
    }

    /** Enters the plan mode. */
    private void plan(String[] args, String instructions, OutputArea output) {
        if (!isAgentAvailable(output)) return;

        if (args.length < 2) {
            output.println("Usage: /plan [short description of goals or tasks]");
            return;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
            agent.conversation().exitPlanMode(null);
            output.setText("Exit the plan mode.");
            return;
        }

        try {
            agent.conversation().enterPlanMode(instructions.substring(5).trim());
            output.println("Enter the plan mode.");
        } catch (IOException e) {
            output.println("Failed to enter the plan mode: " + e.getMessage());
        }
    }

    /** Executes memory commands. */
    private void memory(String[] args, String instructions, Intent intent) throws IOException {
        var output = intent.output();
        if (!isAgentAvailable(output)) return;

        if (args.length < 2) {
            output.println("Usage: /memory [show|add|edit|refresh]");
            return;
        }

        switch (args[1]) {
            case "show" -> showMemory(output);
            case "add" -> addMemory(instructions, output);
            case "edit" -> editMemory(output);
            case "refresh" -> refreshMemory(output);
            default -> output.println("Unknown subcommand for /memory: " + args[1]);
        }
    }

    /** Appends notes to SMILE.md. */
    private void addMemory(String instructions, OutputArea output) throws IOException {
        String md = instructions.substring(instructions.indexOf("add") + 3).trim();
        if (md.isBlank()) {
            output.println("/memory add should be followed with notes.");
        } else {
            agent.addMemory(md);
            output.println("SMILE.md appended with notes.");
        }
    }

    /** Open a notepad to edit the project's long term memory. */
    private void editMemory(OutputArea output) {
        Notepad.open(agent.context().path().resolve(Context.SMILE_MD));
        output.setText("SMILE.md is opened in a notepad window. Edit and save the file to update the long term memory.");
    }

    /** Displays the project's long term memory. */
    private void showMemory(OutputArea output) {
        output.setText(agent.instructions());
    }

    /** Displays the system prompt. */
    private void showSystemPrompt(OutputArea output) {
        output.setText(agent.system());
    }

    /** Reloads the context from disk. */
    private void refreshMemory(OutputArea output) {
        agent.refresh();
        output.println("Long term memory was reloaded.");
    }

    /** Clears the current conversation session. */
    private void clear(OutputArea output) {
        if (!isAgentAvailable(output)) return;
        agent.clear();
        output.println("Current conversation session was cleared.");
    }

    private void runSkill(String command, String instructions, Intent intent) {
        var output = intent.output();
        if (!isAgentAvailable(output)) return;

        var args = instructions.substring(command.length()).trim();
        var skill = agent.conversation().invokeSkill(command, args, Skill::isUserInvocable);

        if (skill.success()) {
            chat(intent, skill.output());
        } else {
            output.setText(skill.output());
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

        if (agent == null || agent.llm() == null) {
            intent.output().setText(bundle.getString("NoAIServiceError"));
            return;
        }

        if (LLM.DEFAULT_REASONING_EFFORT.equals(reasoningEffort)) {
            agent.conversation().params().setProperty(LLM.REASONING_EFFORT, "");
        } else {
            agent.conversation().params().setProperty(LLM.REASONING_EFFORT, reasoningEffort);
        }

        intent.setProgress(true);
        agent.conversation().params().setProperty(LLM.INTERRUPTED, "false");
        intent.setStopAction(() -> agent.conversation().params().setProperty(LLM.INTERRUPTED, "true"));

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
            public void onComplete(long totalTokens, long outputTokens, long inputTokens) {
                SwingUtilities.invokeLater(() -> {
                    intent.setProgress(false);
                    if (outputTokens > 0) {
                        intent.setStatus(outputTokens + " output tokens");
                    }
                });

                // Auto compact if total tokens exceed the threshold, otherwise render Markdown if applicable.
                if (totalTokens > agent.llm().compactThreshold()) {
                    SwingUtilities.invokeLater(() ->
                            intent.output().append("\n\n[The conversation session is too long, a compact command will be executed to summarize conversation.]\n"));
                    compact("", intent);
                }
            }

            @Override
            public void onException(Throwable ex) {
                SwingUtilities.invokeLater(() -> {
                    intent.setProgress(false);
                    intent.output().append("\nError: " + ex.getMessage());
                });
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
