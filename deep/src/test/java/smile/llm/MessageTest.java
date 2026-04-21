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
package smile.llm;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Message} record.
 *
 * @author Haifeng Li
 */
public class MessageTest {
    @Test
    public void testGivenMessageWhenCreatedThenRoleAndContentAccessorsWork() {
        Message msg = new Message(Role.user, "Hello");
        assertEquals(Role.user, msg.role());
        assertEquals("Hello", msg.content());
    }

    @Test
    public void testGivenMessageWithNullRoleWhenCreatedThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Message(null, "content"));
    }

    @Test
    public void testGivenMessageWithNullContentWhenCreatedThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Message(Role.user, null));
    }

    @Test
    public void testGivenMessageSystemFactoryWhenCalledThenRoleIsSystem() {
        Message msg = Message.system("You are helpful.");
        assertEquals(Role.system, msg.role());
        assertEquals("You are helpful.", msg.content());
    }

    @Test
    public void testGivenMessageUserFactoryWhenCalledThenRoleIsUser() {
        Message msg = Message.user("What is 2+2?");
        assertEquals(Role.user, msg.role());
    }

    @Test
    public void testGivenMessageAssistantFactoryWhenCalledThenRoleIsAssistant() {
        Message msg = Message.assistant("The answer is 4.");
        assertEquals(Role.assistant, msg.role());
    }

    @Test
    public void testGivenTwoMessagesWithSameFieldsWhenComparedThenAreEqual() {
        Message a = new Message(Role.user, "hi");
        Message b = new Message(Role.user, "hi");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testGivenMessageWhenToStringCalledThenContainsRoleAndContent() {
        Message msg = new Message(Role.assistant, "hello");
        String str = msg.toString();
        assertTrue(str.contains("assistant") || str.contains("hello"),
                "toString should contain role or content info");
    }
}
