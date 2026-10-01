/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
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

