/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.kernel;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PostRunNavigation}.
 *
 * @author Haifeng Li
 */
public class PostRunNavigationTest {

    @Test
    public void testEnumValues() {
        System.out.println("PostRunNavigation: enum values");
        var values = PostRunNavigation.values();
        assertEquals(3, values.length, "Should have exactly 3 navigation modes");
    }

    @Test
    public void testStayOrdinal() {
        System.out.println("PostRunNavigation: STAY is first");
        assertEquals(0, PostRunNavigation.STAY.ordinal());
    }

    @Test
    public void testNextOrNewOrdinal() {
        System.out.println("PostRunNavigation: NEXT_OR_NEW is second");
        assertEquals(1, PostRunNavigation.NEXT_OR_NEW.ordinal());
    }

    @Test
    public void testInsertBelowOrdinal() {
        System.out.println("PostRunNavigation: INSERT_BELOW is third");
        assertEquals(2, PostRunNavigation.INSERT_BELOW.ordinal());
    }

    @Test
    public void testValueOf() {
        System.out.println("PostRunNavigation: valueOf");
        assertSame(PostRunNavigation.STAY,         PostRunNavigation.valueOf("STAY"));
        assertSame(PostRunNavigation.NEXT_OR_NEW,  PostRunNavigation.valueOf("NEXT_OR_NEW"));
        assertSame(PostRunNavigation.INSERT_BELOW, PostRunNavigation.valueOf("INSERT_BELOW"));
    }

    @Test
    public void testSwitchCoverage() {
        System.out.println("PostRunNavigation: switch covers all values");
        // Verify a switch statement compiles and handles every constant.
        for (var nav : PostRunNavigation.values()) {
            String result = switch (nav) {
                case STAY -> "stay";
                case NEXT_OR_NEW -> "next";
                case INSERT_BELOW -> "insert";
            };
            assertNotNull(result);
        }
    }
}

