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
package smile.llm.parallel;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuxDevicePlacement} (no GPU required).
 *
 * @author Haifeng Li
 */
public class AuxDevicePlacementTest {

    private static UnaryOperator<String> lookup(Map<String, String> values) {
        return values::get;
    }

    @Test
    public void testGivenSingleGpuUnsetWhenResolvedThenBothDefaultToRank0() {
        // Given
        byte[] devices = {0};

        // When
        AuxDevicePlacement p = AuxDevicePlacement.resolve(
                devices, Optional.empty(), Optional.empty(), lookup(Map.of()), lookup(Map.of()));

        // Then
        assertEquals(0, p.dspark().device().index());
        assertEquals(0, p.dspark().tpRank());
        assertFalse(p.dspark().explicit());
        assertFalse(p.dspark().isExternal());

        assertEquals(0, p.vision().device().index());
        assertEquals(0, p.vision().tpRank());
        assertFalse(p.vision().explicit());
        assertFalse(p.vision().isExternal());
    }

    @Test
    public void testGivenMultiGpuUnsetWhenResolvedThenAutoBalancesAcrossRanks() {
        // Given — non-zero starting GPUs to verify rank-to-device mapping
        byte[] devices = {4, 5, 6, 7};

        // When
        AuxDevicePlacement p = AuxDevicePlacement.resolve(
                devices, Optional.empty(), Optional.empty(), lookup(Map.of()), lookup(Map.of()));

        // Then — DSpark on rank 0 (GPU 4), vision auto-balanced onto rank 1 (GPU 5)
        assertEquals(4, p.dspark().device().index());
        assertEquals(0, p.dspark().tpRank());
        assertTrue(p.dspark().isInGroup());
        assertFalse(p.dspark().explicit());

        assertEquals(5, p.vision().device().index());
        assertEquals(1, p.vision().tpRank());
        assertTrue(p.vision().isInGroup());
        assertFalse(p.vision().explicit());
    }

    @Test
    public void testGivenExplicitDsparkOnRank1WhenVisionUnsetThenVisionAutoBalancesToRank0() {
        // Given
        byte[] devices = {0, 1};

        // When
        AuxDevicePlacement p = AuxDevicePlacement.resolve(
                devices, Optional.empty(), Optional.of(1), lookup(Map.of()), lookup(Map.of()));

        // Then
        assertEquals(1, p.dspark().device().index());
        assertEquals(1, p.dspark().tpRank());
        assertTrue(p.dspark().explicit());

        assertEquals(0, p.vision().device().index());
        assertEquals(0, p.vision().tpRank());
        assertFalse(p.vision().explicit());
    }

    @Test
    public void testGivenExplicitVisionOnRank0WhenDsparkUnsetThenDsparkAutoBalancesToRank1() {
        // Given
        byte[] devices = {2, 3};

        // When
        AuxDevicePlacement p = AuxDevicePlacement.resolve(
                devices, Optional.of(2), Optional.empty(), lookup(Map.of()), lookup(Map.of()));

        // Then
        assertEquals(2, p.vision().device().index());
        assertEquals(0, p.vision().tpRank());
        assertTrue(p.vision().explicit());

        assertEquals(3, p.dspark().device().index());
        assertEquals(1, p.dspark().tpRank());
        assertFalse(p.dspark().explicit());
    }

    @Test
    public void testGivenExternalGpuOverridesWhenResolvedThenMarksExternalAndRankMinusOne() {
        // Given — TP group is [0, 1], vision pinned to GPU 2, DSpark pinned to GPU 3
        byte[] devices = {0, 1};

        // When
        AuxDevicePlacement p = AuxDevicePlacement.resolve(
                devices, Optional.of(2), Optional.of(3), lookup(Map.of()), lookup(Map.of()));

        // Then
        assertEquals(2, p.vision().device().index());
        assertEquals(-1, p.vision().tpRank());
        assertTrue(p.vision().isExternal());
        assertFalse(p.vision().isInGroup());
        assertTrue(p.vision().explicit());

        assertEquals(3, p.dspark().device().index());
        assertEquals(-1, p.dspark().tpRank());
        assertTrue(p.dspark().isExternal());
        assertFalse(p.dspark().isInGroup());
        assertTrue(p.dspark().explicit());
    }

