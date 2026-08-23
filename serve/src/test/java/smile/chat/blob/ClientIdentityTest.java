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
 * Unit tests for {@link ClientIdentity}.
 */
public class ClientIdentityTest {

    @Test
    public void testGivenIpv4MappedIpv6WhenNormalizedThenStripsPrefix() {
        // Given / When / Then
        assertEquals("203.0.113.10", ClientIdentity.normalizeIp("::ffff:203.0.113.10"));
        assertEquals("203.0.113.10", ClientIdentity.normalizeIp("::FFFF:203.0.113.10"));
        assertEquals("203.0.113.10", ClientIdentity.normalizeIp("203.0.113.10"));
        assertEquals("unknown", ClientIdentity.normalizeIp(null));
        assertEquals("unknown", ClientIdentity.normalizeIp("  "));
    }

    @Test
    public void testGivenSameIpWhenHashedThenStableSixteenHexChars() {
        String a = ClientIdentity.hashIp("203.0.113.10");
        String b = ClientIdentity.hashIp("203.0.113.10");
        assertEquals(a, b);
        assertEquals(16, a.length());
        assertTrue(a.matches("[0-9a-f]{16}"));
        assertNotEquals(a, ClientIdentity.hashIp("203.0.113.11"));
    }
}
