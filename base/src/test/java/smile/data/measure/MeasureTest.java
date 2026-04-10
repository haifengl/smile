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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import smile.data.type.DataTypes;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the smile.data.measure package.
 *
 * @author Haifeng Li
 */
public class MeasureTest {

    enum Season  { SPRING, SUMMER, FALL, WINTER }
    enum Traffic { RED, YELLOW, GREEN }

    @BeforeAll  public static void setUpClass() {}
    @AfterAll   public static void tearDownClass() {}
    @BeforeEach public void setUp() {}
    @AfterEach  public void tearDown() {}

    // =========================================================================
    // NominalScale – constructors
    // =========================================================================

    @Test
    public void testNominalFromVarargs() {
        System.out.println("NominalScale varargs constructor");
        NominalScale s = new NominalScale("Red", "Green", "Blue");
        assertEquals(3, s.size());
        assertEquals("Red",   s.level(0));
        assertEquals("Green", s.level(1));
        assertEquals("Blue",  s.level(2));
    }

    @Test
    public void testNominalFromList() {
        System.out.println("NominalScale List constructor");
        NominalScale s = new NominalScale(List.of("A", "B", "C"));
        assertEquals(3, s.size());
        assertArrayEquals(new String[]{"A", "B", "C"}, s.levels());
    }

    @Test
    public void testNominalFromIntArray() {
        System.out.println("NominalScale int[] auto-labels via values+levels");
        NominalScale s = new NominalScale(new int[]{10, 20, 30},
                new String[]{"10", "20", "30"});
        assertEquals("10", s.level(10));
        assertEquals("20", s.level(20));
        assertEquals("30", s.level(30));
    }

    @Test
    public void testNominalFromValuesLevels() {
        System.out.println("NominalScale values+levels constructor");
        NominalScale s = new NominalScale(new int[]{5, 10, 15}, new String[]{"Low", "Mid", "High"});
        assertEquals(3, s.size());
        assertEquals("Low",  s.level(5));
        assertEquals("Mid",  s.level(10));
        assertEquals("High", s.level(15));
    }

    @Test
    public void testNominalFromEnum() {
        System.out.println("NominalScale enum constructor");
        NominalScale s = new NominalScale(Season.class);
        assertEquals(4, s.size());
        assertEquals("SPRING", s.level(0));
        assertEquals("SUMMER", s.level(1));
        assertEquals("FALL",   s.level(2));
        assertEquals("WINTER", s.level(3));
    }

    @Test
    public void testNominalSizeMismatchThrows() {
        System.out.println("NominalScale size mismatch throws");
        assertThrows(IllegalArgumentException.class,
            () -> new NominalScale(new int[]{1, 2}, new String[]{"a"}));
    }

    // =========================================================================
    // NominalScale – valueOf / level / factor
    // =========================================================================

    @Test
    public void testNominalValueOf() {
        System.out.println("NominalScale valueOf");
        NominalScale s = new NominalScale("Cat", "Dog", "Bird");
        assertEquals((byte) 0, s.valueOf("Cat"));
        assertEquals((byte) 1, s.valueOf("Dog"));
        assertEquals((byte) 2, s.valueOf("Bird"));
    }

    @Test
    public void testNominalValueOfUnknownReturnsNull() {
        System.out.println("NominalScale valueOf unknown → null");
        NominalScale s = new NominalScale("A", "B");
        assertNull(s.valueOf("UNKNOWN"));
    }

    @Test
    public void testNominalFactor() {
        System.out.println("NominalScale factor (standard)");
        NominalScale s = new NominalScale("X", "Y", "Z");
        assertEquals(0, s.factor(0));
        assertEquals(1, s.factor(1));
        assertEquals(2, s.factor(2));
    }

    @Test
    public void testNominalFactorNonStandard() {
        System.out.println("NominalScale factor (non-standard values)");
        NominalScale s = new NominalScale(new int[]{10, 20, 30}, new String[]{"Low", "Mid", "High"});
        assertEquals(0, s.factor(10));
        assertEquals(1, s.factor(20));
        assertEquals(2, s.factor(30));
    }

