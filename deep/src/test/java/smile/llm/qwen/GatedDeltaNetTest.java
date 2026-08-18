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
package smile.llm.qwen;

import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Gated DeltaNet recurrence helpers.
 *
 * @author Haifeng Li
 */
public class GatedDeltaNetTest {

    @Test
    public void testGivenRecurrentStepWhenRunThenOutputAndStateShapesMatch() {
        int batch = 1, seq = 3, heads = 2, kDim = 4, vDim = 4;
        Tensor q = Tensor.ones(batch, seq, heads, kDim);
        Tensor k = Tensor.ones(batch, seq, heads, kDim);
        Tensor v = Tensor.ones(batch, seq, heads, vDim);
        Tensor g = Tensor.full(-1.0, batch, seq, heads); // decay logits
        Tensor beta = Tensor.full(0.5, batch, seq, heads);

        var result = GatedDeltaRule.recurrentGatedDeltaRule(q, k, v, g, beta, null, true, true);
        assertArrayEquals(new long[]{batch, seq, heads, vDim}, result._1().shape());
        assertNotNull(result._2());
        assertArrayEquals(new long[]{batch, heads, kDim, vDim}, result._2().shape());
        result._1().close();
        result._2().close();
        q.close(); k.close(); v.close(); g.close(); beta.close();
    }

    @Test
    public void testGivenCausalConvWhenUpdatedThenStateLengthPreserved() {
        int batch = 1, channels = 4, seq = 2, kernel = 4;
        Tensor hidden = Tensor.ones(batch, channels, seq);
        Tensor state = Tensor.zeros(batch, channels, kernel - 1);
        Tensor weight = Tensor.ones(channels, kernel);
        Tensor out = GatedDeltaRule.causalConv1dUpdate(hidden, state, weight);
        assertArrayEquals(new long[]{batch, channels, seq}, out.shape());
        assertArrayEquals(new long[]{batch, channels, kernel - 1}, state.shape());
        out.close();
        hidden.close();
        state.close();
        weight.close();
    }

    @Test
    public void testGivenDeltaNetStatePoolWhenResetThenBoundBatchSet() {
        try (var pool = new DeltaNetStatePool(2, 4, 8, 8, 32, 4, 2, Device.CPU(), ScalarType.Float)) {
            pool.reset(1);
            assertEquals(1, pool.boundBatch());
            assertEquals(2, pool.numLinearLayers());
        }
    }
}
