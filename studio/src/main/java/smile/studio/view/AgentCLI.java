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
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import com.formdev.flatlaf.util.SystemInfo;
import smile.agent.Analyst;
import smile.agent.Memory;
import smile.llm.client.StreamResponseHandler;
import smile.plot.swing.Palette;
import smile.shell.JShell;
import smile.studio.SmileStudio;
import smile.studio.kernel.JavaRunner;
import smile.studio.kernel.ShellRunner;
import smile.studio.model.IntentType;
import smile.swing.ScrollablePanel;
import smile.util.OS;

/**
 * The conversation interface for agent to create model building pipeline.
 *
 * @author Haifeng Li
 */
public class AgentCLI extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AgentCLI.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(AgentCLI.class.getName(), Locale.getDefault());
    /** The container of conversations. */
    private final JPanel intents = new ScrollablePanel();
    /** JShell instance. */
    private final JavaRunner runner;
    /** The project folder. */
    private final Path path;
    /** The analyst agent. */
    private Analyst analyst;

    /**
     * Constructor.
     * @param path the project folder.
     * @param runner Java code execution engine.
     */
    public AgentCLI(Path path, JavaRunner runner) {
        super(new BorderLayout());
        this.runner = runner;
        this.path = path;
        initAnalyst();

        setBorder(new EmptyBorder(0, 0, 0, 8));
        intents.setLayout(new BoxLayout(intents, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(intents);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        intents.add(welcome());
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

    private void initAnalyst() {
        analyst = new Analyst(SmileStudio::llm,
                path.resolve(".smile", "analyst"),
                path);
    }

    /** Append a new intent box. */
    public void addIntent() {
        Intent intent = new Intent(this);
        intents.add(intent, intents.getComponentCount() - 1);
        SwingUtilities.invokeLater(() -> intent.editor().requestFocusInWindow());
    }

    /**
     * Executes an intent.
     * @param intentType the type of the intent.
     * @param instructions the instructions to execute.
     * @param output the output area to display the results.
     */
    public void run(IntentType intentType, String instructions, OutputArea output) {
        switch (intentType) {
            case Command -> runCommand(instructions, output);
            case Shell, Python -> runShell(intentType, instructions, output);
            case Instructions -> chat(null, instructions, output);
            default -> logger.debug("Ignore intent type: {}", intentType);
        }
    }

    /** Returns the welcome banner. */
    private Intent welcome() {
        Intent welcome = new Intent(this);
        welcome.setIntentType(IntentType.Raw);
        welcome.setEditable(false);
        welcome.setInputForeground(Palette.DARK_GRAY);
        welcome.editor().setText(JShell.logo.replaceAll("(?m)^\\s{3}", "") + """
        =====================================================================
        Welcome! I am Clair, your AI assistant for machine learning modeling.
        
        /help for help, /init for initializing your project
        cwd:\s""" + System.getProperty("user.dir"));

        welcome.output().setText("""
        As a state-of-the-art machine learning engineering agent,
        I can help you with:
        
        🤖 Automatic end-to-end ML/AI solutions based on your requirements.
        🔍 Best practices and state-of-the-art methods with web search.
        🏅 Targeted code block refinement by ablation study.
        🤝 Improved solution using iterative ensemble strategy.
        💡 High-quality code completion and generation.
        📊 Advanced interactive data visualization.
        📂 Process data from CSV, ARFF, JSON, Avro, Parquet, Iceberg, to SQL.
        🌐 Built-in inference server.
        
        Tips for getting started:
        1. Ctrl + ENTER to execute your intents.
        2. Run /init to create a SMILE.md file with instructions for agent.
        3. Be as specific as you would with another data scientist for the best result.
        4. Data visualization can be feed to AI agents for interpretation and advices.
        5. Create custom slash commands for reusable prompts or workflows.
        6. Run Shell commands starting with a percentage sign (%).
        7. Run Python expressions starting with an exclamation mark (!).
        8. AI can make mistakes. Always review agent's responses.""");
        return welcome;
    }

    /**
     * Executes shell commands.
     */
    private void runShell(IntentType intentType, String instructions, OutputArea output) {
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
    private void runCommand(String instructions, OutputArea output) {
        try {
            String[] command = instructions.split("\\s+");
            switch (command[0]) {
                case "help" -> help(command, output);
                case "train", "predict", "serve" -> runShell(IntentType.Command, instructions, output);
                case "init" -> initMemory(instructions, output);
                case "add-memory" -> addMemory(instructions, output);
                case "show-memory" -> showMemory(output);
                case "refresh-memory" -> refreshMemory(output);
                case "show-system" -> showSystemPrompt(output); // for debugging
                case "clear" -> clear(output);
                default -> runCustomCommand(command[0], instructions, output);
            }
        } catch (Throwable t) {
            output.appendLine("Error: " + t.getMessage());
        }
    }

    private void help(String[] command, OutputArea output) {
        StringBuilder sb = new StringBuilder("""
                The following commands are available:
                
                /init\t\tInitialize the project with your tasks and requirements
                /add-memory\tAdd facts or notes to long-term memory
                /show-memory\tDisplay the content of long-term memory
                /refresh-memory\tReload the context from disk
                /clear\t\tClear the current conversation history.
                /train\t\tTrain a machine learning model
                /predict\tRun batch inference
                /serve\t\tStart an inference service""");

        for (var cmd : analyst.commands()) {
            sb.append("\n/")
              .append(cmd.name())
              .append(cmd.name().length() > 6 ? "\t" : "\t\t")
              .append(cmd.description());
        }

        for (var skill : analyst.skills()) {
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
            analyst.initMemory(md);
            output.appendLine("SMILE.md created with instructions.");
        }
    }

    /** Appends notes to SMILE.md. */
    private void addMemory(String instructions, OutputArea output) throws IOException {
        String md = instructions.substring(10).trim();
        if (md.isBlank()) {
            output.appendLine("/add-memory should be followed with notes.");
        } else {
            analyst.addMemory(md);
            output.appendLine("SMILE.md appended with notes.");
        }
    }

    /** Displays the project long-term memory. */
    private void showMemory(OutputArea output) {
        output.setText(analyst.instructions());
        toMarkdown(output);
    }

    /** Displays the system prompt. */
    private void showSystemPrompt(OutputArea output) {
        output.setText(analyst.system());
    }

    /** Renders output as Markdown. */
    private void toMarkdown(OutputArea output) {
        var html = new Markdown(output.getText());
        var parent = output.getParent();
        parent.remove(output);
        parent.add(html, BorderLayout.SOUTH);
    }

    /** Reloads the context from disk. */
    private void refreshMemory(OutputArea output) {
        initAnalyst();
        output.appendLine("Long-term memory was reloaded.");
    }

    /** Clears the current conversation history. */
    private void clear(OutputArea output) {
        analyst.clear();
        output.appendLine("Current conversation history was cleared.");
    }

    private void load(String[] command) {

    }

    private void analyze(String[] command) {

    }

    private void runCustomCommand(String command, String instructions, OutputArea output) {
        Optional<? extends Memory> cmd = analyst.commands().stream()
                .filter(c -> c.name().equals(command))
                .findFirst();

        if (cmd.isEmpty()) {
            cmd = analyst.skills().stream()
                    .filter(s -> s.name().equals(command))
                    .findFirst();
        }

        if (cmd.isEmpty()) {
            output.setText("Error: Unknown command /" + command);
            return;
        }

        var args = instructions.substring(command.length()).trim();
        if (cmd.get().content().contains("{{args}}")) {
            if (args.isBlank()) {
                output.setText("Error: /" + command + " requires arguments.");
            } else {
                var prompt = cmd.get().prompt(args);
                chat(command, prompt, output);
            }
        } else {
            // append instructions to command without {{args}} placeholder.
            var prompt = cmd.get().content() + "\n\n" + args;
            chat(command, prompt, output);
        }
    }

    private void chat(String command, String prompt, OutputArea output) {
        if (prompt.isBlank()) {
            output.setText(bundle.getString("Hello"));
            return;
        }

        if (analyst.llm() == null) {
            output.setText(bundle.getString("NoAIServiceError"));
            return;
        }

        output.setText("Thinking...");
        var timer = new Timer(500, e -> output.append("."));
        timer.start();

        // Stream processing runs in a background thread so that we don't
        // need to create a SwingWorker thread.
        analyst.stream(command, prompt, new StreamResponseHandler() {
            boolean firstChunk = true;
            @Override
            public void onNext(String chunk) {
                SwingUtilities.invokeLater(() -> {
                    if (firstChunk) {
                        timer.stop();
                        output.clear();
                        firstChunk = false;
                    }
                    output.append(chunk);
                });
            }

            @Override
            public void onComplete(Optional<Throwable> ex) {
                if (ex.isPresent()) {
                    timer.stop();
                    SwingUtilities.invokeLater(() ->
                            output.append("\nError: " + ex.map(Throwable::getMessage).orElse("Unknown")));
                } else {
                    SwingUtilities.invokeLater(() -> {
                        var text = output.getText();
                        // simple heuristic to detect Markdown syntax
                        if (text.contains("##") || text.contains("**")) {
                            // commonmark doesn't render table nicely
                            if (!text.contains("|--")) {
                                toMarkdown(output);
                            }
                        }
                    });
                }
            }
        });
    }
}
