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
package smile.studio.agent;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.UIManager;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import smile.plot.swing.Palette;
import smile.studio.text.Markdown;
import smile.studio.text.Monospaced;
import smile.studio.text.OutputArea;

/**
 * Output area specialized for agent turn transcripts, distinguishing LLM Markdown
 * output from tool-calling status updates and subagent/system status notices so
 * each output kind can be styled and previewed appropriately.
 *
 * @author Haifeng Li
 */
public class AgentOutputArea extends OutputArea {
    /** Prefix for successful tool call status lines emitted by {@code StreamResponseHandler}. */
    static final String TOOL_SUCCESS_PREFIX = "  ✅ ";
    /** Prefix for failed tool call status lines emitted by {@code StreamResponseHandler}. */
    static final String TOOL_FAILURE_PREFIX = "  ❌ ";
    /** Header prefix for {@code TodoWrite} task checklists. */
    static final String TODO_HEADER_PREFIX = "📝 Current Tasks:";

    /**
     * The kind of agent output displayed by this widget.
     */
    public enum Kind {
        /** Model-generated Markdown response text (eligible for rendered HTML preview). */
        LLM,
        /** Tool call completion lines and TodoWrite task checklists (kept as raw lines). */
        TOOL,
        /** Subagent and system status notices (e.g. {@code [Searching...]}). */
        STATUS
    }

    /**
     * A contiguous segment of agent output classified by {@link Kind}.
     *
     * @param kind the segment kind.
     * @param text the segment text.
     */
    public record Segment(Kind kind, String text) {
        public Segment {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
        }
    }

    private final Kind kind;

    /**
     * Creates an {@code AgentOutputArea} for {@link Kind#LLM} output.
     */
    public AgentOutputArea() {
        this(Kind.LLM);
    }

    /**
     * Creates an {@code AgentOutputArea} for the given output {@link Kind}.
     *
     * @param kind the output kind.
     */
    public AgentOutputArea(Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
        setFont(Monospaced.getFont());
        setEditable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        applyKindStyle();
    }

