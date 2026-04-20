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
package smile.hpo;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Hyperparameters}.
 *
 * @author Haifeng Li
 */
public class HyperparametersTest {

    // -------------------------------------------------------------------------
    // add() – validation
    // -------------------------------------------------------------------------

    @Test
    public void givenNullName_whenAddInt_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add(null, 1));
    }

    @Test
    public void givenBlankName_whenAddDouble_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add("  ", 1.0));
    }

    @Test
    public void givenEmptyIntArray_whenAdd_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add("k", new int[]{}));
    }

    @Test
    public void givenEmptyDoubleArray_whenAdd_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add("k", new double[]{}));
    }

    @Test
    public void givenEmptyStringArray_whenAdd_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add("k", new String[]{}));
    }

    @Test
    public void givenEqualStartEnd_whenAddIntRange_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add("k", 5, 5, 1));
    }

    @Test
    public void givenNegativeStep_whenAddIntRange_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add("k", 0, 10, -1));
    }

    @Test
    public void givenEqualStartEnd_whenAddDoubleRange_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add("k", 1.0, 1.0, 0.1));
    }

    @Test
    public void givenNegativeStep_whenAddDoubleRange_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.add("k", 0.0, 1.0, -0.1));
    }

    // -------------------------------------------------------------------------
    // size(), remove(), clear()
    // -------------------------------------------------------------------------

    @Test
    public void givenMultipleParams_whenSize_thenReturnsCount() {
        // Given / When
        var hp = new Hyperparameters()
                .add("a", 1)
                .add("b", 2.0)
                .add("c", "x");
        // Then
        assertEquals(3, hp.size());
    }

    @Test
    public void givenRegisteredParam_whenRemove_thenSizeDecreases() {
        // Given
        var hp = new Hyperparameters().add("a", 1).add("b", 2);
        // When
        hp.remove("a");
        // Then
        assertEquals(1, hp.size());
    }

    @Test
    public void givenRegisteredParams_whenClear_thenSizeIsZero() {
        // Given
        var hp = new Hyperparameters().add("a", 1).add("b", 2);
        // When
        hp.clear();
        // Then
        assertEquals(0, hp.size());
    }

    @Test
    public void givenDuplicateAdd_whenSize_thenOverwritesSilently() {
        // Given
        var hp = new Hyperparameters().add("a", 1).add("a", 2);
        // When / Then
        assertEquals(1, hp.size());
    }

    // -------------------------------------------------------------------------
    // grid() – empty guard
    // -------------------------------------------------------------------------

    @Test
    public void givenNoParams_whenGrid_thenThrowsIllegalStateException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalStateException.class, hp::grid);
    }

    // -------------------------------------------------------------------------
    // grid() – single parameter
    // -------------------------------------------------------------------------

    @Test
    public void givenSingleFixedInt_whenGrid_thenOneConfiguration() {
        // Given
        var hp = new Hyperparameters().add("trees", 100);
        // When
        List<Properties> configs = hp.grid().toList();
        // Then
        assertEquals(1, configs.size());
        assertEquals("100", configs.getFirst().getProperty("trees"));
    }

    @Test
    public void givenSingleIntArray_whenGrid_thenOneConfigPerElement() {
        // Given
        var hp = new Hyperparameters().add("mtry", new int[]{2, 3, 4});
        // When
        List<Properties> configs = hp.grid().toList();
        // Then
        assertEquals(3, configs.size());
        assertEquals(Set.of("2", "3", "4"),
                configs.stream().map(p -> p.getProperty("mtry")).collect(Collectors.toSet()));
    }

    @Test
    public void givenSingleDoubleArray_whenGrid_thenOneConfigPerElement() {
        // Given
        var hp = new Hyperparameters().add("lr", new double[]{0.01, 0.1});
        // When
        List<Properties> configs = hp.grid().toList();
        // Then
        assertEquals(2, configs.size());
    }

    @Test
    public void givenSingleStringArray_whenGrid_thenOneConfigPerElement() {
        // Given
        var hp = new Hyperparameters().add("activation", new String[]{"relu", "sigmoid"});
        // When
        List<Properties> configs = hp.grid().toList();
        // Then
        assertEquals(2, configs.size());
        assertEquals(Set.of("relu", "sigmoid"),
                configs.stream().map(p -> p.getProperty("activation")).collect(Collectors.toSet()));
    }

    // -------------------------------------------------------------------------
    // grid() – Cartesian product
    // -------------------------------------------------------------------------

    @Test
    public void givenTwoDiscretePaParams_whenGrid_thenCartesianProduct() {
        // Given: 3 × 2 = 6 combinations
        var hp = new Hyperparameters()
                .add("mtry", new int[]{2, 3, 4})
                .add("depth", new int[]{5, 10});
        // When
        List<Properties> configs = hp.grid().toList();
        // Then
        assertEquals(6, configs.size());
        // Every configuration must contain both keys
        for (var p : configs) {
            assertNotNull(p.getProperty("mtry"));
            assertNotNull(p.getProperty("depth"));
        }
    }

    @Test
    public void givenThreeParams_whenGrid_thenAllCombinations() {
        // Given: 2 × 2 × 3 = 12 combinations
        var hp = new Hyperparameters()
                .add("a", new int[]{1, 2})
                .add("b", new String[]{"x", "y"})
                .add("c", new double[]{0.1, 0.2, 0.3});
        // When
        List<Properties> configs = hp.grid().toList();
        // Then
        assertEquals(12, configs.size());
    }

    // -------------------------------------------------------------------------
    // grid() – IntRange
    // -------------------------------------------------------------------------

    @Test
    public void givenIntRangeWithStep_whenGrid_thenCorrectValues() {
        // Given: [0, 10] step 5 → 0, 5, 10
        var hp = new Hyperparameters().add("nodes", 0, 10, 5);
        // When
        List<Properties> configs = hp.grid().toList();
        // Then
        assertEquals(3, configs.size());
        assertEquals(List.of("0", "5", "10"),
                configs.stream().map(p -> p.getProperty("nodes")).toList());
    }

    @Test
    public void givenIntRangeAutoStep_whenGrid_thenStartIncluded() {
        // Given: [10, 50] auto-step → step = max(1, 4) = 4
        var hp = new Hyperparameters().add("k", 10, 50);
        List<Properties> configs = hp.grid().toList();
        // First value must be the start
        assertEquals("10", configs.getFirst().getProperty("k"));
    }

    @Test
    public void givenIntRangeWithStep_whenGrid_thenNoValueExceedsEnd() {
        // Given: [0, 11] step 5 → 0, 5, 10  (11 is not generated, 15 > 11)
        var hp = new Hyperparameters().add("n", 0, 11, 5);
        List<Properties> configs = hp.grid().toList();
        // Then: all values <= 11
        for (var p : configs) {
            int v = Integer.parseInt(p.getProperty("n"));
            assertTrue(v <= 11, "Value " + v + " exceeds end (11)");
        }
    }

    // -------------------------------------------------------------------------
    // grid() – DoubleRange
    // -------------------------------------------------------------------------

    @Test
    public void givenDoubleRangeWithStep_whenGrid_thenCorrectCount() {
        // Given: [0.0, 1.0] step 0.25 → 0.0, 0.25, 0.50, 0.75, 1.0 = 5 values
        var hp = new Hyperparameters().add("lr", 0.0, 1.0, 0.25);
        List<Properties> configs = hp.grid().toList();
        assertEquals(5, configs.size());
    }

    @Test
    public void givenDoubleRangeWithStep_whenGrid_thenFirstValueIsStart() {
        // Given
        var hp = new Hyperparameters().add("alpha", 0.1, 0.5, 0.1);
        List<Properties> configs = hp.grid().toList();
        // Then: first value == start
        assertEquals(0.1, Double.parseDouble(configs.getFirst().getProperty("alpha")), 1e-9);
    }

    @Test
    public void givenDoubleRangeWithStep_whenGrid_thenNoValueExceedsEnd() {
        // Given
        var hp = new Hyperparameters().add("r", 0.0, 1.0, 0.3);
        List<Properties> configs = hp.grid().toList();
        for (var p : configs) {
            double v = Double.parseDouble(p.getProperty("r"));
            assertTrue(v <= 1.0 + 1e-9, "Value " + v + " exceeds end (1.0)");
        }
    }

    // -------------------------------------------------------------------------
    // grid() – insertion order preserved
    // -------------------------------------------------------------------------

    @Test
    public void givenMultipleParams_whenGrid_thenInsertionOrderPreserved() {
        // Given: registration order a, b, c
        var hp = new Hyperparameters()
                .add("a", new int[]{1, 2})
                .add("b", new int[]{3, 4});
        List<Properties> configs = hp.grid().toList();
        // The first two configs should vary "b" before advancing "a",
        // because we fix "a" first (outer loop = first parameter).
        assertEquals("1", configs.get(0).getProperty("a"));
        assertEquals("3", configs.get(0).getProperty("b"));
        assertEquals("1", configs.get(1).getProperty("a"));
        assertEquals("4", configs.get(1).getProperty("b"));
        assertEquals("2", configs.get(2).getProperty("a"));
        assertEquals("3", configs.get(2).getProperty("b"));
    }

    // -------------------------------------------------------------------------
    // random() – guards
    // -------------------------------------------------------------------------

    @Test
    public void givenNoParams_whenRandom_thenThrowsIllegalStateException() {
        // Given
        var hp = new Hyperparameters();
        // When / Then
        assertThrows(IllegalStateException.class, hp::random);
    }

    @Test
    public void givenNonPositiveN_whenRandomN_thenThrowsIllegalArgumentException() {
        // Given
        var hp = new Hyperparameters().add("k", 1);
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> hp.random(0));
        assertThrows(IllegalArgumentException.class, () -> hp.random(-1));
    }

    // -------------------------------------------------------------------------
    // random() – correctness
    // -------------------------------------------------------------------------

    @Test
    public void givenFixedIntParam_whenRandom_thenAlwaysReturnsSameValue() {
        // Given
        var hp = new Hyperparameters().add("trees", 100);
        // When
        List<Properties> configs = hp.random(20).toList();
        // Then: every config has trees = 100
        for (var p : configs) {
            assertEquals("100", p.getProperty("trees"));
        }
    }

    @Test
    public void givenIntArrayParam_whenRandom_thenValuesAreFromChoices() {
        // Given
        var hp = new Hyperparameters().add("mtry", new int[]{2, 3, 4});
        Set<String> allowed = Set.of("2", "3", "4");
        // When
        List<Properties> configs = hp.random(50).toList();
        // Then
        for (var p : configs) {
            assertTrue(allowed.contains(p.getProperty("mtry")));
        }
    }

    @Test
    public void givenStringArrayParam_whenRandom_thenValuesAreFromChoices() {
        // Given
        var hp = new Hyperparameters().add("act", new String[]{"relu", "tanh"});
        Set<String> allowed = Set.of("relu", "tanh");
        // When
        List<Properties> configs = hp.random(30).toList();
        // Then
        for (var p : configs) {
            assertTrue(allowed.contains(p.getProperty("act")));
        }
    }

    @Test
    public void givenIntRangeParam_whenRandom_thenValuesWithinBounds() {
        // Given: range [10, 50]
        var hp = new Hyperparameters().add("nodes", 10, 50);
        // When
        List<Properties> configs = hp.random(100).toList();
        // Then
        for (var p : configs) {
            int v = Integer.parseInt(p.getProperty("nodes"));
            assertTrue(v >= 10 && v < 50, "Value out of [10, 50): " + v);
        }
    }

    @Test
    public void givenDoubleRangeParam_whenRandom_thenValuesWithinBounds() {
        // Given: range [0.0, 1.0]
        var hp = new Hyperparameters().add("lr", 0.0, 1.0);
        // When
        List<Properties> configs = hp.random(100).toList();
        // Then
        for (var p : configs) {
            double v = Double.parseDouble(p.getProperty("lr"));
            assertTrue(v >= 0.0 && v < 1.0, "Value out of [0.0, 1.0): " + v);
        }
    }

    @Test
    public void givenParams_whenRandomN_thenExactlyNConfigs() {
        // Given
        var hp = new Hyperparameters().add("k", new int[]{1, 2, 3});
        // When
        List<Properties> configs = hp.random(17).toList();
        // Then
        assertEquals(17, configs.size());
    }

    // -------------------------------------------------------------------------
    // Integration: grid + random on the same Hyperparameters instance
    // -------------------------------------------------------------------------

    @Test
    public void givenHyperparameters_whenGridAndRandomBothCalled_thenBothWork() {
        // Given
        var hp = new Hyperparameters()
                .add("trees", 100)
                .add("mtry", new int[]{2, 3})
                .add("max.nodes", 50, 200, 50);

        // When – grid
        long gridCount = hp.grid().count();
        // Then: 1 × 2 × 4 = 8 (max.nodes: 50,100,150,200)
        assertEquals(8, gridCount);

        // When – random
        long randomCount = hp.random(10).count();
        // Then
        assertEquals(10, randomCount);
    }

    // -------------------------------------------------------------------------
    // Integration: remove then grid
    // -------------------------------------------------------------------------

    @Test
    public void givenRemovedParam_whenGrid_thenRemovedKeyAbsent() {
        // Given
        var hp = new Hyperparameters()
                .add("a", new int[]{1, 2})
                .add("b", new int[]{3, 4});
        hp.remove("b");

        // When
        List<Properties> configs = hp.grid().toList();

        // Then: only "a" keys, 2 configs
        assertEquals(2, configs.size());
        for (var p : configs) {
            assertNull(p.getProperty("b"));
            assertNotNull(p.getProperty("a"));
        }
    }

    // -------------------------------------------------------------------------
    // Integration: clear then grid throws
    // -------------------------------------------------------------------------

    @Test
    public void givenClearedParams_whenGrid_thenThrowsIllegalStateException() {
        // Given
        var hp = new Hyperparameters().add("a", 1).clear();
        // When / Then
        assertThrows(IllegalStateException.class, hp::grid);
    }
}

