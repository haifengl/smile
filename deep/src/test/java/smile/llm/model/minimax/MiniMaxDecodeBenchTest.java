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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in decode-latency benchmark. Environment: {@code MINIMAX_MODEL_DIR}, {@code MINIMAX_DEVICES},
 * optional {@code MINIMAX_BATCHES} (default {@code 1,8,32}), {@code MINIMAX_CONTEXT} (default 512),
 * {@code MINIMAX_STEPS} (default 20).
 *
 * @author Haifeng Li
 */
public class MiniMaxDecodeBenchTest {
    @Test
    public void bench() throws Exception {
        String dir = System.getenv("MINIMAX_MODEL_DIR");
        String devList = System.getenv("MINIMAX_DEVICES");
        assumeTrue(dir != null && devList != null, "MINIMAX_* not set");
        String[] ds = devList.split(",");
        byte[] devices = new byte[ds.length];
        for (int i = 0; i < ds.length; i++) devices[i] = Byte.parseByte(ds[i].trim());
        int context = Integer.parseInt(System.getenv().getOrDefault("MINIMAX_CONTEXT", "512"));
        int steps = Integer.parseInt(System.getenv().getOrDefault("MINIMAX_STEPS", "20"));
        List<Integer> batches = new ArrayList<>();
        for (String b : System.getenv().getOrDefault("MINIMAX_BATCHES", "1,8,32").split(",")) batches.add(Integer.parseInt(b.trim()));
        int maxBatch = batches.stream().mapToInt(Integer::intValue).max().orElse(1);

        try (MiniMax m = MiniMax.build(dir, maxBatch, context + steps + 8, devices, 0.7, 1)) {
            for (int batch : batches) {
                int[] prompt = new int[context];
                for (int i = 0; i < context; i++) prompt[i] = 10 + (i * 37) % 500;
                int[] ids = new int[batch];
                for (int b = 0; b < batch; b++) {
                    int[] p = prompt.clone();
                    p[0] = 20 + b;   // distinct prompts, no prefix sharing
                    ids[b] = m.bind(p, context + steps + 8);
                    m.prefill(ids[b], p, 0).close();
                }
                int[] tokens = new int[batch];
                java.util.Arrays.fill(tokens, 5);
                long best = Long.MAX_VALUE;
                double total = 0;
                for (int s = 0; s < steps + 3; s++) {
                    int[] positions = new int[batch];
                    java.util.Arrays.fill(positions, context + s);
                    long t0 = System.nanoTime();
                    try (Tensor logits = m.decodeStep(ids, tokens, positions)) {
                        smile.llm.engine.Sampling.sampleGreedyTokenIds(logits);  // forces completion
                    }
                    long dt = System.nanoTime() - t0;
                    if (s >= 3) {
                        total += dt;
                        best = Math.min(best, dt);
                    }
                }
                for (int id : ids) m.evict(id);
                System.out.printf("BENCH batch=%d context=%d ranks=%d: mean %.1f ms/step, best %.1f ms/step (%.1f tok/s)%n",
                        batch, context, devices.length, total / steps / 1e6, best / 1e6, batch * 1e9 / (total / steps));
            }
        }
    }
}