    @Test
    public void testNominalFactorInvalidThrows() {
        System.out.println("NominalScale factor invalid throws");
        NominalScale s = new NominalScale(new int[]{10, 20}, new String[]{"A", "B"});
        assertThrows(IllegalArgumentException.class, () -> s.factor(99));
    }

    @Test
    public void testNominalLevelUnknownReturnsNull() {
        System.out.println("NominalScale level unknown → null");
        NominalScale s = new NominalScale("A", "B");
        assertNull(s.level(99));
    }

    // =========================================================================
    // NominalScale – contains / indexOf
    // =========================================================================

    @Test
    public void testNominalContainsInt() {
        System.out.println("NominalScale contains(int)");
        NominalScale s = new NominalScale("P", "Q");
        assertTrue(s.contains(0));
        assertTrue(s.contains(1));
        assertFalse(s.contains(2));
    }

    @Test
    public void testNominalContainsString() {
        System.out.println("NominalScale contains(String)");
        NominalScale s = new NominalScale("P", "Q");
        assertTrue(s.contains("P"));
        assertFalse(s.contains("R"));
    }

    @Test
    public void testNominalIndexOf() {
        System.out.println("NominalScale indexOf");
        NominalScale s = new NominalScale("Alpha", "Beta", "Gamma");
        assertEquals(0,  s.indexOf("Alpha"));
        assertEquals(1,  s.indexOf("Beta"));
        assertEquals(2,  s.indexOf("Gamma"));
        assertEquals(-1, s.indexOf("Delta"));
    }

    // =========================================================================
    // NominalScale – toString
    // =========================================================================

    @Test
    public void testNominalToStringObject() {
        System.out.println("NominalScale toString(Object)");
        NominalScale s = new NominalScale("Male", "Female");
        assertEquals("Male",   s.toString(0));
        assertEquals("Female", s.toString(1));
    }

    @Test
    public void testNominalToStringNull() {
        System.out.println("NominalScale toString(null) null-safety");
        NominalScale s = new NominalScale("A", "B");
        assertEquals("null", s.toString(null));
    }

    @Test
    public void testNominalToStringRepresentation() {
        System.out.println("NominalScale toString()");
        NominalScale s = new NominalScale("a", "b");
        assertTrue(s.toString().startsWith("nominal"));
        assertTrue(s.toString().contains("a"));
        assertTrue(s.toString().contains("b"));
    }

    // =========================================================================
    // NominalScale – type()
    // =========================================================================

    @Test
    public void testNominalTypeByte() {
        System.out.println("NominalScale type byte");
        NominalScale s = new NominalScale("A", "B");
        assertEquals(DataTypes.ByteType, s.type());
    }

    @Test
    public void testNominalTypeShort() {
        System.out.println("NominalScale type short (129 levels)");
        String[] levels = new String[129];
        for (int i = 0; i < levels.length; i++) levels[i] = "L" + i;
        NominalScale s = new NominalScale(levels);
        assertEquals(DataTypes.ShortType, s.type());
    }

    @Test
    public void testNominalTypeInt() {
        System.out.println("NominalScale type int (32769 levels)");
        String[] levels = new String[32769];
        for (int i = 0; i < levels.length; i++) levels[i] = "L" + i;
        NominalScale s = new NominalScale(levels);
        assertEquals(DataTypes.IntType, s.type());
    }

    // =========================================================================
    // NominalScale – equals / hashCode
    // =========================================================================

