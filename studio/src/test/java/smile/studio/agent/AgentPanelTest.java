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
 */
package smile.studio.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import ioa.agent.Agent;
import ioa.llm.Conversation;
import ioa.llm.Message;
import ioa.llm.Model;
import ioa.llm.client.LLM;
import ioa.llm.client.StreamResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import smile.studio.LlmServices;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AgentPanel}, including /resume transcript filtering,
 * composer reuse, and queued intent ordering during auto-compaction.
 *
 * @author Haifeng Li
 */
class AgentPanelTest {

    @TempDir
    Path tempDir;

    @Test
    void transcriptKeepsOnlyUserAndAssistantText() {
        List<Message> messages = List.of(
                Message.system("system prompt"),
                Message.user("do the thing"),
                Message.assistant("done"),
                // A tool turn, with the formatted call and its full output, is the bulk
                // of a long session and must not appear in the recap.
                Message.toolCall("tool call + output", List.of()),
                Message.error("boom"));

        List<Message> shown = AgentPanel.transcript(messages);

        assertEquals(2, shown.size());
        assertEquals("do the thing", shown.get(0).content());
        assertEquals("done", shown.get(1).content());
    }

    @Test
    void transcriptDropsBlankTextAndNonTextContent() {
        List<Message> messages = List.of(
                Message.user("   \n  "),
                Message.assistant(""),
                Message.toolCall("something", List.of()));

        assertTrue(AgentPanel.transcript(messages).isEmpty());
    }

    @Test
    void transcriptPreservesOrder() {
        List<Message> messages = List.of(
                Message.user("first"),
                Message.assistant("second"),
                Message.user("third"),
                Message.assistant("fourth"));

        List<Object> contents = new ArrayList<>();
        for (Message message : AgentPanel.transcript(messages)) {
            contents.add(message.content());
        }

        assertEquals(List.of("first", "second", "third", "fourth"), contents);
    }

    @Test
    void addIntentReusesTheExistingComposerInsteadOfStacking() {
        // Regression: repeated addIntent() calls used to append a fresh editable intent
        // each time, so a tab could end up with two active Intent widgets.
        AgentPanel cli = new AgentPanel(null, null);
        assertEquals(1, cli.composers().size(), "a new CLI starts with one composer");

        cli.addIntent();
        cli.addIntent();

        assertEquals(1, cli.composers().size(),
                "addIntent() must not add a second editable intent");
        // The single composer stays last, above the trailing glue.
        List<Intent> all = cli.intentList();
        assertTrue(all.get(all.size() - 1).editor().isEditable(),
                "the composer must remain the last intent");
    }

    @Test
    void autoCompactAndContinuationRunBeforeQueuedIntent() throws Exception {
        // Given a running intent that will exceed the compaction threshold and a queued second intent
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch compactObserved = new CountDownLatch(1);
        CountDownLatch releaseCompact = new CountDownLatch(1);
        CountDownLatch continueObserved = new CountDownLatch(1);
        CountDownLatch releaseContinue = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);

        List<String> order = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean firstIntentQueuedDuringCompact = new AtomicBoolean();
        AtomicBoolean secondIntentQueuedDuringCompact = new AtomicBoolean();
        AtomicBoolean firstIntentQueuedDuringContinue = new AtomicBoolean();
        AtomicBoolean secondIntentQueuedDuringContinue = new AtomicBoolean();

        LLM fakeLlm = new LLM() {
            @Override
            public boolean initSession(Conversation conversation) {
                return false;
            }

            @Override
            public CompletableFuture<String> complete(String prompt, Properties params, Model model) {
                order.add("COMPACT");
                return CompletableFuture.supplyAsync(() -> {
                    compactObserved.countDown();
                    await(releaseCompact);
                    return "<summary>Compacted summary of first task.</summary>";
                });
            }

            @Override
            public void complete(String prompt, Conversation conversation,
                                 StreamResponseHandler response, Model model) {
                if ("first task".equals(prompt)) {
                    order.add("first task");
                    conversation.add(Message.user(prompt));
                    conversation.add(Message.assistant("partial first output"));
                    new Thread(() -> {
                        firstStarted.countDown();
                        await(releaseFirst);
                        response.onNext("partial first output");
                        response.onComplete(model.compactThreshold() + 1_000, 10, model.compactThreshold() + 990);
                    }, "test-first-turn").start();
                } else if (prompt.startsWith("The conversation was just compacted")) {
                    order.add("CONTINUE");
                    conversation.add(Message.user(prompt));
                    conversation.add(Message.assistant("finished first task"));
                    new Thread(() -> {
                        continueObserved.countDown();
                        await(releaseContinue);
                        response.onNext("finished first task");
                        response.onComplete(500, 20, 480);
                    }, "test-continue-turn").start();
                } else {
                    order.add(prompt);
                    conversation.add(Message.user(prompt));
                    conversation.add(Message.assistant("second output"));
                    new Thread(() -> {
                        response.onNext("second output");
                        response.onComplete(200, 15, 185);
                        secondDone.countDown();
                    }, "test-second-turn").start();
                }
            }
        };

