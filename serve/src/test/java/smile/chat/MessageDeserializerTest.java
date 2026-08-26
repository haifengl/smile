/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.llm.AudioUrlPart;
import smile.llm.Message;
import smile.llm.Role;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MessageDeserializer}.
 */
public class MessageDeserializerTest {
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Message.class, new MessageDeserializer());
        mapper = new ObjectMapper();
        mapper.registerModule(module);
    }

    @Test
    public void testGivenAudioUrlPartWhenDeserializedThenParsed() throws Exception {
        Message message = mapper.readValue("""
                {"role":"user","content":[
                  {"type":"audio_url","audio_url":{"url":"/api/v1/media/abc"}}
                ]}
                """, Message.class);
        assertEquals(Role.user, message.role());
        assertEquals(1, message.parts().size());
        assertInstanceOf(AudioUrlPart.class, message.parts().getFirst());
        assertTrue(message.hasAudio());
    }

    @Test
    public void testGivenToolRoleWhenDeserializedThenMappedToTool() throws Exception {
        Message message = mapper.readValue("""
                {"role":"tool","content":"42"}
                """, Message.class);
        assertEquals(Role.tool, message.role());
        assertEquals("42", message.content());
    }

    @Test
    public void testGivenIpythonRoleWhenDeserializedThenMappedToTool() throws Exception {
        Message message = mapper.readValue("""
                {"role":"ipython","content":"42"}
                """, Message.class);
        assertEquals(Role.tool, message.role());
        assertEquals("42", message.content());
    }

    @Test
    public void testGivenAssistantToolCallsWhenDeserializedThenParsed() throws Exception {
        Message message = mapper.readValue("""
                {
                  "role":"assistant",
                  "content":null,
                  "tool_calls":[{
                    "id":"call_1",
                    "type":"function",
                    "function":{"name":"get_weather","arguments":"{\\"location\\":\\"SF\\"}"}
                  }]
                }
                """, Message.class);
        assertEquals(Role.assistant, message.role());
        assertTrue(message.hasToolCalls());
        assertEquals("get_weather", message.toolCalls().getFirst().function().name());
        assertEquals("call_1", message.toolCalls().getFirst().id());
    }

    @Test
    public void testGivenToolMessageWithIdWhenDeserializedThenParsed() throws Exception {
        Message message = mapper.readValue("""
                {"role":"tool","tool_call_id":"call_1","content":"72F"}
                """, Message.class);
        assertEquals(Role.tool, message.role());
        assertEquals("call_1", message.toolCallId());
        assertEquals("72F", message.content());
    }
}
