/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tool;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.FunctionDefinition;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for assistant text sanitization and tool post-processing.
 */
public class AssistantTextSanitizerTest {
    @Test
    public void testGivenMixedToolTranscriptWhenSanitizedThenOnlyFinalAnswer() {
        String raw = """
                The user wants to see the top 2 rows.
                </think>

                <tool_call>
                <function=Read>
                <parameter=file_path>
                /tmp/README.md
                </parameter>
                </function>
                </tool_call><|im_end|>
                <|im_start|>user
                <tool_response>
                "1\\t# SMILE"
                </tool_response><|im_end|>
                <think>
                I've already read it.
                </think>

                Here are the top 2 lines of README.md:
                """;
        String cleaned = AssistantTextSanitizer.sanitize(raw);
        assertNotNull(cleaned);
        assertFalse(cleaned.contains("<|im_end|>"));
        assertFalse(cleaned.contains("<|im_start|>"));
        assertFalse(cleaned.contains("<tool_call>"));
        assertFalse(cleaned.contains("<tool_response>"));
        assertFalse(cleaned.contains("</think>"));
        assertTrue(cleaned.contains("Here are the top 2 lines"));
    }

    @Test
    public void testGivenToolsFinalAnswerWhenPostProcessedThenStripsThink() {
        ToolDefinition tool = ToolDefinition.function(new FunctionDefinition(
                "Read", "read", Map.of("type", "object")));
        ChatOptions opts = new ChatOptions(new ToolDefinition[]{tool}, ToolChoice.AUTO, true);
        ChatCompletion raw = new ChatCompletion(
                "qwen",
                "reasoning\n</think>\n\nFinal answer.",
                new int[]{1}, new int[]{2}, FinishReason.stop, null);
        ChatCompletion out = ToolCallPostProcessor.apply(raw, opts);
        assertEquals("Final answer.", out.content());
        assertFalse(out.hasToolCalls());
    }

    @Test
    public void testGivenToolCallsWhenPostProcessedThenContentHasNoXml() {
        ToolDefinition tool = ToolDefinition.function(new FunctionDefinition(
                "Read", "read", Map.of("type", "object")));
        ChatOptions opts = new ChatOptions(new ToolDefinition[]{tool}, ToolChoice.AUTO, true);
        String raw = """
                </think>
                <tool_call>
                <function=Read>
                <parameter=file_path>
                /tmp/x
                </parameter>
                </function>
                </tool_call>
                """;
        ChatCompletion completion = new ChatCompletion(
                "qwen", raw, new int[]{1}, new int[]{2}, FinishReason.stop, null);
        ChatCompletion out = ToolCallPostProcessor.apply(completion, opts);
        assertEquals(FinishReason.tool_calls, out.reason());
        assertTrue(out.hasToolCalls());
        assertEquals("Read", out.toolCalls().getFirst().function().name());
        assertTrue(out.content() == null || !out.content().contains("tool_call"));
    }
}
