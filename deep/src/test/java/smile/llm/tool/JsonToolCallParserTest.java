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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.FunctionDefinition;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;

/**
 * JSON / OGA-style tool-call parse fixtures.
 *
 * @author Haifeng Li
 */
public class JsonToolCallParserTest {

    @Test
    public void parsesToolCallXmlBlock() {
        String raw = """
                Sure.
                <tool_call>
                {"name":"get_weather","arguments":{"city":"SF"}}
                </tool_call>
                """;
        ParseResult r = new JsonToolCallParser().parse(raw);
        assertTrue(r.hasToolCalls());
        assertEquals(FinishReason.tool_calls, r.reason());
        assertEquals("get_weather", r.toolCalls().getFirst().function().name());
        assertTrue(r.content().startsWith("Sure"));
    }

    @Test
    public void parsesBareJsonObject() {
        ParseResult r = new JsonToolCallParser().parse(
                "{\"name\":\"lookup\",\"arguments\":{\"q\":\"x\"}}");
        assertTrue(r.hasToolCalls());
        assertEquals("lookup", r.toolCalls().getFirst().function().name());
    }

    @Test
    public void postProcessorSelectsJsonForOgaStyle() {
        ChatCompletion raw = new ChatCompletion(
                "m",
                "<tool_call>\n{\"name\":\"f\",\"arguments\":{}}\n</tool_call>",
                new int[]{1},
                new int[]{2},
                FinishReason.stop,
                null);
        ChatOptions opts = new ChatOptions(
                new ToolDefinition[]{
                        ToolDefinition.function(new FunctionDefinition("f", "d", Map.of()))
                },
                ToolChoice.AUTO,
                true);
        ChatCompletion out = ToolCallPostProcessor.apply(raw, opts);
        assertEquals(FinishReason.tool_calls, out.reason());
        assertNotNull(out.toolCalls());
        assertEquals(1, out.toolCalls().size());
        assertEquals("f", out.toolCalls().getFirst().function().name());
    }
}