    @Test
    public void testNominalEqualsSymmetric() {
        System.out.println("NominalScale equals symmetric");
        NominalScale a = new NominalScale("X", "Y");
        NominalScale b = new NominalScale("X", "Y");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void testNominalEqualsDifferentLevels() {
        System.out.println("NominalScale equals different levels");
        NominalScale a = new NominalScale("X", "Y");
        NominalScale b = new NominalScale("X", "Y", "Z");
        assertNotEquals(a, b);
    }

    @Test
    public void testNominalEqualsDifferentValues() {
        System.out.println("NominalScale equals same labels but different values (bug fix)");
        NominalScale standard = new NominalScale("A", "B");
        NominalScale nonStd   = new NominalScale(new int[]{5, 6}, new String[]{"A", "B"});
        assertNotEquals(standard, nonStd,
            "Scales with different underlying values should not be equal");
    }

    @Test
    public void testNominalNotEqualsOrdinal() {
        System.out.println("NominalScale not equals OrdinalScale with same levels");
        NominalScale nominal = new NominalScale("low", "high");
        OrdinalScale ordinal = new OrdinalScale("low", "high");
        assertNotEquals(nominal, ordinal);
    }

    @Test
    public void testNominalHashCodeContract() {
        System.out.println("NominalScale hashCode contract");
        NominalScale a = new NominalScale("P", "Q", "R");
        NominalScale b = new NominalScale("P", "Q", "R");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testNominalInHashSet() {
        System.out.println("NominalScale in HashSet");
        Set<NominalScale> set = new HashSet<>();
        set.add(new NominalScale("A", "B"));
        set.add(new NominalScale("A", "B")); // duplicate
        assertEquals(1, set.size());
    }

    // =========================================================================
    // OrdinalScale – constructors and ordering
    // =========================================================================

    @Test
    public void testOrdinalFromVarargs() {
        System.out.println("OrdinalScale varargs");
        OrdinalScale s = new OrdinalScale("low", "medium", "high");
        assertEquals(3, s.size());
        assertEquals("low",    s.level(0));
        assertEquals("medium", s.level(1));
        assertEquals("high",   s.level(2));
    }

    @Test
    public void testOrdinalFromList() {
        System.out.println("OrdinalScale List constructor");
        OrdinalScale s = new OrdinalScale(List.of("a", "b", "c"));
        assertArrayEquals(new String[]{"a", "b", "c"}, s.levels());
    }

    @Test
    public void testOrdinalFromEnum() {
        System.out.println("OrdinalScale enum constructor");
        OrdinalScale s = new OrdinalScale(Traffic.class);
        assertEquals(3, s.size());
        assertEquals("RED",    s.level(0));
        assertEquals("YELLOW", s.level(1));
        assertEquals("GREEN",  s.level(2));
    }

    @Test
    public void testOrdinalFromValuesLevelsSortedConsistently() {
        System.out.println("OrdinalScale values+levels sorted together (bug fix)");
        // Pass values out of order: {30,10,20} with labels {"High","Low","Mid"}
        // After sorting by value: {10→"Low", 20→"Mid", 30→"High"}
        OrdinalScale s = new OrdinalScale(
            new int[]   {30,    10,    20},
            new String[]{"High","Low", "Mid"});

        assertEquals("Low",  s.level(10));
        assertEquals("Mid",  s.level(20));
        assertEquals("High", s.level(30));

        assertArrayEquals(new int[]   {10,    20,    30},    s.values());
        assertArrayEquals(new String[]{"Low", "Mid", "High"}, s.levels());
    }

    @Test
    public void testOrdinalValuesAndLevelsExposed() {
        System.out.println("OrdinalScale values and levels accessors");
        OrdinalScale s = new OrdinalScale("first", "second", "third");
        assertArrayEquals(new int[]{0, 1, 2}, s.values());
        assertArrayEquals(new String[]{"first", "second", "third"}, s.levels());
    }

    // =========================================================================
    // OrdinalScale – factor / valueOf / contains / indexOf
    // =========================================================================

    @Test
    public void testOrdinalFactor() {
        System.out.println("OrdinalScale factor");
        OrdinalScale s = new OrdinalScale("none", "low", "med", "high");
        assertEquals(0, s.factor(0));
        assertEquals(3, s.factor(3));
    }

    @Test
    public void testOrdinalValueOf() {
        System.out.println("OrdinalScale valueOf");
        OrdinalScale s = new OrdinalScale("alpha", "beta", "gamma");
        assertEquals((byte) 0, s.valueOf("alpha"));
        assertEquals((byte) 2, s.valueOf("gamma"));
        assertNull(s.valueOf("delta"));
    }

    @Test
    public void testOrdinalContains() {
        System.out.println("OrdinalScale contains");
        OrdinalScale s = new OrdinalScale("A", "B");
        assertTrue(s.contains("A"));
        assertFalse(s.contains("C"));
        assertTrue(s.contains(0));
        assertFalse(s.contains(9));
    }

    @Test
    public void testOrdinalIndexOf() {
        System.out.println("OrdinalScale indexOf");
        OrdinalScale s = new OrdinalScale("X", "Y", "Z");
        assertEquals(0,  s.indexOf("X"));
        assertEquals(2,  s.indexOf("Z"));
        assertEquals(-1, s.indexOf("W"));
    }

    // =========================================================================
    // OrdinalScale – toString
    // =========================================================================

    @Test
    public void testOrdinalToStringObject() {
        System.out.println("OrdinalScale toString(Object)");
        OrdinalScale s = new OrdinalScale("never", "rarely", "often", "always");
        assertEquals("never",  s.toString(0));
        assertEquals("always", s.toString(3));
    }

    @Test
    public void testOrdinalToStringNull() {
        System.out.println("OrdinalScale toString(null) null-safety");
        OrdinalScale s = new OrdinalScale("a", "b");
        assertEquals("null", s.toString(null));
    }

    @Test
    public void testOrdinalToStringRepresentation() {
        System.out.println("OrdinalScale toString()");
        OrdinalScale s = new OrdinalScale("bad", "ok", "good");
        assertTrue(s.toString().startsWith("ordinal"));
    }

    // =========================================================================
    // OrdinalScale – equals / hashCode
    // =========================================================================

    @Test
    public void testOrdinalEqualsSymmetric() {
        System.out.println("OrdinalScale equals symmetric");
        OrdinalScale a = new OrdinalScale("a", "b", "c");
        OrdinalScale b = new OrdinalScale("a", "b", "c");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void testOrdinalEqualsDifferentValues() {
        System.out.println("OrdinalScale equals different underlying values");
        OrdinalScale standard = new OrdinalScale("a", "b");
        OrdinalScale nonStd   = new OrdinalScale(new int[]{5, 6}, new String[]{"a", "b"});
        assertNotEquals(standard, nonStd);
    }

    @Test
    public void testOrdinalNotEqualsNominal() {
        System.out.println("OrdinalScale not equals NominalScale");
        OrdinalScale ordinal = new OrdinalScale("x", "y");
        NominalScale nominal = new NominalScale("x", "y");
        assertNotEquals(ordinal, nominal);
    }

    @Test
    public void testOrdinalHashCodeContract() {
        System.out.println("OrdinalScale hashCode contract");
        OrdinalScale a = new OrdinalScale("p", "q");
        OrdinalScale b = new OrdinalScale("p", "q");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testOrdinalInHashSet() {
        System.out.println("OrdinalScale in HashSet");
        Set<OrdinalScale> set = new HashSet<>();
        set.add(new OrdinalScale("a", "b"));
        set.add(new OrdinalScale("a", "b")); // duplicate
        assertEquals(1, set.size());
    }

    // =========================================================================
    // CategoricalMeasure shared behaviour
    // =========================================================================

    @Test
    public void testCategoricalValuesAndLevelsAccessors() {
        System.out.println("CategoricalMeasure values/levels accessors");
        NominalScale s = new NominalScale("p", "q", "r");
        assertArrayEquals(new int[]{0, 1, 2}, s.values());
        assertArrayEquals(new String[]{"p", "q", "r"}, s.levels());
    }

    @Test
    public void testCategoricalHashCodeInParentClass() {
        System.out.println("CategoricalMeasure hashCode in base class");
        NominalScale a = new NominalScale("cat", "dog");
        NominalScale b = new NominalScale("cat", "dog");
        assertEquals(a.hashCode(), b.hashCode());
        NominalScale c = new NominalScale("cat", "fish");
        assertNotEquals(a.hashCode(), c.hashCode());
    }

    @Test
    public void testCategoricalToStringIntMethod() {
        System.out.println("CategoricalMeasure toString(int)");
        NominalScale s = new NominalScale("One", "Two", "Three");
        assertEquals("One",   s.toString(0));
        assertEquals("Three", s.toString(2));
    }

    @Test
    public void testCategoricalLargeNonStandardValues() {
        System.out.println("CategoricalMeasure non-standard sparse values (linear-scan path)");
        int[] vals   = {1000, 2000, 3000};
        String[] lvl = {"X",  "Y",  "Z"};
        NominalScale s = new NominalScale(vals, lvl);
        assertEquals(0, s.factor(1000));
        assertEquals(1, s.factor(2000));
        assertEquals(2, s.factor(3000));
        assertThrows(IllegalArgumentException.class, () -> s.factor(500));
    }

    @Test
    public void testCategoricalCompactNonStandardValues() {
        System.out.println("CategoricalMeasure compact non-standard values (array path)");
        NominalScale s = new NominalScale(new int[]{2, 3, 4}, new String[]{"A", "B", "C"});
        assertEquals(0, s.factor(2));
        assertEquals(1, s.factor(3));
        assertEquals(2, s.factor(4));
        assertThrows(IllegalArgumentException.class, () -> s.factor(0));
        assertThrows(IllegalArgumentException.class, () -> s.factor(5));
    }

    // =========================================================================
    // IntervalScale
    // =========================================================================

    @Test
    public void testIntervalScaleToString() {
        System.out.println("IntervalScale toString()");
        IntervalScale s = new IntervalScale(NumberFormat.getInstance());
        assertEquals("interval", s.toString());
    }

    @Test
    public void testIntervalScaleFormat() {
        System.out.println("IntervalScale toString(Object) and valueOf");
        IntervalScale s = new IntervalScale(NumberFormat.getNumberInstance());
        Number n = s.valueOf("42");
        assertNotNull(n);
        assertEquals(42L, n.longValue());
        assertNotNull(s.toString(42));
    }

    @Test
    public void testIntervalScaleValueOfInvalidThrows() {
        System.out.println("IntervalScale valueOf invalid throws");
        IntervalScale s = new IntervalScale(NumberFormat.getInstance());
        assertThrows(NumberFormatException.class, () -> s.valueOf("not-a-number"));
    }

    // =========================================================================
    // RatioScale
    // =========================================================================

    @Test
    public void testRatioScaleToString() {
        System.out.println("RatioScale toString()");
        RatioScale s = new RatioScale(NumberFormat.getInstance());
        assertEquals("ratio", s.toString());
    }

    @Test
    public void testRatioScaleCurrency() {
        System.out.println("Measure.Currency singleton");
        RatioScale c = Measure.Currency;
        assertNotNull(c);
        assertEquals("ratio", c.toString());
        String formatted = c.toString(1234.56);
        assertNotNull(formatted);
    }

    @Test
    public void testRatioScalePercent() {
        System.out.println("Measure.Percent singleton");
        RatioScale p = Measure.Percent;
        assertNotNull(p);
        String formatted = p.toString(0.75);
        assertNotNull(formatted);
        assertTrue(formatted.contains("%") || formatted.contains("75"),
            "Expected percent formatted, got: " + formatted);
    }

    @Test
    public void testRatioScaleValueOfInvalidThrows() {
        System.out.println("RatioScale valueOf invalid throws");
        RatioScale s = new RatioScale(NumberFormat.getInstance());
        assertThrows(NumberFormatException.class, () -> s.valueOf("not-a-number"));
    }

    // =========================================================================
    // NumericalMeasure thread safety
    // =========================================================================

    @Test
    public void testNumericalMeasureThreadSafety() throws Exception {
        System.out.println("NumericalMeasure thread safety");
        RatioScale scale = new RatioScale(NumberFormat.getNumberInstance());
        int threads = 8, ops = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int base = t * 1000;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < ops; i++) {
                    double v = base + i;
                    String s = scale.toString(v);
                    Number n = scale.valueOf(s);
                    assertEquals(v, n.doubleValue(), 1.0,
                        "Thread-safety violation: " + v + " != " + n);
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get();
        pool.shutdown();
    }

    // =========================================================================
    // Measure singletons
    // =========================================================================

    @Test
    public void testMeasureSingletons() {
        System.out.println("Measure singleton instances");
        assertNotNull(Measure.Currency);
        assertNotNull(Measure.Percent);
        assertSame(Measure.Currency, Measure.Currency);
        assertSame(Measure.Percent,  Measure.Percent);
    }
}
