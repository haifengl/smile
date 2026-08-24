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
package smile.llm.model.qwen;

import smile.llm.parallel.ParallelConfig;
import smile.llm.parallel.TensorShardSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for Qwen tensor-parallel shard sizing (no native lib required).
 *
 * <p>Full 27B multi-GPU load is exercised on the remote A100 box with
 * {@code -Dsmile.chat.tensor-parallel-size=2}.
 *
 * @author Haifeng Li
 */
public class QwenTensorParallelTest {

    @Test
    public void testGivenDefaultArgsWhenTpTwoThenLocalHeadsAndFfnDivide() {
        QwenModelArgs args = new QwenModelArgs();
        TensorShardSpec shard0 = TensorShardSpec.forRank(
                2, 0, args.numHeads(), args.numKvHeads(), args.intermediateSize(),
                args.linearNumKeyHeads(), args.linearNumValueHeads());
        TensorShardSpec shard1 = TensorShardSpec.forRank(
                2, 1, args.numHeads(), args.numKvHeads(), args.intermediateSize(),
                args.linearNumKeyHeads(), args.linearNumValueHeads());

        assertEquals(args.numHeads() / 2, shard0.numHeads());
        assertEquals(args.numKvHeads() / 2, shard0.numKvHeads());
        assertEquals(args.intermediateSize() / 2, shard0.intermediateSize());
        assertEquals(args.linearConvDim() / 2, args.linearConvDim(shard0));
        assertEquals(0, shard0.tpRank());
        assertEquals(1, shard1.tpRank());

        ParallelConfig cfg = ParallelConfig.tensorParallel((byte) 0, (byte) 1);
        assertEquals(2, cfg.tpSize());
        assertEquals(args.numKvHeads() / 2, args.kvCacheLayout(shard0).numKvHeads());
    }
}
