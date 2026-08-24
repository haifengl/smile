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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for parallel config / shard math (no GPU required).
 *
 * @author Haifeng Li
 */
public class ParallelConfigTest {

    @Test
    public void testGivenSingleDeviceWhenCreatedThenTpSizeIsOne() {
        ParallelConfig cfg = ParallelConfig.single((byte) 0);
        assertEquals(1, cfg.tpSize());
        assertEquals(1, cfg.ppSize());
        assertFalse(cfg.isTensorParallel());
        ParallelState state = new ParallelState(cfg, 0);
        assertTrue(state.isTpRoot());
        assertTrue(state.isFirstStage());
        assertTrue(state.isLastStage());
    }

    @Test
    public void testGivenTwoDevicesWhenTensorParallelThenRanksMapToDevices() {
        ParallelConfig cfg = ParallelConfig.tensorParallel((byte) 0, (byte) 1);
        assertEquals(2, cfg.tpSize());
        assertTrue(cfg.isTensorParallel());
        assertEquals(0, new ParallelState(cfg, 0).deviceIndex());
        assertEquals(1, new ParallelState(cfg, 1).deviceIndex());
        assertEquals(1, new ParallelState(cfg, 1).globalRank());
    }

    @Test
    public void testGivenPpGreaterThanOneWhenCreatedThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelConfig(2, 2, 1, new byte[]{0, 1}));
    }

    @Test
    public void testGivenShardSpecWhenHeadsNotDivisibleThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> TensorShardSpec.forRank(2, 0, 3, 2, 128, 2, 4));
    }

    @Test
    public void testGivenShardSpecWhenValidThenLocalSizesHalve() {
        TensorShardSpec s = TensorShardSpec.forRank(2, 1, 8, 4, 256, 4, 8);
        assertEquals(4, s.numHeads());
        assertEquals(2, s.numKvHeads());
        assertEquals(128, s.intermediateSize());
        assertEquals(2, s.linearNumKeyHeads());
        assertEquals(4, s.linearNumValueHeads());
        assertEquals(1, s.tpRank());
    }
}
