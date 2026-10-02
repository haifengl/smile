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
package smile.studio.cli;

import java.util.ArrayList;
import java.util.List;
import ioa.llm.Message;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the message selection behind the /resume transcript.
 *
 * @author Haifeng Li
 */
class AgentCLITest {

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

        List<Message> shown = AgentCLI.transcript(messages);

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

        assertTrue(AgentCLI.transcript(messages).isEmpty());
    }

    @Test
    void transcriptPreservesOrder() {
        List<Message> messages = List.of(
                Message.user("first"),
                Message.assistant("second"),
                Message.user("third"),
                Message.assistant("fourth"));

        List<Object> contents = new ArrayList<>();
        for (Message message : AgentCLI.transcript(messages)) {
            contents.add(message.content());
        }

        assertEquals(List.of("first", "second", "third", "fourth"), contents);
    }

    @Test
    void addIntentReusesTheExistingComposerInsteadOfStacking() {
        // Regression: repeated addIntent() calls used to append a fresh editable intent
        // each time, so a tab could end up with two active Intent widgets.
        AgentCLI cli = new AgentCLI(null, null);
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
}