    @Test
    public void testGivenSingleGpuWithExternalOffloadWhenResolvedThenOtherStaysOnRank0() {
        // Given
        byte[] devices = {0};

        // When
        AuxDevicePlacement p = AuxDevicePlacement.resolve(
                devices, Optional.of(1), Optional.empty(), lookup(Map.of()), lookup(Map.of()));

        // Then
        assertEquals(1, p.vision().device().index());
        assertTrue(p.vision().isExternal());
        assertEquals(0, p.dspark().device().index());
        assertEquals(0, p.dspark().tpRank());
        assertFalse(p.dspark().isExternal());
    }

    @Test
    public void testGivenSysPropAndEnvWhenResolvedThenPrecedenceIsOverrideOverPropOverEnv() {
        // Given
        byte[] devices = {0, 1, 2, 3};
        var props = lookup(Map.of(
                AuxDevicePlacement.VISION_DEVICE_PROPERTY, "2",
                AuxDevicePlacement.DSPARK_DEVICE_PROPERTY, "  "));
        var env = lookup(Map.of(
                AuxDevicePlacement.VISION_DEVICE_ENV, "3",
                AuxDevicePlacement.DSPARK_DEVICE_ENV, "3"));

        // When — no caller overrides: vision takes sysprop (2), dspark skips blank sysprop and takes env (3)
        AuxDevicePlacement fromPropEnv = AuxDevicePlacement.resolve(
                devices, Optional.empty(), Optional.empty(), props, env);

        // Then
        assertEquals(2, fromPropEnv.vision().device().index());
        assertEquals(2, fromPropEnv.vision().tpRank());
        assertTrue(fromPropEnv.vision().explicit());
        assertEquals(3, fromPropEnv.dspark().device().index());
        assertEquals(3, fromPropEnv.dspark().tpRank());
        assertTrue(fromPropEnv.dspark().explicit());

        // When — caller override beats sysprop and env
        AuxDevicePlacement fromOverride = AuxDevicePlacement.resolve(
                devices, Optional.of(0), Optional.of(1), props, env);
        assertEquals(0, fromOverride.vision().device().index());
        assertEquals(1, fromOverride.dspark().device().index());
    }

    @Test
    public void testGivenCpuModeWhenUnsetThenDefaultsToCpu() {
        // When
        AuxDevicePlacement p = AuxDevicePlacement.resolve(
                null, Optional.empty(), Optional.empty(), lookup(Map.of()), lookup(Map.of()));

        // Then
        assertTrue(p.vision().device().isCPU());
        assertFalse(p.vision().isExternal());
        assertTrue(p.dspark().device().isCPU());
        assertFalse(p.dspark().isExternal());
    }

    @Test
    public void testGivenInvalidDeviceIndexWhenResolvedThenThrowsIllegalArgument() {
        byte[] devices = {0, 1};
        assertThrows(IllegalArgumentException.class, () -> AuxDevicePlacement.resolve(
                devices, Optional.of(-1), Optional.empty(), lookup(Map.of()), lookup(Map.of())));
        assertThrows(IllegalArgumentException.class, () -> AuxDevicePlacement.resolve(
                devices, Optional.empty(), Optional.of(128), lookup(Map.of()), lookup(Map.of())));
        assertThrows(IllegalArgumentException.class, () -> AuxDevicePlacement.resolve(
                devices, Optional.empty(), Optional.empty(),
                lookup(Map.of(AuxDevicePlacement.VISION_DEVICE_PROPERTY, "gpu1")), lookup(Map.of())));
        assertThrows(IllegalArgumentException.class, () -> AuxDevicePlacement.resolve(
                devices, Optional.empty(), Optional.empty(),
                lookup(Map.of()), lookup(Map.of(AuxDevicePlacement.DSPARK_DEVICE_ENV, "-2"))));
    }
}
