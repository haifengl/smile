/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
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
import java.util.concurrent.atomic.AtomicBoolean;
import com.formdev.flatlaf.util.SystemInfo;
import ioa.agent.AgentListener;
import ioa.agent.AgentRequest;
import ioa.agent.AgentRequestQueue;
import ioa.agent.Context;
import ioa.agent.QueuedRequest;
import ioa.llm.Conversation;
import ioa.llm.Message;
import ioa.llm.Role;
import ioa.llm.client.LLM;
import smile.studio.LlmServices;
import smile.studio.SmileStudio;
import ioa.agent.Agent;
import ioa.agent.memory.Skill;
import ioa.llm.tool.Question;
import smile.plot.swing.Palette;
import smile.studio.text.HintWindow;
import smile.studio.text.OutputArea;
import smile.studio.workspace.Workspace;
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
    /** The workspace that hosts the file tabs. */
    private final Workspace workspace;
    /** The reasoning effort level. {@code default} sends nothing, so the server uses its own budget. */
    private String reasoningEffort = LLM.DEFAULT_REASONING_EFFORT;
    /** The intent whose turn is running, or null when idle. Set only by onStarted. */
    private Intent activeIntent;
    /** The queue id of the running turn, or null when idle. Used to mark a cancelled turn. */
    private String activeTurnId;
    /** The id of the running turn the user asked to cancel, or null when no cancel is pending. */
    private String cancelledTurnId;
    /** Guards the cancel handler so a second click cannot stack a confirm dialog. */
    private boolean cancelling;
    /**
     * Waiting intents keyed by the session queue id assigned on accept. A queued
     * intent is not the active intent, so a running turn's output never leaks into it.
     */
    private final Map<String, Intent> queuedIntents = new HashMap<>();
    /** The id currently being re-opened for editing, so its cancel is not shown as one. */
    private String editingId;
    /**
     * The composer the user types into: the single editable intent, kept at the end of
     * the conversation. Every other intent is read-only history. Tracked explicitly so
     * the composer is reused rather than duplicated when it must be re-created (after a
     * resume rebuilds the transcript) or refilled (after editing a queued request).
     */
    private Intent composer;
    /** Guards {@link #ensureComposer()} so a concurrent caller does not add two. */
    private final AtomicBoolean buildingComposer = new AtomicBoolean();
    /** Output that arrived before its turn was promoted; flushed on STARTED. */
    private final StringBuilder pendingOutput = new StringBuilder();
    /**
     * Set when auto-compact interrupted a task. After the summary is stored,
     * that same turn continues on the compacted context.
     */
    private boolean continueAfterCompact;
    /** How many times this task has already been compacted and resumed. */
    private int compactContinuations;
    private static final String CONTINUE_AFTER_COMPACT = """
            The conversation was just compacted to fit the context window. The summary above is your memory. \
            Continue the task from where you left off and finish it. Do not repeat the summary. \
            If you were about to call a tool, call it now.""";
    /** The hint window for showing argument hints of slash commands. */
    private final HintWindow hintWindow;

    /**
     * Constructor.
     * @param agent the agent.
     * @param workspace the workspace that hosts the file tabs.
     */
    public AgentCLI(Agent agent, Workspace workspace) {
        super(new BorderLayout());
        this.agent = agent;
        this.workspace = workspace;
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

        composer = new Intent(this);
        intents.add(composer);
        intents.add(Box.createVerticalGlue());
        if (agent != null) {
            var def = SmileStudio.llmServices().defaultModel();
            if (def != null) {
                String existing = agent.conversation().params().getProperty(LLM.MODEL, "");
                if (existing == null || existing.isBlank()) {
                    agent.conversation().params().setProperty(LLM.MODEL, def.model().id());
                }
            }
            // Deliver queue events on the EDT. This is what makes the id returned by
            // accept(...) bind before the deferred ENQUEUED event runs, and it keeps
            // ENQUEUED ahead of STARTED (a single invokeLater queue is FIFO).
            agent.session().setEventDispatcher(SwingUtilities::invokeLater);
            agent.session().addListener(sessionListener());
        }
    }

    /** Renders queued requests, notices, and stream progress for this agent. */
    private AgentListener sessionListener() {
        return new AgentListener() {
            @Override
            public void onQueueChanged(AgentRequestQueue.Event event) {
                // Already delivered on the EDT by the session dispatcher, so run inline.
                onQueueEvent(event);
            }

            @Override
            public void onNotice(AgentRequest request) {
                onEdtNow(() -> openRequest(request.task()).setProgress(false));
            }

            @Override
            public void onSkipped(AgentRequest request, String reason) {
                // The SKIPPED queue event renders the reason; nothing to add here.
            }

            @Override
            public void onStarted(String runId, String label) {
                onEdtNow(() -> {
                    if (runId == null) {
                        // A top-level turn started; onQueueEvent(STARTED) promotes the
                        // matching intent to activeIntent and re-arms the progress bar.
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
                    } else if (runId == null) {
                        // A synchronous LLM can stream before STARTED is delivered. Hold
                        // the chunk and flush it once the turn is promoted, so the first
                        // tokens are not dropped.
                        pendingOutput.append(chunk);
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
                        // A turn the user cancelled ends in a distinct terminal state rather
                        // than reporting token counts it never finished producing.
                        if (activeTurnId != null && activeTurnId.equals(cancelledTurnId)) {
                            activeIntent.setStatus(Intent.queuedMessage("Cancelled"));
                        } else if (outputTokens > 0) {
                            activeIntent.setStatus(outputTokens + " output tokens");
                        }
                    }
                    activeTurnId = null;
                    cancelledTurnId = null;
                    boolean alreadyCompacted = "true".equals(agent.conversation().params().getProperty(LLM.COMPACTED));
                    agent.conversation().params().remove(LLM.COMPACTED);
                    if (continueAfterCompact) {
                        continueAfterCompact = false;
                        boolean interrupted = "true".equals(agent.conversation().params().getProperty(LLM.INTERRUPTED));
                        if (alreadyCompacted && !interrupted && activeIntent != null) {
                            activeIntent.output().append("\n\n[Context compacted. Continuing the task.]\n");
                            activeIntent.setStatus("Continuing...");
                            chat(activeIntent, CONTINUE_AFTER_COMPACT);
                        } else if (!alreadyCompacted && activeIntent != null) {
                            activeIntent.output().append("\n\n[Compaction did not produce a summary, so the task was not resumed.]\n");
                        }
                        return;
                    }
                    long compactAt = LLM.DEFAULT_COMPACT_THRESHOLD;
                    try {
                        compactAt = agent.turnModel().compactThreshold();
                    } catch (RuntimeException ignored) {
                        var resolved = activeIntent != null ? activeIntent.resolveModel() : null;
                        if (resolved != null) {
                            compactAt = resolved.model().compactThreshold();
                        }
                    }
                    if (!alreadyCompacted && (totalTokens > compactAt || (totalTokens > 0 && outputTokens == 0)) && activeIntent != null) {
                        if (compactContinuations >= 2) {
                            compactContinuations = 0;
                            activeIntent.output().append("\n\n[The conversation is still too long after compaction, so the task was not resumed.]\n");
                            return;
                        }
                        compactContinuations++;
                        activeIntent.output().append("\n\n[The conversation session is too long, a compact command will be executed to summarize conversation.]\n");
                        continueAfterCompact = true;
                        compact("", activeIntent);
                    } else {
                        compactContinuations = 0;
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

    /**
     * Renders one queue change on the EDT. The session owns queue state; this method
     * only reflects it. It is id-keyed, so it never guesses which intent a turn belongs
     * to from arrival order.
     */
    private void onQueueEvent(AgentRequestQueue.Event event) {
        QueuedRequest item = event.item();
        String id = item.id();
        switch (event.action()) {
            case ENQUEUED -> {
                Intent intent = queuedIntents.get(id);
                if (intent == null) {
                    // Not a local submit we bound already: it arrived from a peer agent.
                    // prompt() renders a request or an event, so this stays null-safe if
                    // an event ever reaches this session.
                    intent = openRequest(item.prompt());
                    queuedIntents.put(id, intent);
                }
                wireQueueControls(intent, id, item);
                intent.showQueued(event.position(), event.size());
                showQueueDepth(event.size());
            }
            case STARTED -> {
                Intent intent = queuedIntents.remove(id);
                if (intent != null) {
                    activeIntent = intent;
                    activeTurnId = id;
                }
                if (activeIntent != null) {
                    activeIntent.clearQueued();
                    activeIntent.setProgress(true);
                    activeIntent.setStatus("Thinking...");
                    // Flush output that arrived before this turn was promoted.
                    if (!pendingOutput.isEmpty()) {
                        activeIntent.appendRun(null, pendingOutput.toString());
                        pendingOutput.setLength(0);
                    }
                }
                showQueueDepth(event.size());
            }
            case CANCELLED, SKIPPED -> {
                boolean editing = id.equals(editingId);
                editingId = null;
                Intent intent = queuedIntents.remove(id);
                if (intent != null) {
                    intent.clearQueued();
                    intent.setProgress(false);
                    if (!editing) {
                        // An edit already re-opened the composer; only a real cancel or a
                        // skip prints a reason. An event has no task text, so fall back to
                        // its subject.
                        intent.output().append(event.action() == AgentRequestQueue.Action.CANCELLED
                                ? Intent.queuedMessage("CancelledQueued")
                                : item.isEvent() ? item.event().subject() : item.request().task());
                    }
                }
                showQueueDepth(event.size());
            }
            case REORDERED -> {
                for (QueuedRequest waiting : agent.session().queued()) {
                    Intent intent = queuedIntents.get(waiting.id());
                    if (intent != null) {
                        intent.showQueued(agent.session().queuePosition(waiting.id()),
                                agent.session().queued().size());
                    }
                }
                showQueueDepth(event.size());
            }
            case NOTICE -> { /* shown via onNotice */ }
        }
    }

    /** Wires cancel/edit/reorder on a waiting intent. Edit is offered only for local prompts. */
    private void wireQueueControls(Intent intent, String id, QueuedRequest item) {
        // from() is blank for an event, so an event would look "local" and be offered an
        // edit that folds it into the composer as a user prompt. Exclude it explicitly.
        boolean local = !item.isEvent() && item.from().isBlank();
        int position = agent.session().queuePosition(id);
        int size = agent.session().queued().size();
        Runnable moveUp = () -> agent.session().move(id, -1);
        Runnable moveDown = () -> agent.session().move(id, 1);
        Runnable edit = local ? () -> editQueued(id) : null;
        intent.setQueueControls(() -> agent.session().cancel(id), edit, moveUp, moveDown);
        intent.setQueueControlsEnabled(position > 1, position < size);
    }

    /**
     * Shows the total number of waiting requests in the composer status line. The
     * composer is the trailing editable intent, not the running turn, so this never
     * clobbers the running turn's own status.
     */
    private void showQueueDepth(int size) {
        if (composer == null || composer.getParent() != intents) {
            return;
        }
        composer.setStatus(size > 0 ? Intent.queuedMessage("QueueDepth", size) : "");
    }

    /**
     * Removes a queued request and returns its prompt to the composer for editing. The
     * cancel drives the same CANCELLED path as the cancel button (the event handler
     * clears the badge and drops the id from the map).
     * <p>The prompt is folded into the existing composer rather than turning the queued
     * widget editable in place. A second editable intent would leave two composers in
     * the tab -- the one the user types into plus the edited one -- and every later
     * submit would append yet another. The queued widget is dropped and the composer
     * takes over with the prompt loaded and the caret at its end.
     */
    private void editQueued(String id) {
        Intent intent = queuedIntents.get(id);
        if (intent == null) {
            return;
        }
        // Mark it so the CANCELLED handler re-opens it instead of reporting a cancel.
        editingId = id;
        agent.session().cancel(id);
        String text = intent.editor().getText();
        // Run on the EDT after the CANCELLED event, so the queued widget is already
        // un-badged when it is removed.
        SwingUtilities.invokeLater(() -> {
            intents.remove(intent);
            Intent target = composerOrCreate();
            if (target == null) {
                intents.revalidate();
                return;
            }
            target.editor().setText(text);
            target.editor().setCaretPosition(text.length());
            target.setEditable(true);
            composer = target;
            intents.revalidate();
            intents.repaint();
            target.editor().requestFocusInWindow();
        });
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
     * Scrolls the conversation to the bottom so a just-appended turn is visible
     * without the user having to drag the scrollbar.
     * <p>Adding an {@link Intent} grows the {@code BoxLayout} panel but touches
     * neither the caret nor the outer scrollbar, so nothing scrolls on its own.
     * The view only reaches the bottom when output tokens make the caret follow
     * the text -- the delay this method removes. Called when a submit appends a
     * turn, before any output has streamed.
     * <p>The scroll is deferred with {@link SwingUtilities#invokeLater} so the
     * pending layout pass has resized the viewport and the scrollbar's maximum
     * reflects the new content; scrolling in the same frame would clamp to the
     * old maximum and stop short.
     */
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, intents);
            if (scroll != null) {
                JScrollBar bar = scroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    /**
     * Inserts a read-only intent for work that arrived from another agent
     * or as a notice, just above the empty composer. Keeping the composer as
     * the last intent lets the user find it easily to type a new prompt.
     * @param text the prompt or notice.
     * @return the new intent.
     */
    public Intent openRequest(String text) {
        Intent intent = new Intent(this);
        intent.editor().setText(text);
        intent.setEditable(false);
        intents.add(intent, composerIndex());
        intents.revalidate();
        scrollToBottom();
        return intent;
    }

    /**
     * Returns the index at which a new intent should be inserted so that the
     * empty composer stays the last intent in the conversation. The trailing
     * vertical glue is not an intent and is skipped.
     * @return the insertion index.
     */
    private int composerIndex() {
        int count = intents.getComponentCount();
        int index = count;
        while (index > 0 && !(intents.getComponent(index - 1) instanceof Intent)) {
            index--;
        }
        while (index > 0 && isComposer((Intent) intents.getComponent(index - 1))) {
            index--;
        }
        return index;
    }

    /** Returns true if the intent is the active composer the user types into. */
    private static boolean isComposer(Intent intent) {
        return intent.editor().isEditable();
    }

    /**
     * Returns the editable intents in the conversation. Normally there is exactly one:
     * the composer at the end. Package-private for testing.
     * @return the editable intents, in order.
     */
    List<Intent> composers() {
        List<Intent> editable = new ArrayList<>();
        for (Component component : intents.getComponents()) {
            if (component instanceof Intent intent && intent.editor().isEditable()) {
                editable.add(intent);
            }
        }
        return editable;
    }

    /** Returns the conversation's intents in order. Package-private for testing. */
    List<Intent> intentList() {
        List<Intent> all = new ArrayList<>();
        for (Component component : intents.getComponents()) {
            if (component instanceof Intent intent) {
                all.add(intent);
            }
        }
        return all;
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

    /** Refreshes model and effort combos on every Intent after settings change. */
    public void refreshModels() {
        for (Component component : intents.getComponents()) {
            if (component instanceof Intent intent) {
                intent.refreshModels();
            }
        }
    }

    /** Append a new intent box, keeping it as the last intent in the conversation. */
    public void addIntent() {
        ensureComposer();
    }

    /**
     * Makes sure the conversation has exactly one editable composer, at the end.
     * <p>If the tracked composer is still editable and in the panel, it stays; a repeat
     * call is a no-op. Otherwise (after a resume cleared it, for example) a fresh one is
     * appended. Repeated calls therefore do not stack a second editable intent -- the
     * bug that left two active widgets in a tab.
     */
    private void ensureComposer() {
        if (!buildingComposer.compareAndSet(false, true)) {
            return;
        }
        try {
            if (composer != null && composer.editor().isEditable()
                    && composer.getParent() == intents) {
                return;
            }
            Intent intent = new Intent(this);
            intents.add(intent, composerIndex());
            composer = intent;
            intents.revalidate();
            // The new composer sits below the submitted turn; keep it in view so the
            // user can see where to type the next prompt.
            scrollToBottom();
            SwingUtilities.invokeLater(() -> intent.editor().requestFocusInWindow());
        } finally {
            buildingComposer.set(false);
        }
    }

    /**
     * Returns the composer the user types into, creating one if the conversation has
     * none (for example after {@link #showResumedSession} rebuilt the transcript).
     * @return the composer, or null when there is no agent.
     */
    private Intent composerOrCreate() {
        if (composer == null || !composer.editor().isEditable() || composer.getParent() != intents) {
            ensureComposer();
        }
        return composer;
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
        hints.put("/memory edit", "[ENTER to open a tab to edit the long term memory]");
        hints.put("/memory refresh", "[ENTER to reload the context from disk]");
        hints.put("/compact", "[instructions]");
        hints.put("/resume", "[ENTER to choose a previous session]");
        hints.put("/plan", "[off|short description of goals or tasks]");
        hints.put("/edit", "[file path]");
        hints.put("/train", "[ENTER for helps]");
        hints.put("/predict", "[ENTER for helps]");
        hints.put("/plugin", "[marketplace|install|list|enable|disable|uninstall|mcp]");

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
                case "train", "predict" -> runShellCommand(intent, IntentType.Command, instructions);
                case "memory" -> memory(args, instructions, intent);
                case "system" -> showSystemPrompt(intent.output()); // for debugging
                case "clear" -> clear(intent.output());
                case "resume" -> resume(intent.output());
                case "compact" -> compact(instructions, intent);
                case "plan" -> plan(args, instructions, intent.output());
                case "plugin" -> plugin(args, intent.output());
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
        workspace.openFile(Path.of(path));
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
                /memory edit        Open a tab to edit the long term memory
                /memory refresh     Reload the context from disk
                /plan               Enter the plan mode.
                /plan off           Exit  the plan mode.
                /clear              Clear the current conversation session.
                /resume             Choose a previous session and restore its context.
                /compact            Summarize the conversation and retain critical details.
                /edit               Edit a file in a tab.
                /train              Train a machine learning model
                /predict            Run batch inference
                /plugin             Manage plugins and marketplaces""");

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

    /**
     * Runs a plugin subcommand ({@code /plugin ...}). Plugin management is
     * independent of the agent, so it does not require an AI service.
     *
     * @param args the arguments after {@code /plugin}.
     * @param output the output area.
     */
    private void plugin(String[] args, OutputArea output) {
        var service = new smile.studio.plugin.PluginService(
                Path.of(System.getProperty("user.dir")));
        var rest = java.util.Arrays.asList(args).subList(1, args.length);
        output.setText(service.execute(rest));
    }

    /** Executes memory commands. */
    private void memory(String[] args, String instructions, Intent intent) throws IOException {        var output = intent.output();
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

    /** Open a tab to edit the project's long term memory. */
    private void editMemory(OutputArea output) {
        workspace.openFile(agent.context().path().resolve(Context.SMILE_MD));
        output.setText("SMILE.md is opened in a tab. Edit and save the file to update the long term memory.");
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
        // The old composer was removed with everything else; forget it so the next
        // addIntent()/composerOrCreate() builds a fresh one instead of reusing a widget
        // that is no longer in the panel.
        composer = null;
        for (Intent banner : banners) {
            intents.add(banner);
        }

        Intent current = null;
        StringBuilder body = new StringBuilder();
        for (Message message : transcript(agent.conversation().messages())) {
            String text = (String) message.content();
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
        scrollToBottom();
    }

    /**
     * Selects the messages worth showing in the resumed transcript: the user's
     * prompts and the assistant's text. Tool calls, including their full output,
     * system messages, and error markers are dropped. They are the bulk of a long
     * session and add little to a quick recap, while the agent still has the
     * complete history in its conversation. Kept package-private and pure so the
     * selection and ordering can be tested without a live agent.
     * @param messages the full conversation history, in order.
     * @return the user and assistant text messages, in order.
     */
    static List<Message> transcript(List<Message> messages) {
        List<Message> shown = new ArrayList<>();
        for (Message message : messages) {
            if (message.role() == Role.user || message.role() == Role.assistant) {
                if (message.content() instanceof String text && !text.isBlank()) {
                    shown.add(message);
                }
            }
        }
        return shown;
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
        String extra = instructions == null ? "" : instructions.replaceFirst("^compact\\s*", "").trim();
        var prompt = ioa.llm.Conversation.compactPrompt();
        if (prompt.isBlank()) {
            continueAfterCompact = false;
            logger.error("No compact instructions specified.");
            return;
        }

        if (!extra.isBlank()) {
            prompt = prompt + "\n\n" + extra;
        }

        intent.setStatus("Compacting...");
        chat(intent, prompt);
    }

    private void chat(Intent intent, String prompt) {
        if (prompt.isBlank()) {
            intent.output().setText(bundle.getString("Hello"));
            return;
        }

        LlmServices.AvailableModel available = intent.resolveModel();
        if (agent == null || available == null || available.client() == null) {
            intent.output().setText(bundle.getString("NoAIServiceError"));
            return;
        }

        if (LLM.DEFAULT_REASONING_EFFORT.equals(reasoningEffort)) {
            agent.conversation().params().setProperty(LLM.REASONING_EFFORT, "");
        } else {
            agent.conversation().params().setProperty(LLM.REASONING_EFFORT, reasoningEffort);
        }
        agent.conversation().params().setProperty(LLM.MODEL, available.model().id());

        // Do NOT set activeIntent here. A submitted turn is queued, not running; only
        // the STARTED queue event promotes it. Setting it now would let a running turn's
        // output stream into this newly queued intent.
        intent.setStopAction(() -> {
            cancelTurn();
            return null;
        });

        var result = agent.session().accept(
                AgentRequest.fromUser(agent.session().callName(), prompt),
                available.client(),
                available.model());
        if (result.ok()) {
            // Bind the id before the deferred ENQUEUED handler runs so it updates this
            // intent instead of opening a second one.
            queuedIntents.put(result.id(), intent);
        }
    }

    /**
     * Cancels the running turn, and asks whether to drop the requests still waiting
     * behind it. The interrupt flag makes the runtime abort the in-flight stream and
     * stop running subagents; dropping the queue is a separate, destructive choice the
     * user confirms.
     */
    private void cancelTurn() {
        if (cancelling) {
            return;
        }
        cancelling = true;
        try {
            String turnId = activeTurnId;
            agent.conversation().params().setProperty(LLM.INTERRUPTED, "true");
            if (turnId != null) {
                cancelledTurnId = turnId;
            }
            boolean drop = false;
            if (!agent.session().queued().isEmpty()) {
                drop = openConfirm(bundle.getString("CancelConfirmTitle"),
                        Intent.queuedMessage("ConfirmDropQueue", agent.session().queued().size()));
            }
            if (drop) {
                for (QueuedRequest waiting : List.copyOf(agent.session().queued())) {
                    agent.session().cancel(waiting.id());
                }
            }
        } finally {
            cancelling = false;
        }
    }

    /** Shows a modal Yes/No dialog and returns true when the user chose Yes. */
    private boolean openConfirm(String title, String message) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner == null || !owner.isDisplayable()) {
            // No visible window (headless or a detached tab). Default to the safe,
            // non-destructive choice: keep the queued work.
            return false;
        }
        int choice = JOptionPane.showConfirmDialog(owner, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }
}
