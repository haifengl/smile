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
package smile.onnx.genai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.llm.ChatOptions;
import smile.llm.FunctionDefinition;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;

/**
 * Chat-template JSON fixtures (tools I/O Phase 1).
 *
 * @author Haifeng Li
 */
public class ChatTemplateJsonTest {

    @Test
    public void serializesToolsForTemplate() {
        ChatOptions opts = new ChatOptions(
                new ToolDefinition[]{
                        ToolDefinition.function(new FunctionDefinition(
                                "get_weather", "Weather", Map.of("type", "object")))
                },
                ToolChoice.AUTO,
                true);
        String tools = ChatTemplateJson.tools(opts);
        assertNotNull(tools);
        assertTrue(tools.contains("get_weather"));
        assertTrue(tools.contains("\"type\":\"function\"") || tools.contains("function"));
    }

    @Test
    public void serializesMessages() {
        String json = ChatTemplateJson.messages(new Message(Role.user, "hi"));
        assertTrue(json.contains("\"role\":\"user\""));
        assertTrue(json.contains("hi"));
    }
}
