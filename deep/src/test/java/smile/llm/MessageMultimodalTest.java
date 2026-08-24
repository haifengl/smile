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
 * Unit tests for multimodal {@link Message}.
 *
 * @author Haifeng Li
 */
public class MessageMultimodalTest {

    @Test
    public void testGivenTextOnlyWhenContentThenString() {
        Message msg = Message.user("hello");
        assertEquals("hello", msg.content());
        assertFalse(msg.hasMedia());
        assertEquals(1, msg.parts().size());
        assertInstanceOf(TextPart.class, msg.parts().get(0));
    }

    @Test
    public void testGivenImagePartWhenHasMediaThenTrue() {
        Message msg = Message.user(
                new ImageUrlPart("https://example.com/a.png"),
                new TextPart("what is this?"));
        assertTrue(msg.hasMedia());
        assertEquals("what is this?", msg.content());
    }

    @Test
    public void testGivenNullPartsWhenConstructThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Message(Role.user, (ContentPart[]) null));
    }
}
