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
package smile.llm.model.minimax;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCachePool;
import smile.torch.Native;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke and consistency tests for the MiniMax-M3 reference model (tiny random weights, CPU).
 *
 * @author Haifeng Li
 */
public class MiniMaxModelTest {
    private static MiniMaxModelArgs args() throws IOException {
        return MiniMaxModelArgs.fromHuggingFace("deep/src/test/resources/minimax/config_tiny.json", 2, 0);
    }

    private static MiniMaxModel tinyModel(MiniMaxModelArgs args) {
        MiniMaxModel model = new MiniMaxModel(args);
        // Random routers so top-k is not decided by ties.
        for (MiniMaxBlock block : model.layers()) {
            if (block.moe() != null) {
                try (var noGrad = Tensor.noGradGuard();
                     Tensor r = Tensor.randn(args.numExperts(), args.dim())) {
                    Native.copy_(block.moe().gateWeight, r);
                }
            }
        }
        model.to(Device.CPU());
        model.setKvCachePool(KvCachePool.forTesting(args.kvCacheLayout(), Device.CPU()), false);
        model.eval();
        return model;
    }

    @Test
    public void testGivenTinyArgsWhenModelConstructedThenBlocksMatchLayerKinds() throws IOException {
        MiniMaxModelArgs args = args();
        MiniMaxModel model = tinyModel(args);
        assertEquals(4, model.layers().size());
        assertNotNull(model.layers().get(0).mlp());
        assertNull(model.layers().get(0).moe());
        assertNotNull(model.layers().get(3).moe());
        assertNull(model.layers().get(3).mlp());
        assertEquals(args.numLayers(), model.kvCachePool().numLayers());
    }

    @Test
    public void testGivenPrefillWhenAllTokenLogitsThenShapeMatches() throws IOException {
        MiniMaxModelArgs args = args();
        MiniMaxModel model = tinyModel(args);
        KvCachePool pool = model.kvCachePool();
        pool.bindRequests(1, 8);
        try (Tensor tokens = Tensor.of(new long[]{1, 2, 3, 4}, 1, 4);
             Tensor all = model.forward(tokens, 0, true)) {
            assertArrayEquals(new long[]{1, 4, args.vocabSize()}, all.shape());
        } finally {
            pool.unbindRequests();
        }
    }

    @Test
    public void testGivenCachedDecodeWhenComparedToFullPrefillThenLogitsMatch() throws IOException {
        MiniMaxModelArgs args = args();
        MiniMaxModel model = tinyModel(args);
        KvCachePool pool = model.kvCachePool();
        long[] seq = {5, 9, 11, 3, 7};

        pool.bindRequests(1, 8);
        float[] full;
        try (Tensor tokens = Tensor.of(seq, 1, 5);
             Tensor logits = model.forward(tokens, 0, false)) {
            full = logits.floatArray();
        } finally {
            pool.unbindRequests();
        }

        pool.bindRequests(1, 8);
        float[] incremental;
        try (Tensor prefix = Tensor.of(new long[]{5, 9, 11, 3}, 1, 4);
             Tensor ignored = model.forward(prefix, 0, false);
             Tensor next = Tensor.of(new long[]{7}, 1, 1);
             Tensor logits = model.forward(next, new int[]{4})) {
            incremental = logits.floatArray();
        } finally {
            pool.unbindRequests();
        }

        assertEquals(full.length, incremental.length);
        for (int i = 0; i < full.length; i++) {
            assertEquals(full[i], incremental[i], 1e-3f, "logit " + i);
        }
    }

    @Test
    public void testGivenRandomHiddenWhenRoutedThenTopKDistinctAndWeightsScaled() throws IOException {
        MiniMaxModelArgs args = args();
        MiniMaxModel model = tinyModel(args);
        MiniMaxMoE moe = model.layers().get(3).moe();
        int tokens = 6;
        try (Tensor x = Tensor.randn(tokens, args.dim())) {
            MiniMaxMoE.Routing r = moe.route(x);
            int k = args.numExpertsPerTok();
            assertEquals(tokens * k, r.experts().length);
            for (int t = 0; t < tokens; t++) {
                Set<Long> seen = new HashSet<>();
                float sum = 0;
                for (int j = 0; j < k; j++) {
                    long e = r.experts()[t * k + j];
                    assertTrue(e >= 0 && e < args.numExperts());
                    assertTrue(seen.add(e), "duplicate expert for token " + t);
                    sum += r.weights()[t * k + j];
                }
                assertEquals(args.routedScalingFactor(), sum, 1e-4, "weights sum to routed_scaling_factor");
            }
        }
    }

    @Test
    public void testGivenMoeLayerWhenForwardThenShapeAndFiniteOutput() throws IOException {
        MiniMaxModelArgs args = args();
        MiniMaxModel model = tinyModel(args);
        MiniMaxMoE moe = model.layers().get(3).moe();
        try (Tensor x = Tensor.randn(2, 3, args.dim());
             Tensor y = moe.forward(x)) {
            assertArrayEquals(new long[]{2, 3, args.dim()}, y.shape());
            for (float v : y.copy().floatArray()) {
                assertTrue(Float.isFinite(v));
            }
        }
    }

