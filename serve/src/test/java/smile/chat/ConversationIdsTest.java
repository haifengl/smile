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

import java.util.Map;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConversationIds}.
 *
 * @author Haifeng Li
 */
public class ConversationIdsTest {

    @Test
    public void testGivenNumericIdWhenFormattedThenUsesConvPrefix() {
        assertEquals("conv_42", ConversationIds.toExternalId(42));
    }

    @Test
    public void testGivenPrefixedOrBareIdWhenParsedThenReturnsNumericId() {
        assertEquals(42L, ConversationIds.parseRequired("conv_42"));
        assertEquals(42L, ConversationIds.parseRequired("42"));
        assertNull(ConversationIds.parseOptional(null));
        assertNull(ConversationIds.parseOptional(""));
    }

    @Test
    public void testGivenMalformedIdWhenParsedThenBadRequest() {
        assertThrows(BadRequestException.class, () -> ConversationIds.parseRequired("conv_abc"));
        assertThrows(BadRequestException.class, () -> ConversationIds.parseOptional("nope"));
    }

    @Test
    public void testGivenOversizedMetadataWhenValidatedThenBadRequest() {
        Map<String, String> tooMany = new java.util.HashMap<>();
        for (int i = 0; i < 17; i++) {
            tooMany.put("k" + i, "v");
        }
        assertThrows(BadRequestException.class, () -> ConversationIds.validateMetadata(tooMany));
        assertThrows(BadRequestException.class,
                () -> ConversationIds.validateMetadata(Map.of("k".repeat(65), "v")));
        assertThrows(BadRequestException.class,
                () -> ConversationIds.validateMetadata(Map.of("k", "v".repeat(513))));
    }
}
