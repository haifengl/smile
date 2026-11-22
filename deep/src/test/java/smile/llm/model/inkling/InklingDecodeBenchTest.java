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
package smile.llm.model.inkling;

import java.io.File;
import java.util.Arrays;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.Message;

/**
 * Decode speed through the executor API: prefill {@code INKLING_BENCH_PROMPT} tokens per request, then time greedy
 * single-token steps of {@code B} requests together for each B in {@code INKLING_BENCH_BATCHES}.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_DEVICE}, {@code INKLING_BENCH_BATCHES} (default "1,2,4,8"),
 * {@code INKLING_BENCH_STEPS} (default 32), {@code INKLING_BENCH_PROMPT} (default 300).
 *
 * @author Haifeng Li
 */
public class InklingDecodeBenchTest {
    @Test
    public void testGivenBatchesWhenDecodedThenPrintsTokensPerSecond() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        Assumptions.assumeTrue(ckpt != null && new File(ckpt, "config.json").exists(), "set INKLING_CKPT");
        byte device = Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0"));
        int steps = Integer.parseInt(System.getenv().getOrDefault("INKLING_BENCH_STEPS", "32"));
        int promptLen = Integer.parseInt(System.getenv().getOrDefault("INKLING_BENCH_PROMPT", "300"));
        try (Inkling model = Inkling.build(ckpt, 0, 8192, device)) {
            long[] mem = smile.torch.Native.cudaMemGetInfo(0);
            System.out.printf("GPU memory after load: %.1f GB used of %.1f GB%n", (mem[1] - mem[0]) / 1e9, mem[1] / 1e9);
            model.setPrefixReuseEnabled(false);
            StringBuilder sb = new StringBuilder();
            for (int n = 0; sb.length() < promptLen * 5; n++) {
                sb.append("Item ").append(n).append(": the quick brown fox jumps over the lazy dog ").append(n * 7919 % 1000).append(".\n");
            }
            int[] ids = model.encodeChat(Message.user(sb.toString()));
            int[] prompt = Arrays.copyOf(ids, Math.min(ids.length, promptLen));
            for (String b : System.getenv().getOrDefault("INKLING_BENCH_BATCHES", "1,2,4,8").split(",")) {
                int batch = Integer.parseInt(b.trim());
                int[] req = new int[batch];
                int[] last = new int[batch];
                int[] pos = new int[batch];
                for (int i = 0; i < batch; i++) {
                    req[i] = model.bind(prompt, prompt.length + steps + 8);
                    try (Tensor t = model.prefillChunk(req[i], prompt, 0, prompt.length)) {
                        last[i] = argmax(t);
                    }
                    pos[i] = prompt.length;
                }
                // warm-up (graph capture, allocator), then timed steps
                for (int s = 0; s < 4 + steps; s++) {
                    if (s == 4) {
                        sync(model, req, last, pos);
                    }
                    long t0 = s >= 4 ? System.nanoTime() : 0;
                    try (Tensor logits = model.decodeStep(req, last, pos)) {
                        int[] next = argmaxRows(logits, batch);
                        for (int i = 0; i < batch; i++) {
                            last[i] = next[i];
                            pos[i]++;
                        }
                    }
                    if (s >= 4) {
                        times[s - 4] = System.nanoTime() - t0;
                    }
                }
                long total = 0;
                for (int s = 0; s < steps; s++) {
                    total += times[s];
                }
                double msStep = total / 1e6 / steps;
                System.out.printf("batch %d: %.1f ms/step, %.1f tok/s per request, %.1f tok/s aggregate%n", batch, msStep,
                        1000.0 / msStep, batch * 1000.0 / msStep);
                for (int i = 0; i < batch; i++) {
                    model.evict(req[i]);
                }
            }
        }
    }

    private final long[] times = new long[4096];

    private static void sync(Inkling model, int[] req, int[] last, int[] pos) {
        // decodeStep returns host-side data in argmaxRows, so the device is already idle here
    }

    private static int argmax(Tensor logits) {
        return argmaxRows(logits, 1)[0];
    }

    private static int[] argmaxRows(Tensor logits, int rows) {
        float[] flat = logits.to(Device.CPU()).floatArray();
        int vocab = flat.length / rows;
        int[] out = new int[rows];
        for (int r = 0; r < rows; r++) {
            int best = 0;
            for (int v = 1; v < vocab; v++) {
                if (flat[r * vocab + v] > flat[r * vocab + best]) {
                    best = v;
                }
            }
            out[r] = best;
        }
        return out;
    }
}
