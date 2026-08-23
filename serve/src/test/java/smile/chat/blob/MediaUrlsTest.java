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
package smile.chat.blob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MediaUrls}.
 */
public class MediaUrlsTest {

    @Test
    public void testGivenRelativeUrlWhenParsedThenReturnsContentId() {
        String id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        assertEquals(id, MediaUrls.parseContentId("/api/v1/media/" + id).orElseThrow());
        assertEquals(id, MediaUrls.parseContentId("http://localhost:8888/api/v1/media/" + id).orElseThrow());
        assertEquals(id, MediaUrls.parseContentId("/api/v1/media/" + id + "?download=true").orElseThrow());
    }

    @Test
    public void testGivenExternalOrDataUrlWhenParsedThenEmpty() {
        assertTrue(MediaUrls.parseContentId("https://example.com/img.png").isEmpty());
        assertTrue(MediaUrls.parseContentId("data:image/png;base64,aaa").isEmpty());
        assertTrue(MediaUrls.parseContentId(null).isEmpty());
    }

    @Test
    public void testGivenContentIdWhenToUrlThenUsesApiPrefix() {
        assertEquals("/api/v1/media/abc", MediaUrls.toUrl("abc"));
    }
}