        Agent agent = testAgent(fakeLlm);
        LlmServices.AvailableModel model = new LlmServices.AvailableModel("test", "Test", agent.turnModel(), fakeLlm);

        AgentPanel[] panelRef = new AgentPanel[1];
        Intent[] intent1Ref = new Intent[1];
        Intent[] intent2Ref = new Intent[1];

        SwingUtilities.invokeAndWait(() -> {
            AgentPanel panel = new AgentPanel(agent, null);
            panelRef[0] = panel;
            Intent intent1 = panel.composers().get(0);
            intent1.setSelectedModel(model);
            intent1.editor().setText("first task");
            intent1.setEditable(false);
            intent1Ref[0] = intent1;
            panel.addIntent();
            panel.run(intent1, IntentType.Instructions);
        });

        assertTrue(firstStarted.await(5, TimeUnit.SECONDS), "first intent should start");
        flushEdt();

        // Queue second intent while first intent is running
        SwingUtilities.invokeAndWait(() -> {
            AgentPanel panel = panelRef[0];
            Intent intent2 = panel.composers().get(0);
            intent2.setSelectedModel(model);
            intent2.editor().setText("second task");
            intent2.setEditable(false);
            intent2Ref[0] = intent2;
            panel.addIntent();
            panel.run(intent2, IntentType.Instructions);
        });
        flushEdt();
        assertTrue(intent2Ref[0].queuePane().isVisible(), "second intent should be queued");

        // When first intent finishes over the compaction threshold
        releaseFirst.countDown();

        // Then compaction runs next before the second intent, without marking intent1 as queued
        assertTrue(compactObserved.await(5, TimeUnit.SECONDS), "compaction should start immediately");
        SwingUtilities.invokeAndWait(() -> {
            firstIntentQueuedDuringCompact.set(intent1Ref[0].queuePane().isVisible());
            secondIntentQueuedDuringCompact.set(intent2Ref[0].queuePane().isVisible());
        });
        assertFalse(firstIntentQueuedDuringCompact.get(), "active intent must not show queued controls during compaction");
        assertTrue(secondIntentQueuedDuringCompact.get(), "second intent must stay queued during compaction");
        assertEquals(List.of("first task", "COMPACT"), new ArrayList<>(order));

        // Release compaction -> continuation must run before the second intent
        releaseCompact.countDown();
        assertTrue(continueObserved.await(5, TimeUnit.SECONDS), "continuation should start immediately after compaction");
        SwingUtilities.invokeAndWait(() -> {
            firstIntentQueuedDuringContinue.set(intent1Ref[0].queuePane().isVisible());
            secondIntentQueuedDuringContinue.set(intent2Ref[0].queuePane().isVisible());
        });
        assertFalse(firstIntentQueuedDuringContinue.get(), "active intent must not show queued controls during continuation");
        assertTrue(secondIntentQueuedDuringContinue.get(), "second intent must stay queued during continuation");
        assertEquals(List.of("first task", "COMPACT", "CONTINUE"), new ArrayList<>(order));

        // Release continuation -> second intent finally runs and finishes
        releaseContinue.countDown();
        assertTrue(secondDone.await(5, TimeUnit.SECONDS), "second intent should finish after first intent completes");
        flushEdt();

