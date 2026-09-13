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

import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

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
    public void testGivenWarmConvStateWhenMultiTokenUpdateThenMatchesSequentialS1() {
        // Given – warm conv state, then a 3-token continuation
        int batch = 1, channels = 4, kernel = 4, stateLen = kernel - 1;
        Tensor weight = Tensor.randn(channels, kernel);
        Tensor stateWindow = Tensor.randn(batch, channels, stateLen);
        Tensor stateSeq = stateWindow.copy();
        Tensor hidden = Tensor.randn(batch, channels, 3);

        // When – one multi-token Update vs three S=1 Updates
        Tensor outWindow = GatedDeltaRule.causalConv1dUpdate(hidden, stateWindow, weight);
        Tensor step0;
        Tensor step1;
        Tensor step2;
        try (var s0 = Index.slice(0, 1);
             var s1 = Index.slice(1, 2);
             var s2 = Index.slice(2, 3);
             Tensor h0 = hidden.get(Index.Colon, Index.Colon, s0);
             Tensor h1 = hidden.get(Index.Colon, Index.Colon, s1);
             Tensor h2 = hidden.get(Index.Colon, Index.Colon, s2)) {
            step0 = GatedDeltaRule.causalConv1dUpdate(h0, stateSeq, weight);
            step1 = GatedDeltaRule.causalConv1dUpdate(h1, stateSeq, weight);
            step2 = GatedDeltaRule.causalConv1dUpdate(h2, stateSeq, weight);
        }
        try (Tensor cat01 = GatedDeltaRule.concatLast3(step0, step1);
             Tensor cat = GatedDeltaRule.concatLast3(cat01, step2);
             Tensor diff = outWindow.sub(cat).abs();
             Tensor total = diff.sum()) {
            double mae = total.doubleValue() / outWindow.length();
            assertTrue(mae < 1e-5, "multi-token Update vs S=1 Update MAE=" + mae);
        }
        try (Tensor stateDiff = stateWindow.sub(stateSeq).abs();
             Tensor stateTotal = stateDiff.sum()) {
            assertTrue(stateTotal.doubleValue() < 1e-5,
                    "conv state after multi-token Update must match sequential S=1");
        }

        // Prefill zeros left context and must diverge from Update on warm state
        Tensor stateForPrefill = Tensor.randn(batch, channels, stateLen);
        Tensor stateForUpdate = stateForPrefill.copy();
        Tensor outPrefill = GatedDeltaRule.causalConv1dPrefill(hidden, stateForPrefill, weight);
        Tensor outUpdate = GatedDeltaRule.causalConv1dUpdate(hidden, stateForUpdate, weight);
        try (Tensor diff = outPrefill.sub(outUpdate).abs();
             Tensor total = diff.sum()) {
            assertTrue(total.doubleValue() > 1e-3,
                    "Prefill ignores warm conv left context; must differ from Update");
        }

        outWindow.close();
        step0.close();
        step1.close();
        step2.close();
        outPrefill.close();
        outUpdate.close();
        hidden.close();
        weight.close();
        stateWindow.close();
        stateSeq.close();
        stateForPrefill.close();
        stateForUpdate.close();
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
    public void testGivenFloatPoolStateWhenRecurrentThenMutatesInPlace() {
        int batch = 1, seq = 2, heads = 2, kDim = 4, vDim = 4;
        Tensor q = Tensor.ones(batch, seq, heads, kDim);
        Tensor k = Tensor.ones(batch, seq, heads, kDim);
        Tensor v = Tensor.ones(batch, seq, heads, vDim);
        Tensor g = Tensor.full(-1.0, batch, seq, heads);
        Tensor beta = Tensor.full(0.5, batch, seq, heads);
        Tensor pool = Tensor.zeros(batch, heads, kDim, vDim);

        var result = GatedDeltaRule.recurrentGatedDeltaRule(
                q, k, v, g, beta, pool, true, true);
        assertArrayEquals(new long[]{batch, seq, heads, vDim}, result._1().shape());
        assertNull(result._2());
        // Pool was mutated (not left at zeros after a non-trivial step).
        try (Tensor s = pool.abs().sum()) {
            assertTrue(s.doubleValue() > 0.0);
        }
        result._1().close();
        q.close(); k.close(); v.close(); g.close(); beta.close();
        pool.close();
    }

    @Test
    public void testGivenNativeAndJavaWhenSameInputsThenOutputsClose() {
        int batch = 1, seq = 2, heads = 2, kDim = 4, vDim = 4;
        Tensor q = Tensor.randn(batch, seq, heads, kDim);
        Tensor k = Tensor.randn(batch, seq, heads, kDim);
        Tensor v = Tensor.randn(batch, seq, heads, vDim);
        Tensor g = Tensor.randn(batch, seq, heads);
        Tensor beta = Tensor.full(0.5, batch, seq, heads);
        Tensor poolNative = Tensor.zeros(batch, heads, kDim, vDim);
        Tensor poolJava = Tensor.zeros(batch, heads, kDim, vDim);

        var nativeOut = smile.torch.Native.recurrentGatedDeltaRule(
                q, k, v, g, beta, poolNative, true);
        Assumptions.assumeTrue(nativeOut != null, "native gated-delta unavailable");

        var javaOut = GatedDeltaRule.recurrentGatedDeltaRuleJava(
                q, k, v, g, beta, poolJava, true, true);
        try (Tensor diff = nativeOut.sub(javaOut._1()).abs();
             Tensor total = diff.sum()) {
            double mae = total.doubleValue() / nativeOut.length();
            assertTrue(mae < 1e-4, "native vs java MAE=" + mae);
        }
        nativeOut.close();
        javaOut._1().close();
        q.close(); k.close(); v.close(); g.close(); beta.close();
        poolNative.close();
        poolJava.close();
    }

    @Test
    public void testGivenDeltaNetStatePoolWhenResetThenBoundBatchSet() {
        try (var pool = new DeltaNetStatePool(2, 4, 8, 8, 32, 4, 2, Device.CPU(), ScalarType.Float)) {
            pool.reset(1);
            assertEquals(1, pool.boundBatch());
            assertEquals(2, pool.numLinearLayers());
        }
    }

    @Test
    public void testGivenMaxBatchPoolWhenActivateOneThenActiveRecurrentIsBatchOne() {
        try (var pool = new DeltaNetStatePool(1, 4, 8, 8, 32, 4, 16, Device.CPU(), ScalarType.Float)) {
            pool.bindRequest(1);
            pool.activateStep(1);
            assertEquals(1, pool.boundBatch());
            assertArrayEquals(new long[]{16, 4, 8, 8}, pool.recurrent(0).shape());
            assertArrayEquals(new long[]{1, 4, 8, 8}, pool.activeRecurrent(0).shape());
            assertArrayEquals(new long[]{1, 32, 3}, pool.activeConv(0).shape());
        }
    }

    @Test
    public void testGivenPooledStateWhenPrefillSeq16ThenJavaRecurrentSucceeds() {
        int batch = 1, seq = 16, heads = 24, kDim = 128, vDim = 128;
        int maxBatch = 16;
        try (var statePool = new DeltaNetStatePool(
                1, heads, kDim, vDim, 32, 4, maxBatch, Device.CPU(), ScalarType.Float)) {
            statePool.bindRequest(42);
            statePool.activateStep(42);
            Tensor q = Tensor.randn(batch, seq, heads, kDim);
            Tensor k = Tensor.randn(batch, seq, heads, kDim);
            Tensor v = Tensor.randn(batch, seq, heads, vDim);
            Tensor g = Tensor.randn(batch, seq, heads);
            Tensor beta = Tensor.full(0.5, batch, seq, heads);
            Tensor state = statePool.activeRecurrent(0);
            var result = GatedDeltaRule.recurrentGatedDeltaRuleJava(
                    q, k, v, g, beta, state, true, true);
            assertArrayEquals(new long[]{batch, seq, heads, vDim}, result._1().shape());
            assertNull(result._2());
            result._1().close();
            q.close();
            k.close();
            v.close();
            g.close();
            beta.close();
        }
    }
}