    @Test
    public void testGivenAuxLayersWhenForwardAuxThenTapsAreLayerOutputs() throws IOException {
        MiniMaxModelArgs args = args();
        MiniMaxModel model = tinyModel(args);
        KvCachePool pool = model.kvCachePool();
        int dim = args.dim();
        long[] seq = {5, 9, 11, 3, 7};
        model.setAuxLayers(0, 2, 3);
        assertArrayEquals(new int[]{0, 2, 3}, model.auxLayers());

        pool.bindRequests(1, 8);
        float[] plain;
        try (Tensor tokens = Tensor.of(seq, 1, 5);
             Tensor logits = model.forward(tokens, 0, false)) {
            plain = logits.floatArray();
        } finally {
            pool.unbindRequests();
        }

        pool.bindRequests(1, 8);
        float[] withAux;
        float[] viaTap;
        try (Tensor tokens = Tensor.of(seq, 1, 5);
             Tensor logits = model.forwardAux(tokens, 0, false, null, null);
             Tensor aux = model.takeAux()) {
            withAux = logits.floatArray();
            assertArrayEquals(new long[]{5, 3L * dim}, aux.shape());
            assertNull(model.takeAux(), "taps are handed out once");
            // The last layer's tap is the stream the final norm and the output head consume.
            try (Tensor last = aux.get(smile.deep.tensor.Index.Colon, smile.deep.tensor.Index.slice(2L * dim, 3L * dim));
                 Tensor row = last.get(smile.deep.tensor.Index.slice(4, 5)).unsqueeze(0).copy();
                 Tensor normed = model.norm.forward(row);
                 Tensor projected = model.lmHead.forward(normed).to(smile.deep.tensor.ScalarType.Float).copy()) {
                viaTap = projected.floatArray();
            }
            float[] t = aux.to(smile.deep.tensor.ScalarType.Float).floatArray();
            boolean differs = false;
            for (int k = 0; k < t.length; k++) {
                assertTrue(Float.isFinite(t[k]));
                // layer 0 and layer 2 taps differ
                if (k % (3 * dim) < dim && t[k] != t[k + dim]) {
                    differs = true;
                }
            }
            assertTrue(differs);
        } finally {
            pool.unbindRequests();
        }
        // Capturing must not change the logits.
        assertArrayEquals(plain, withAux, 0f);
        assertEquals(withAux.length, viaTap.length);
        for (int k = 0; k < viaTap.length; k++) {
            assertEquals(withAux[k], viaTap[k], 1e-4f, "logit " + k);
        }
    }

    @Test
    public void testGivenAuxLayersWhenDecodeThenTapEqualsWindowRow() throws IOException {
        MiniMaxModelArgs args = args();
        MiniMaxModel model = tinyModel(args);
        KvCachePool pool = model.kvCachePool();
        int dim = args.dim();
        model.setAuxLayers(1, 3);

        pool.bindRequests(1, 8);
        float[] window;
        try (Tensor tokens = Tensor.of(new long[]{5, 9, 11, 3, 7}, 1, 5);
             Tensor logits = model.forwardAux(tokens, 0, false, null, null);
             Tensor aux = model.takeAux()) {
            window = aux.to(smile.deep.tensor.ScalarType.Float).floatArray();
        } finally {
            pool.unbindRequests();
        }

        pool.bindRequests(1, 8);
        float[] decode;
        try (Tensor prefix = Tensor.of(new long[]{5, 9, 11, 3}, 1, 4);
             Tensor ignored = model.forward(prefix, 0, false);
             Tensor next = Tensor.of(new long[]{7}, 1, 1);
             Tensor logits = model.forwardAux(next, new int[]{4});
             Tensor aux = model.takeAux()) {
            assertArrayEquals(new long[]{1, 2L * dim}, aux.shape());
            decode = aux.to(smile.deep.tensor.ScalarType.Float).floatArray();
        } finally {
            pool.unbindRequests();
        }
        for (int i = 0; i < 2 * dim; i++) {
            assertEquals(window[4 * 2 * dim + i], decode[i], 2e-3f, "tap element " + i);
        }
    }

    @Test
    public void testGivenBadAuxLayersThenRejected() throws IOException {
        MiniMaxModel model = tinyModel(args());
        assertThrows(IllegalArgumentException.class, () -> model.setAuxLayers(2, 2));
        assertThrows(IllegalArgumentException.class, () -> model.setAuxLayers(0, 4));
        assertThrows(IllegalArgumentException.class, () -> model.setAuxLayers(-1));
        MiniMaxModel off = tinyModel(args());
        assertThrows(IllegalStateException.class, () -> off.forwardAux(Tensor.of(new long[]{1}, 1, 1), new int[]{0}));
    }
}
