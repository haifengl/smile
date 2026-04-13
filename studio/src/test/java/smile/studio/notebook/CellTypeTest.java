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

