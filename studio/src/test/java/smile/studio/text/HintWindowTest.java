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
package smile.studio.text;

import java.util.Map;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the hint trigger text extracted at the caret.
 *
 * @author Haifeng Li
 */
public class HintWindowTest {

    @Test
    public void leadingTextReturnsTheCaretLinePrefix() throws Exception {
        JTextArea area = new JTextArea();
        area.setText("/memory show\n/plan off");
        int dot = area.getText().indexOf("off") + "off".length();

        assertEquals("/plan off", HintWindow.leadingText(area, dot),
                "the trigger is the text from the line start up to the caret");
    }

    @Test
    public void leadingTextDoesNotReadPastTheDocumentOnALaterLine() throws Exception {
        // Regression: the code passed `dot` as the length to getText(start, dot),
        // requesting the range [start, start + dot). Once the document was shorter
        // than start + dot -- i.e. on almost every space on a multi-line composer --
        // GapContent threw BadLocationException("Invalid location"), logged as a WARN
        // by the caller, and the hint never appeared.
        JTextArea area = new JTextArea();
        area.setText("a long first line of text here\n/memory");
        int dot = area.getText().length();

        assertEquals("/memory", HintWindow.leadingText(area, dot));
    }

    @Test
    public void leadingTextReturnsTheWholeLineForATwoWordTrigger() throws Exception {
        JTextArea area = new JTextArea();
        area.setText("/memory show");
        int dot = area.getText().length();

        assertEquals("/memory show", HintWindow.leadingText(area, dot),
                "a two-word trigger key such as \"/memory show\" must survive intact");
    }

    @Test
    public void leadingTextIsEmptyOnABlankLine() throws Exception {
        JTextArea area = new JTextArea();
        area.setText("/memory\n");
        // Offset 8 is the start of the (empty) second line, so there is no trigger.
        int dot = "/memory\n".length();

        assertEquals("", HintWindow.leadingText(area, dot));
    }

    @Test
    public void hintForKeepsTheHintWhileTypingAnArgument() {
        Map<String, String> hints = Map.of(
                "/memory", "show | add | edit | refresh",
                "/memory add", "the note to append");
        String command = "show | add | edit | refresh";

        assertEquals(command, HintWindow.hintFor(hints, "/memory"),
                "the command hint shows with no argument yet");
        assertEquals(command, HintWindow.hintFor(hints, "/memory "),
                "the hint stays after the trailing space");
        assertEquals(command, HintWindow.hintFor(hints, "/memory ad"),
                "the hint stays while the argument is typed");
    }

    @Test
    public void hintForPrefersTheLongestPrefix() {
        Map<String, String> hints = Map.of(
                "/memory", "show | add | edit | refresh",
                "/memory add", "the note to append");

        assertEquals("the note to append", HintWindow.hintFor(hints, "/memory add"),
                "the fully-typed two-word trigger wins over its shorter prefix");
        assertEquals("the note to append", HintWindow.hintFor(hints, "/memory add "),
                "and still shows while the note is typed");
    }

    @Test
    public void hintForReturnsNullWhenNothingMatches() {
        Map<String, String> hints = Map.of("/memory", "show | add | edit | refresh");

        assertNull(HintWindow.hintFor(hints, "hello world"));
        assertNull(HintWindow.hintFor(hints, ""));
    }
}
