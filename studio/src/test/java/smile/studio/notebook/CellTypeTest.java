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
package smile.studio.notebook;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CellType}.
 *
 * @author Haifeng Li
 */
public class CellTypeTest {

    @Test
    public void testEnumHasThreeValues() {
        System.out.println("CellType: has exactly 3 values");
        assertEquals(3, CellType.values().length);
    }

    @Test
    public void testCodeValue() {
        System.out.println("CellType: Code value string");
        assertEquals("code", CellType.Code.value());
    }

    @Test
    public void testMarkdownValue() {
        System.out.println("CellType: Markdown value string");
        assertEquals("markdown", CellType.Markdown.value());
    }

    @Test
    public void testRawValue() {
        System.out.println("CellType: Raw value string");
        assertEquals("raw", CellType.Raw.value());
    }

    @Test
    public void testToStringMatchesValue() {
        System.out.println("CellType: toString() matches value()");
        for (CellType type : CellType.values()) {
            assertEquals(type.value(), type.toString(),
                    "toString() must match value() for " + type.name());
        }
    }

    @Test
    public void testValueOf() {
        System.out.println("CellType: valueOf by enum name");
        assertSame(CellType.Code,     CellType.valueOf("Code"));
        assertSame(CellType.Markdown, CellType.valueOf("Markdown"));
        assertSame(CellType.Raw,      CellType.valueOf("Raw"));
    }

    @Test
    public void testOrdinals() {
        System.out.println("CellType: ordinals are stable");
        assertEquals(0, CellType.Code.ordinal());
        assertEquals(1, CellType.Markdown.ordinal());
        assertEquals(2, CellType.Raw.ordinal());
    }

    @Test
    public void testSwitchCoverage() {
        System.out.println("CellType: switch covers all values without default");
        for (CellType type : CellType.values()) {
            // This switch must compile; if a value is added without updating
            // the switch the test will fail to compile.
            String label = switch (type) {
                case Code     -> "code";
                case Markdown -> "markdown";
                case Raw      -> "raw";
            };
            assertEquals(type.value(), label);
        }
    }
}

