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
package smile.data.measure;

import java.text.NumberFormat;
import java.util.List;
import smile.data.type.DataTypes;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for smile.data.measure package.
 *
 * @author Haifeng Li
 */
public class MeasureTest {

    enum Season { SPRING, SUMMER, FALL, WINTER }

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    // -----------------------------------------------------------------------
    // NominalScale tests
    // -----------------------------------------------------------------------

    @Test
    public void testNominalFromVarargs() {
        System.out.println("NominalScale varargs constructor");
        NominalScale scale = new NominalScale("Male", "Female");
        assertEquals(2, scale.size());
        assertEquals("Male",   scale.level(0));
        assertEquals("Female", scale.level(1));
        assertEquals(0, scale.valueOf("Male").intValue());
        assertEquals(1, scale.valueOf("Female").intValue());
    }

    @Test
    public void testNominalFromList() {
        System.out.println("NominalScale list constructor");
        NominalScale scale = new NominalScale(List.of("A", "B", "C"));
        assertEquals(3, scale.size());
        assertEquals("A", scale.level(0));
        assertEquals("B", scale.level(1));
        assertEquals("C", scale.level(2));
    }

    @Test
    public void testNominalFromEnum() {
        System.out.println("NominalScale enum constructor");
        NominalScale scale = new NominalScale(Season.class);
        assertEquals(4, scale.size());
        assertEquals("SPRING", scale.level(0));
        assertEquals("WINTER", scale.level(3));
        assertEquals(1, scale.valueOf("SUMMER").intValue());
    }

    @Test
    public void testNominalFactor() {
        System.out.println("NominalScale factor");
        NominalScale scale = new NominalScale("Red", "Green", "Blue");
        assertEquals(0, scale.factor(0));
        assertEquals(1, scale.factor(1));
        assertEquals(2, scale.factor(2));
    }

    @Test
    public void testNominalNonStandardFactor() {
        System.out.println("NominalScale non-standard factor lookup");
        // values = {10, 20, 30} → factor indices = {0, 1, 2}
        NominalScale scale = new NominalScale(new int[]{10, 20, 30}, new String[]{"X", "Y", "Z"});
        assertEquals(0, scale.factor(10));
        assertEquals(1, scale.factor(20));
        assertEquals(2, scale.factor(30));
        assertThrows(IllegalArgumentException.class, () -> scale.factor(5));
    }

    @Test
    public void testNominalToString() {
        System.out.println("NominalScale toString");
        NominalScale scale = new NominalScale("A", "B");
        assertTrue(scale.toString().startsWith("nominal"));
        assertEquals("A", scale.toString(0));
        assertEquals("B", scale.toString(1));
    }

    @Test
    public void testNominalEquals() {
        System.out.println("NominalScale equals");
        NominalScale a = new NominalScale("X", "Y");
        NominalScale b = new NominalScale("X", "Y");
        NominalScale c = new NominalScale("X", "Y", "Z");
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    public void testNominalDataType() {
        System.out.println("NominalScale data type");
        // ≤128 levels → byte
        NominalScale small = new NominalScale("A", "B");
        assertEquals(DataTypes.ByteType, small.type());
    }

    // -----------------------------------------------------------------------
    // OrdinalScale tests
    // -----------------------------------------------------------------------

    @Test
    public void testOrdinalFromVarargs() {
        System.out.println("OrdinalScale varargs constructor");
        OrdinalScale scale = new OrdinalScale("low", "medium", "high");
        assertEquals(3, scale.size());
        assertEquals("low",    scale.level(0));
        assertEquals("medium", scale.level(1));
        assertEquals("high",   scale.level(2));
        assertEquals(0, scale.valueOf("low").intValue());
        assertEquals(2, scale.valueOf("high").intValue());
    }

    @Test
    public void testOrdinalFromEnum() {
        System.out.println("OrdinalScale enum constructor");
        OrdinalScale scale = new OrdinalScale(Season.class);
        assertEquals(4, scale.size());
        assertEquals("SPRING", scale.level(0));
        assertEquals("SUMMER", scale.level(1));
    }

    @Test
    public void testOrdinalToString() {
        System.out.println("OrdinalScale toString");
        OrdinalScale scale = new OrdinalScale("low", "high");
        assertTrue(scale.toString().startsWith("ordinal"));
    }

    @Test
    public void testOrdinalEquals() {
        System.out.println("OrdinalScale equals");
        OrdinalScale a = new OrdinalScale("a", "b", "c");
        OrdinalScale b = new OrdinalScale("a", "b", "c");
        OrdinalScale c = new OrdinalScale("a", "b");
        assertEquals(a, b);
        assertNotEquals(a, c);
        // NominalScale vs OrdinalScale with same levels should not be equal
        NominalScale n = new NominalScale("a", "b", "c");
        assertNotEquals(a, n);
    }

    // -----------------------------------------------------------------------
    // NumericalMeasure (IntervalScale / RatioScale) tests
    // -----------------------------------------------------------------------

    @Test
    public void testIntervalScale() {
        System.out.println("IntervalScale");
        IntervalScale scale = new IntervalScale(NumberFormat.getInstance());
        assertEquals("interval", scale.toString());
        Number v = scale.valueOf("42");
        assertNotNull(v);
    }

    @Test
    public void testRatioScale() {
        System.out.println("RatioScale");
        RatioScale scale = new RatioScale(NumberFormat.getInstance());
        assertEquals("ratio", scale.toString());
        Number v = scale.valueOf("3.14");
        assertNotNull(v);
    }

    // -----------------------------------------------------------------------
    // CategoricalMeasure shared behaviour
    // -----------------------------------------------------------------------

    @Test
    public void testCategoricalValues() {
        System.out.println("CategoricalMeasure values/levels");
        NominalScale scale = new NominalScale("p", "q", "r");
        assertArrayEquals(new int[]{0, 1, 2}, scale.values());
        assertArrayEquals(new String[]{"p", "q", "r"}, scale.levels());
    }

    @Test
    public void testCategoricalValueOfNull() {
        System.out.println("CategoricalMeasure valueOf unknown returns null");
        NominalScale scale = new NominalScale("A", "B");
        assertNull(scale.valueOf("UNKNOWN"));
    }

    @Test
    public void testCategoricalLargeNonFactor() {
        System.out.println("CategoricalMeasure non-standard values sparse");
        // values widely spread – should fall back to linear scan
        int[] vals   = { 1000, 2000, 3000 };
        String[] lvl = { "X",  "Y",  "Z"  };
        NominalScale scale = new NominalScale(vals, lvl);
        assertEquals(0, scale.factor(1000));
        assertEquals(1, scale.factor(2000));
        assertEquals(2, scale.factor(3000));
        assertThrows(IllegalArgumentException.class, () -> scale.factor(999));
    }
}

