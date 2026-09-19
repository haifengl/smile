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

import org.junit.jupiter.api.*;
import smile.llm.Message;
import smile.llm.Role;

/**
 * Unit tests that do not require the onnxruntime-genai native library.
 *
 * @author Haifeng Li
 */
public class GenAIExceptionTest {

    @Test
    public void messageAndCause() {
        GenAIException ex = new GenAIException("boom", new IllegalStateException("root"));
        assertEquals("boom", ex.getMessage());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    public void chatTemplateJsonEscapesAndRoles() {
        String json = ChatTemplateJson.messages(
                new Message(Role.system, "Be \"helpful\""),
                new Message(Role.user, "Hi\nthere"));
        assertTrue(json.contains("\"role\":\"system\""));
        assertTrue(json.contains("Be \\\"helpful\\\""));
        assertTrue(json.contains("Hi\\nthere"));
        assertFalse(ChatTemplateJson.hasMedia(
                new Message(Role.user, "text only")));
    }
}