        assertEquals(List.of("first task", "COMPACT", "CONTINUE", "second task"), new ArrayList<>(order));
        assertFalse(intent2Ref[0].queuePane().isVisible(), "second intent queue badge should clear once it runs");
    }

    @Test
    void manualCompactSlashCommandStaysQueuedBehindEarlierIntent() throws Exception {
        // Given a running intent, a queued second intent, and a user-submitted /compact behind it
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch compactDone = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        LLM fakeLlm = new LLM() {
            @Override
            public boolean initSession(Conversation conversation) {
                return false;
            }

            @Override
            public CompletableFuture<String> complete(String prompt, Properties params, Model model) {
                order.add("COMPACT");
                return CompletableFuture.supplyAsync(() -> {
                    compactDone.countDown();
                    return "<summary>Manual compact summary.</summary>";
                });
            }

            @Override
            public void complete(String prompt, Conversation conversation,
                                 StreamResponseHandler response, Model model) {
                order.add(prompt);
                conversation.add(Message.user(prompt));
                conversation.add(Message.assistant("reply to " + prompt));
                new Thread(() -> {
                    if ("first task".equals(prompt)) {
                        firstStarted.countDown();
                        await(releaseFirst);
                    }
                    response.onNext("reply to " + prompt);
                    response.onComplete(100, 10, 90);
                }, "test-manual-compact").start();
            }
        };

        Agent agent = testAgent(fakeLlm);
        LlmServices.AvailableModel model = new LlmServices.AvailableModel("test", "Test", agent.turnModel(), fakeLlm);

        AgentPanel[] panelRef = new AgentPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            AgentPanel panel = new AgentPanel(agent, null);
            panelRef[0] = panel;
            Intent intent1 = panel.composers().get(0);
            intent1.setSelectedModel(model);
            intent1.editor().setText("first task");
            intent1.setEditable(false);
            panel.addIntent();
            panel.run(intent1, IntentType.Instructions);
        });

        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        flushEdt();

        SwingUtilities.invokeAndWait(() -> {
            AgentPanel panel = panelRef[0];
            Intent intent2 = panel.composers().get(0);
            intent2.setSelectedModel(model);
            intent2.editor().setText("second task");
            intent2.setEditable(false);
            panel.addIntent();
            panel.run(intent2, IntentType.Instructions);

            Intent intent3 = panel.composers().get(0);
            intent3.setSelectedModel(model);
            intent3.editor().setText("compact");
            intent3.setEditable(false);
            panel.addIntent();
            panel.run(intent3, IntentType.Command);
        });
        flushEdt();

        // When first intent finishes normally
        releaseFirst.countDown();
        assertTrue(compactDone.await(5, TimeUnit.SECONDS));
        waitUntilIdle(agent);
        flushEdt();

        // Then user-queued /compact preserves FIFO order behind the second intent
        assertEquals(List.of("first task", "second task", "COMPACT"), new ArrayList<>(order));
    }

    @Test
    void failedAutoCompactResetsStateBeforeQueuedIntent() throws Exception {
        // Given a running intent whose auto-compaction fails and a queued second intent
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        LLM fakeLlm = new LLM() {
            @Override
            public boolean initSession(Conversation conversation) {
                return false;
            }

            @Override
            public CompletableFuture<String> complete(String prompt, Properties params, Model model) {
                order.add("COMPACT");
                return CompletableFuture.failedFuture(new RuntimeException("compaction boom"));
            }

            @Override
            public void complete(String prompt, Conversation conversation,
                                 StreamResponseHandler response, Model model) {
                order.add(prompt);
                conversation.add(Message.user(prompt));
                conversation.add(Message.assistant("reply"));
                new Thread(() -> {
                    if ("first task".equals(prompt)) {
                        firstStarted.countDown();
                        await(releaseFirst);
                        response.onComplete(model.compactThreshold() + 1_000, 10, model.compactThreshold() + 990);
                    } else {
                        response.onNext("second ok");
                        response.onComplete(100, 10, 90);
                        secondDone.countDown();
                    }
                }, "test-failed-compact").start();
            }
        };

        Agent agent = testAgent(fakeLlm);
        LlmServices.AvailableModel model = new LlmServices.AvailableModel("test", "Test", agent.turnModel(), fakeLlm);

        AgentPanel[] panelRef = new AgentPanel[1];
        Intent[] intent2Ref = new Intent[1];
        SwingUtilities.invokeAndWait(() -> {
            AgentPanel panel = new AgentPanel(agent, null);
            panelRef[0] = panel;
            Intent intent1 = panel.composers().get(0);
            intent1.setSelectedModel(model);
            intent1.editor().setText("first task");
            intent1.setEditable(false);
            panel.addIntent();
            panel.run(intent1, IntentType.Instructions);
        });

        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        flushEdt();

        SwingUtilities.invokeAndWait(() -> {
            AgentPanel panel = panelRef[0];
            Intent intent2 = panel.composers().get(0);
            intent2.setSelectedModel(model);
            intent2.editor().setText("second task");
            intent2.setEditable(false);
            intent2Ref[0] = intent2;
            panel.addIntent();
            panel.run(intent2, IntentType.Instructions);
        });
        flushEdt();

        // When first intent triggers compaction that fails
        releaseFirst.countDown();
        assertTrue(secondDone.await(5, TimeUnit.SECONDS), "second intent should still run after failed compaction");
        waitUntilIdle(agent);
        flushEdt();

        // Then compaction ran before second task and did not leak continueAfterCompact into intent2
        assertEquals(List.of("first task", "COMPACT", "second task"), new ArrayList<>(order));
        assertFalse(intent2Ref[0].output().getText().contains("Compaction did not produce a summary"),
                "failed compaction state must not leak onto the next intent");
    }

    @Test
    void interruptedTurnDoesNotAutoCompactBeforeQueuedIntent() throws Exception {
        // Given a running intent that gets interrupted and a queued second intent
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        LLM fakeLlm = new LLM() {
            @Override
            public boolean initSession(Conversation conversation) {
                return false;
            }

            @Override
            public CompletableFuture<String> complete(String prompt, Properties params, Model model) {
                order.add("COMPACT");
                return CompletableFuture.completedFuture("<summary>unexpected</summary>");
            }

            @Override
            public void complete(String prompt, Conversation conversation,
                                 StreamResponseHandler response, Model model) {
                order.add(prompt);
                conversation.add(Message.user(prompt));
                new Thread(() -> {
                    if ("first task".equals(prompt)) {
                        firstStarted.countDown();
                        await(releaseFirst);
                        conversation.params().setProperty(LLM.INTERRUPTED, "true");
                        response.onComplete(model.compactThreshold() + 1_000, 0, model.compactThreshold() + 1_000);
                    } else {
                        response.onNext("second ok");
                        response.onComplete(100, 10, 90);
                        secondDone.countDown();
                    }
                }, "test-interrupted-turn").start();
            }
        };

        Agent agent = testAgent(fakeLlm);
        LlmServices.AvailableModel model = new LlmServices.AvailableModel("test", "Test", agent.turnModel(), fakeLlm);

        AgentPanel[] panelRef = new AgentPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            AgentPanel panel = new AgentPanel(agent, null);
            panelRef[0] = panel;
            Intent intent1 = panel.composers().get(0);
            intent1.setSelectedModel(model);
            intent1.editor().setText("first task");
            intent1.setEditable(false);
            panel.addIntent();
            panel.run(intent1, IntentType.Instructions);
        });

        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        flushEdt();

        SwingUtilities.invokeAndWait(() -> {
            AgentPanel panel = panelRef[0];
            Intent intent2 = panel.composers().get(0);
            intent2.setSelectedModel(model);
            intent2.editor().setText("second task");
            intent2.setEditable(false);
            panel.addIntent();
            panel.run(intent2, IntentType.Instructions);
        });
        flushEdt();

        // When first intent is interrupted
        releaseFirst.countDown();
        assertTrue(secondDone.await(5, TimeUnit.SECONDS), "second intent should run after interrupted first intent");
        waitUntilIdle(agent);
        flushEdt();

        // Then auto-compaction does not run on the cancelled turn
        assertEquals(List.of("first task", "second task"), new ArrayList<>(order));
    }

    private Agent testAgent(LLM llm) throws Exception {
        Path dir = tempDir.resolve("agent-" + System.nanoTime());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("AGENT.md"), """
                ---
                name: tester
                description: test agent
                model: gpt-4o
                ---
                Test.
                """);
        return new Agent(Agent.Spec.from(dir), () -> llm, tempDir.resolve("work-" + System.nanoTime()));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "timed out waiting for test latch");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail(ex);
        }
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static void waitUntilIdle(Agent agent) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (agent.session().isBusy() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(agent.session().isBusy(), "session should become idle");
    }
}