    /**
     * Returns the output kind of this area.
     *
     * @return the {@link Kind}.
     */
    public Kind kind() {
        return kind;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    private void applyKindStyle() {
        switch (kind) {
            case LLM -> setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
            case TOOL -> setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            case STATUS -> {
                setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
                Color muted = UIManager.getColor("Label.disabledForeground");
                setForeground(muted != null ? muted : Palette.DARK_GRAY);
            }
        }
    }

    /**
     * Creates a rendered {@link Markdown} preview component for this area's text,
     * invoking {@code onDoubleClick} when the user double-clicks the rendered view.
     *
     * @param onDoubleClick callback invoked on double-click, or {@code null}.
     * @return the rendered {@link Markdown} component.
     */
    public Markdown createPreview(Runnable onDoubleClick) {
        Markdown md = new Markdown(getText());
        if (onDoubleClick != null) {
            md.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        onDoubleClick.run();
                    }
                }
            });
        }
        return md;
    }

    /**
     * Classifies a single complete line (without trailing newline) outside a fenced
     * code block into {@link Kind#LLM}, {@link Kind#TOOL}, or {@link Kind#STATUS}.
     *
     * @param line the line to inspect.
     * @param inTodoBlock whether the preceding line was inside a {@code TodoWrite} checklist.
     * @return the classified {@link Kind}.
     */
    public static Kind classifyLine(String line, boolean inTodoBlock) {
        if (line == null || line.isEmpty()) {
            return Kind.LLM;
        }
        if (line.startsWith(TOOL_SUCCESS_PREFIX)
                || line.startsWith(TOOL_FAILURE_PREFIX)
                || line.startsWith(TODO_HEADER_PREFIX)) {
            return Kind.TOOL;
        }
        if (inTodoBlock && isTodoItemLine(line)) {
            return Kind.TOOL;
        }
        if (isTodoItemLine(line)) {
            return Kind.TOOL;
        }
        if (isBracketedStatusLine(line)) {
            return Kind.STATUS;
        }
        return Kind.LLM;
    }

    /**
     * Returns true if {@code line} is a {@code TodoWrite} item line starting with one of
     * the task status emojis ({@code ⏳}, {@code 🚧}, {@code ✅}, {@code ❓}) followed by a space.
     *
     * @param line the line to check.
     * @return true if it is a todo item line.
     */
    public static boolean isTodoItemLine(String line) {
        return line.startsWith("⏳ ")
                || line.startsWith("🚧 ")
                || line.startsWith("✅ ")
                || line.startsWith("❓ ");
    }

    /**
     * Returns true if {@code line} is a bracketed status notice (e.g. {@code [Reading file...]}
     * or {@code [Context compacted. Continuing the task.]}) rather than a Markdown link
     * or reference definition.
     *
     * @param line the line to check.
     * @return true if it is a bracketed status line.
     */
    public static boolean isBracketedStatusLine(String line) {
        String trimmed = line.strip();
        if (trimmed.length() < 3 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            return false;
        }
        // Exclude Markdown inline links [text](url), reference links [text][ref], and footnotes [^1]
        if (trimmed.charAt(1) == '^') {
            return false;
        }
        int closeBracket = trimmed.indexOf(']');
        return closeBracket == trimmed.length() - 1;
    }

    /**
     * Splits a multi-line transcript string into contiguous {@link Segment}s of
     * {@link Kind#LLM}, {@link Kind#TOOL}, and {@link Kind#STATUS}, respecting
     * Markdown fenced code blocks ({@code ```} or {@code ~~~}) so lines inside
     * code blocks always remain part of the enclosing {@link Kind#LLM} segment.
     *
     * @param raw the raw transcript text.
     * @return the ordered list of segments.
     */
    public static List<Segment> splitSegments(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Segment> segments = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        Kind currentKind = null;
        boolean inFence = false;
        String fenceMarker = null;
        boolean inTodoBlock = false;

        int len = raw.length();
        int i = 0;
        while (i < len) {
            int nl = raw.indexOf('\n', i);
            String fullLine = (nl >= 0) ? raw.substring(i, nl + 1) : raw.substring(i);
            String content = fullLine;
            if (content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
            }
            if (content.endsWith("\r")) {
                content = content.substring(0, content.length() - 1);
            }

            // Track fenced code blocks (up to 3 leading spaces allowed in CommonMark)
            String strippedLeading = content.stripLeading();
            int leadingSpaces = content.length() - strippedLeading.length();
            if (leadingSpaces <= 3 && (strippedLeading.startsWith("```") || strippedLeading.startsWith("~~~"))) {
                char fc = strippedLeading.charAt(0);
                int count = 0;
                while (count < strippedLeading.length() && strippedLeading.charAt(count) == fc) {
                    count++;
                }
                String marker = strippedLeading.substring(0, count);
                if (!inFence) {
                    inFence = true;
                    fenceMarker = marker;
                } else if (strippedLeading.startsWith(fenceMarker)
                        && strippedLeading.substring(fenceMarker.length()).isBlank()) {
                    inFence = false;
                    fenceMarker = null;
                }
            }

            Kind lineKind;
            if (inFence) {
                lineKind = Kind.LLM;
                inTodoBlock = false;
            } else if (content.isBlank()) {
                // Blank lines stay attached to the active segment (or default to LLM)
                inTodoBlock = false;
                lineKind = (currentKind != null) ? currentKind : Kind.LLM;
            } else {
                lineKind = classifyLine(content, inTodoBlock);
                if (content.startsWith(TODO_HEADER_PREFIX) || (inTodoBlock && isTodoItemLine(content))) {
                    inTodoBlock = true;
                } else {
                    inTodoBlock = false;
                }
            }

            if (currentKind == null) {
                currentKind = lineKind;
            } else if (lineKind != currentKind) {
                flushSegment(segments, currentKind, currentText);
                currentKind = lineKind;
            }
            currentText.append(fullLine);

            i = (nl >= 0) ? nl + 1 : len;
        }
        if (currentKind != null && !currentText.isEmpty()) {
            flushSegment(segments, currentKind, currentText);
        }
        return segments;
    }

    private static void flushSegment(List<Segment> segments, Kind kind, StringBuilder sb) {
        String text = sb.toString();
        sb.setLength(0);
        if (text.isBlank()) {
            // Merge whitespace-only segment into previous if possible, or drop leading blank
            if (!segments.isEmpty()) {
                Segment prev = segments.removeLast();
                segments.add(new Segment(prev.kind(), prev.text() + text));
            }
            return;
        }
        // Trim leading/trailing blank lines on TOOL and STATUS segments so they stay compact
        if (kind == Kind.TOOL || kind == Kind.STATUS) {
            text = trimSurroundingBlankLines(text);
        }
        if (!text.isBlank()) {
            segments.add(new Segment(kind, text));
        }
    }

    static String trimSurroundingBlankLines(String text) {
        int start = 0;
        int len = text.length();
        while (start < len && (text.charAt(start) == '\n' || text.charAt(start) == '\r')) {
            start++;
        }
        int end = len;
        while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
            end--;
        }
        return text.substring(start, end);
    }
}
