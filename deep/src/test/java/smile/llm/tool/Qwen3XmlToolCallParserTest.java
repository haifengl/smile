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

import org.junit.jupiter.api.Test;
import smile.llm.FinishReason;
import smile.llm.ToolCall;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Qwen3XmlToolCallParser}.
 */
public class Qwen3XmlToolCallParserTest {
    private final Qwen3XmlToolCallParser parser = new Qwen3XmlToolCallParser();

    @Test
    public void testGivenPlainTextWhenParsedThenStopWithoutToolCalls() {
        ParseResult result = parser.parse("Hello, world!");
        assertEquals(FinishReason.stop, result.reason());
        assertFalse(result.hasToolCalls());
        assertEquals("Hello, world!", result.content());
    }

    @Test
    public void testGivenSingleToolCallWhenParsedThenExtractsNameAndArgs() {
        String text = """
                <tool_call>
                <function=get_weather>
                <parameter=location>
                San Francisco
                </parameter>
                </function>
                </tool_call>
                """;
        ParseResult result = parser.parse(text);
        assertEquals(FinishReason.tool_calls, result.reason());
        assertEquals(1, result.toolCalls().size());
        ToolCall call = result.toolCalls().getFirst();
        assertEquals("get_weather", call.function().name());
        assertTrue(call.function().arguments().contains("San Francisco"));
        assertTrue(call.id().startsWith("call_"));
        assertNull(result.content());
    }

    @Test
    public void testGivenThinkingPrefixWhenParsedThenStripped() {
        String text = """
                <think>
                I should call the weather tool.
                </think>

                <tool_call>
                <function=get_weather>
                <parameter=location>
                Tokyo
                </parameter>
                </function>
                </tool_call>
                """;
        ParseResult result = parser.parse(text);
        assertEquals(FinishReason.tool_calls, result.reason());
        assertEquals("get_weather", result.toolCalls().getFirst().function().name());
    }

    @Test
    public void testGivenMultipleToolCallsWhenParsedThenAllExtracted() {
        String text = """
                <tool_call>
                <function=a>
                <parameter=x>
                1
                </parameter>
                </function>
                </tool_call>
                <tool_call>
                <function=b>
                <parameter=y>
                2
                </parameter>
                </function>
                </tool_call>
                """;
        ParseResult result = parser.parse(text);
        assertEquals(2, result.toolCalls().size());
        assertEquals("a", result.toolCalls().get(0).function().name());
        assertEquals("b", result.toolCalls().get(1).function().name());
    }

    @Test
    public void testGivenJsonParameterWhenParsedThenDecoded() {
        String text = """
                <tool_call>
                <function=search>
                <parameter=filters>
                {"city":"SF","limit":3}
                </parameter>
                </function>
                </tool_call>
                """;
        ParseResult result = parser.parse(text);
        assertTrue(result.toolCalls().getFirst().function().arguments().contains("\"city\""));
    }
}
