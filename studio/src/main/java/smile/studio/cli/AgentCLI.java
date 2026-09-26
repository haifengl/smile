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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.formdev.flatlaf.util.SystemInfo;
import ioa.agent.AgentListener;
import ioa.agent.AgentRequest;
import ioa.agent.Context;
import ioa.llm.Conversation;
import ioa.llm.Message;
import ioa.llm.Role;
import ioa.llm.client.LLM;
import ioa.agent.Agent;
import ioa.agent.memory.Skill;
import ioa.llm.tool.Question;
import smile.plot.swing.Palette;
import smile.studio.SmileStudio;
import smile.studio.text.HintWindow;
import smile.studio.text.Notepad;
import smile.studio.text.OutputArea;
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
    /** The intent whose parent turn is showing, or the next user prompt. */
    private Intent activeIntent;
    /** Intents waiting for their queued turn to start, in queue order. */
    private final ArrayDeque<Intent> pendingTurns = new ArrayDeque<>();
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
        if (agent != null) {
            agent.session().addListener(sessionListener());
        }
    }

    /** Renders queued requests, notices, and stream progress for this agent. */
    private AgentListener sessionListener() {
        return new AgentListener() {
            @Override
            public void onQueued(AgentRequest request) {
                if (request.kind() == AgentRequest.Kind.NOTICE) {
                    return;
                }
                onEdtNow(() -> {
                    if (request.from() == null || request.from().isBlank()) {
                        if (activeIntent != null) {
                            pendingTurns.addLast(activeIntent);
                        }
                    } else {
                        pendingTurns.addLast(openRequest(request.modelPrompt()));
                    }
                });
            }

            @Override
            public void onNotice(AgentRequest request) {
                onEdtNow(() -> openRequest(request.task()).setProgress(false));
            }

            @Override
            public void onSkipped(AgentRequest request, String reason) {
                onEdtNow(() -> {
                    Intent intent = pendingTurns.pollFirst();
                    if (intent == null) {
                        intent = openRequest("");
                    }
                    intent.output().append(reason);
                    intent.setProgress(false);
                });
            }

            @Override
            public void onStarted(String runId, String label) {
                onEdtNow(() -> {
                    if (runId == null) {
                        Intent intent = pendingTurns.pollFirst();
                        if (intent != null) {
                            activeIntent = intent;
                        }
                        if (activeIntent != null) {
                            activeIntent.setProgress(true);
                            activeIntent.setStatus("Thinking...");
                        }
                        return;
                    }
                    if (activeIntent != null) {
                        activeIntent.beginRun(runId, label, agent.session().callName());
                    }
                });
            }

            @Override
            public void onNext(String runId, String chunk) {
                onEdt(() -> {
                    if (activeIntent != null) {
                        activeIntent.appendRun(runId, chunk);
                    }
                });
            }

            @Override
            public void onStatus(String runId, String status) {
                if (Strings.isNullOrBlank(status)) {
                    return;
                }
                onEdt(() -> {
                    if (activeIntent == null) {
                        return;
                    }
                    if (runId == null) {
                        activeIntent.setStatus(status);
                    } else {
                        activeIntent.appendRun(runId, "\n[" + status + "]\n");
                    }
                });
            }

            @Override
            public void onQuestion(String runId, Question question) {
                onEdtNow(() -> {
                    if (activeIntent != null) {
                        activeIntent.addQuestion(runId, question);
                    }
                });
            }

            @Override
            public void onComplete(String runId, long totalTokens, long outputTokens, long inputTokens) {
                onEdtNow(() -> {
                    if (runId != null) {
                        if (activeIntent != null) {
                            activeIntent.finishRun(runId, "Finished");
                        }
                        return;
                    }
                    if (activeIntent != null) {
                        activeIntent.setProgress(false);
                        if (outputTokens > 0) {
                            activeIntent.setStatus(outputTokens + " output tokens");
                        }
                    }
                    boolean alreadyCompacted = "true".equals(agent.conversation().params().getProperty(LLM.COMPACTED));
                    agent.conversation().params().remove(LLM.COMPACTED);
                    if (!alreadyCompacted && totalTokens > agent.llm().map(LLM::compactThreshold).orElse(LLM.DEFAULT_COMPACT_THRESHOLD) && activeIntent != null) {
                        activeIntent.output().append("\n\n[The conversation session is too long, a compact command will be executed to summarize conversation.]\n");
                        compact("", activeIntent);
                    }
                });
            }

            @Override
            public void onException(String runId, Throwable ex) {
                onEdtNow(() -> {
                    Throwable root = ex;
                    while (root.getCause() != null && root.getCause() != root) {
                        root = root.getCause();
                    }
                    String message = root.getMessage() == null ? root.toString() : root.getMessage();
                    if (runId != null) {
                        if (activeIntent != null) {
                            activeIntent.appendRun(runId, "\n" + message);
                            activeIntent.finishRun(runId, root.getClass().getSimpleName());
                        }
                        return;
                    }
                    if (activeIntent != null) {
                        activeIntent.setProgress(false);
                        activeIntent.setStatus(root.getClass().getSimpleName());
                        activeIntent.output().append("\n" + message);
                    }
                });
            }
        };
    }

    private void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private void onEdtNow(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(action);
            } catch (Exception ex) {
                logger.error("Failed to update the agent view: {}", ex.getMessage());
            }
        }
    }

    /**
     * Inserts a read-only intent for work that arrived from another agent
     * or as a notice, above the empty composer.
     * @param text the prompt or notice.
     * @return the new intent.
     */
    public Intent openRequest(String text) {
        Intent intent = new Intent(this);
        intent.editor().setText(text);
        intent.setEditable(false);
        intents.add(intent, Math.max(0, intents.getComponentCount() - 1));
        intents.revalidate();
        return intent;
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
        hints.put("/resume", "[ENTER to choose a previous session]");
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
                case "resume" -> resume(intent.output());
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
        if (agent == null || agent.llm().isEmpty()) {
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
                /resume             Choose a previous session and restore its context.
                /compact            Summarize the conversation and retain critical details.
                /edit               Edit a file with notepad.
                /train              Train a machine learning model
                /predict            Run batch inference
                /serve              Start an inference service""");

        if (agent != null && agent.llm().isPresent()) {
            for (var skill : agent.skills()) {
                if (skill.isUserInvocable()) {
                    sb.append(String.format("\n/%-18s ", skill.name()))
                      .append(skill.description().split("\\.", 2)[0]);
                }
            }
        }

        SwingUtilities.invokeLater(() -> output.setText(sb.toString()));
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

    /** Opens a session picker and loads the selected conversation. */
    private void resume(OutputArea output) {
        if (!isAgentAvailable(output)) return;
        if (agent.session().isBusy()) {
            output.println("Wait until the current turn finishes before resuming a session.");
            return;
        }
        List<Conversation.Session> sessions;
        try {
            sessions = agent.conversation().sessions();
        } catch (IOException ex) {
            output.println("Failed to list sessions: " + ex.getMessage());
            return;
        }
        if (sessions.isEmpty()) {
            output.println("No previous sessions.");
            return;
        }
        Conversation.Session selected = pickSession(sessions);
        if (selected == null) {
            return;
        }
        if (agent.session().isBusy()) {
            output.println("Wait until the current turn finishes before resuming a session.");
            return;
        }
        try {
            int count = agent.conversation().resume(selected.directory());
            showResumedSession(selected, count);
        } catch (IOException ex) {
            output.println("Failed to resume session: " + ex.getMessage());
        }
    }

    private Conversation.Session pickSession(List<Conversation.Session> sessions) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Resume session", Dialog.ModalityType.APPLICATION_MODAL);
        DefaultListModel<Conversation.Session> model = new DefaultListModel<>();
        sessions.forEach(model::addElement);
        JList<Conversation.Session> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(12);
        list.setCellRenderer(new SessionRenderer());

        final Conversation.Session[] chosen = new Conversation.Session[1];
        Runnable choose = () -> {
            chosen[0] = list.getSelectedValue();
            dialog.dispose();
        };
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && list.getSelectedValue() != null) {
                    choose.run();
                }
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "resume");
        list.getActionMap().put("resume", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                choose.run();
            }
        });

        JButton resume = new JButton("Resume");
        resume.addActionListener(event -> choose.run());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(resume);

        JLabel hint = new JLabel("Choose a session. The model continues with that conversation.");
        hint.setBorder(new EmptyBorder(8, 12, 4, 12));
        dialog.add(hint, BorderLayout.NORTH);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(resume);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setPreferredSize(new Dimension(560, 420));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return chosen[0];
    }

    /**
     * Replaces the transcript with the resumed messages. The caller appends a
     * fresh composer after this returns.
     */
    private void showResumedSession(Conversation.Session session, int count) {
        List<Intent> banners = new ArrayList<>();
        for (Component component : intents.getComponents()) {
            if (component instanceof Intent intent && intent.getIntentType() == IntentType.Raw) {
                banners.add(intent);
            }
        }
        intents.removeAll();
        for (Intent banner : banners) {
            intents.add(banner);
        }

        Intent current = null;
        StringBuilder body = new StringBuilder();
        for (Message message : agent.conversation().messages()) {
            if (!(message.content() instanceof String text) || text.isBlank()) {
                continue;
            }
            if (message.role() == Role.user) {
                flushHistory(current, body);
                current = historyIntent(text);
            } else {
                if (current == null) {
                    current = historyIntent("Session summary");
                }
                if (!body.isEmpty()) {
                    body.append("\n\n");
                }
                body.append(forDisplay(text));
            }
        }
        flushHistory(current, body);
        if (current != null) {
            current.setStatus("Resumed " + sessionLabel(session) + " · " + count + " messages");
        }

        intents.add(Box.createVerticalGlue());
        intents.revalidate();
        revalidate();
        intents.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, intents);
            if (scroll != null) {
                JScrollBar bar = scroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    private Intent historyIntent(String prompt) {
        Intent intent = new Intent(this);
        intent.editor().setText(prompt);
        intent.setEditable(false);
        intents.add(intent);
        return intent;
    }

    private static void flushHistory(Intent current, StringBuilder body) {
        if (current == null || body.isEmpty()) {
            return;
        }
        String shown = body.toString();
        current.output().print(shown);
        current.output().setText(shown);
        body.setLength(0);
    }

    private static String forDisplay(String text) {
        int limit = 12_000;
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "\n\n… [truncated in the view; the model still has the full text]";
    }

    private static String sessionLabel(Conversation.Session session) {
        try {
            LocalDateTime parsed = LocalDateTime.parse(session.id(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
            return parsed.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ex) {
            return session.id();
        }
    }

    /**
     * Two-line session row. Both lines use the list foreground, including the
     * selection colors, so the preview stays readable in light and dark themes.
     */
    private final class SessionRenderer extends JPanel implements ListCellRenderer<Conversation.Session> {
        private final JLabel title = new JLabel();
        private final JLabel preview = new JLabel();

        private SessionRenderer() {
            super(new BorderLayout(0, 2));
            setBorder(new EmptyBorder(6, 10, 6, 10));
            title.setOpaque(false);
            preview.setOpaque(false);
            add(title, BorderLayout.NORTH);
            add(preview, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Conversation.Session> list, Conversation.Session session,
                                                      int index, boolean selected, boolean focus) {
            String current = session.directory().equals(agent.conversation().path()) ? " (current)" : "";
            String text = session.preview().isBlank() ? "(no preview)" : session.preview();
            title.setText(sessionLabel(session) + current);
            preview.setText(text);

            Font base = list.getFont();
            title.setFont(base.deriveFont(Font.BOLD));
            preview.setFont(base);

            Color background = selected ? list.getSelectionBackground() : list.getBackground();
            Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
            if (background == null) {
                background = UIManager.getColor(selected ? "List.selectionBackground" : "List.background");
            }
            if (foreground == null) {
                foreground = UIManager.getColor("List.foreground");
            }
            setBackground(background);
            setForeground(foreground);
            title.setForeground(foreground);
            preview.setForeground(foreground);
            setOpaque(true);
            return this;
        }
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
        var prompt = "";
        try (var is = ioa.llm.Conversation.class.getResourceAsStream("/ioa/llm/compact.md")) {
            if (is == null) {
                logger.error("ioa.llm.compact not found.");
            } else {
                // Reads all bytes and converts them into a String using UTF-8 encoding
                prompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            logger.error("Failed to read compact instructions: {}", ex.getMessage());
        }

        if (prompt.isBlank()) {
            logger.error("No compact instructions specified.");
            return;
        }

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

        if (agent == null || agent.llm().isEmpty()) {
            intent.output().setText(bundle.getString("NoAIServiceError"));
            return;
        }

        if (LLM.DEFAULT_REASONING_EFFORT.equals(reasoningEffort)) {
            agent.conversation().params().setProperty(LLM.REASONING_EFFORT, "");
        } else {
            agent.conversation().params().setProperty(LLM.REASONING_EFFORT, reasoningEffort);
        }

        activeIntent = intent;
        agent.conversation().params().setProperty(LLM.INTERRUPTED, "false");
        intent.setStopAction(() -> agent.conversation().params().setProperty(LLM.INTERRUPTED, "true"));
        agent.session().accept(AgentRequest.fromUser(agent.session().callName(), prompt));
    }
}
