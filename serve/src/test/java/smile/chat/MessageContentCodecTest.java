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

import org.junit.jupiter.api.Test;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.VideoUrlPart;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MessageContentCodec}.
 */
public class MessageContentCodecTest {

    @Test
    public void testGivenTextOnlyWhenEncodedThenPlainString() {
        Message msg = new Message(Role.user, "hello");
        assertEquals("hello", MessageContentCodec.toStoredContent(msg));
        assertTrue(MessageContentCodec.mediaContentIds(msg).isEmpty());
    }

    @Test
    public void testGivenMediaPartsWhenEncodedThenJsonArrayWithUrls() {
        Message msg = new Message(Role.user,
                new TextPart("look"),
                new ImageUrlPart("/api/v1/media/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                new VideoUrlPart("/api/v1/media/11111111-2222-3333-4444-555555555555", 2.0));
        String json = MessageContentCodec.toStoredContent(msg);
        assertTrue(json.contains("\"type\":\"image_url\""));
        assertTrue(json.contains("\"type\":\"video_url\""));
        assertTrue(json.contains("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        assertEquals(2, MessageContentCodec.mediaContentIds(msg).size());
    }
}
