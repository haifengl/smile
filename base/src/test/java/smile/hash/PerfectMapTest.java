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
package smile.hash;

import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PerfectMap}.
 *
 * @author Haifeng Li
 */
public class PerfectMapTest {

    public PerfectMapTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testPerfectMap() {
        System.out.println("PerfectMap basic lookup");
        PerfectMap<Integer> map = new PerfectMap.Builder<Integer>()
            .add("one", 1)
            .add("two", 2)
            .add("three", 3)
            .build();

        assertEquals(1, map.get("one"));
        assertEquals(2, map.get("two"));
        assertEquals(3, map.get("three"));
        assertNull(map.get("four"));
        assertNull(map.get("zero"));
    }

    @Test
    public void testPerfectMapFromExistingMap() {
        System.out.println("PerfectMap from existing Map");
        Map<String, String> source = Map.of(
            "red",   "#FF0000",
            "green", "#00FF00",
            "blue",  "#0000FF"
        );
        PerfectMap<String> map = new PerfectMap.Builder<String>(source).build();

        assertEquals("#FF0000", map.get("red"));
        assertEquals("#00FF00", map.get("green"));
        assertEquals("#0000FF", map.get("blue"));
        assertNull(map.get("yellow"));
    }

    @Test
    public void testPerfectMapNullOnMiss() {
        System.out.println("PerfectMap returns null for missing key");
        PerfectMap<Object> map = new PerfectMap.Builder<>()
            .add("key", new Object())
            .build();

        assertNull(map.get("missing"));
        assertNull(map.get(""));
    }

    @Test
    public void testPerfectMapLargeSet() {
        System.out.println("PerfectMap large set");
        // Use Greek letters to ensure distinct character signatures
        String[] keys = {
            "alpha", "beta", "gamma", "delta", "epsilon",
            "zeta", "eta", "theta", "iota", "kappa",
            "lambda", "mu", "nu", "xi", "omicron",
            "pi", "rho", "sigma", "tau", "upsilon",
            "phi", "chi", "psi", "omega"
        };
        var builder = new PerfectMap.Builder<Integer>();
        for (int i = 0; i < keys.length; i++) {
            builder.add(keys[i], i);
        }
        PerfectMap<Integer> map = builder.build();

        for (int i = 0; i < keys.length; i++) {
            assertEquals(i, map.get(keys[i]), "Wrong value for key: " + keys[i]);
        }
        assertNull(map.get("missing"));
    }

    @Test
    public void testPerfectMapValueOrderingIsCorrect() {
        System.out.println("PerfectMap value-key alignment");
        // Deliberately add many keys to stress HashMap ordering non-determinism
        var builder = new PerfectMap.Builder<String>();
        String[] keys = {"alpha", "beta", "gamma", "delta", "epsilon",
                         "zeta", "eta", "theta", "iota", "kappa"};
        for (String k : keys) {
            builder.add(k, k.toUpperCase());
        }
        PerfectMap<String> map = builder.build();
        for (String k : keys) {
            assertEquals(k.toUpperCase(), map.get(k),
                "Value mismatch for key: " + k);
        }
    }
}

