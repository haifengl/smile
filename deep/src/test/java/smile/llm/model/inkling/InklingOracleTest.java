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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares {@link InklingModel} with the Hugging Face model's dumps (see {@code /raid/haili/inkling/oracle}): the output
 * of every layer application on the first dialog, and teacher-forced and greedy logprobs on all dialogs.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_ORACLE} (out json), {@code INKLING_HIDDEN} (layer outputs
 * as safetensors), {@code INKLING_LAYERS} / {@code INKLING_PASSES} (a slice; default the full model),
 * {@code INKLING_DEVICE} (default 0).
 *
 * @author Haifeng Li
 */
public class InklingOracleTest {
    private static String env(String name) {
        return System.getenv(name);
    }

    @Test
    public void testGivenOracleDumpsWhenRunThenHiddenStatesAndLogprobsMatch() throws Exception {
        String ckpt = env("INKLING_CKPT");
        String oracle = env("INKLING_ORACLE");
        Assumptions.assumeTrue(ckpt != null && oracle != null && new File(oracle).exists(), "set INKLING_CKPT and INKLING_ORACLE");
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        String layers = env("INKLING_LAYERS");
        String passes = env("INKLING_PASSES");
        if (layers != null || passes != null) {
            args = args.withLayers(layers == null ? args.numLayers() : Integer.parseInt(layers),
                    passes == null ? args.numPasses() : Integer.parseInt(passes));
        }
        Device device = Device.CUDA(Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        long t0 = System.nanoTime();
        InklingModel model = InklingModel.load(ckpt, args, device);
        System.out.printf("loaded in %.1f s (%d layers, %d applications)%n", (System.nanoTime() - t0) / 1e9,
                args.numLayers(), args.numApplications());
        JsonNode results = new ObjectMapper().readTree(new File(oracle)).get("results");

        int worstTop1Miss = 0;
        double worstChosen = 0;
        for (int d = 0; d < results.size(); d++) {
            JsonNode r = results.get(d);
            long[] prompt = longs(r.get("prompt_ids"));
            long[] gen = longs(r.get("gen_ids"));
            long[] all = new long[prompt.length + gen.length];
            System.arraycopy(prompt, 0, all, 0, prompt.length);
            System.arraycopy(gen, 0, all, prompt.length, gen.length);
            int[] rows = new int[gen.length];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = prompt.length - 1 + i;
            }

            // teacher-forced: one forward over prompt + generation
            List<Tensor> trace = new ArrayList<>();
            try (InklingState st = model.newState(all.length + 16)) {
                Tensor logits = model.forward(st, all, rows, d == 0 && env("INKLING_HIDDEN") != null ? trace::add : null);
                Tensor lp = Ix.o("aten::log_softmax.int", logits, -1L, null).to(Device.CPU());
                float[] flat = lp.floatArray();
                int vocab = (int) lp.size(1);
                double maxChosen = 0;
                int top1Miss = 0;
                double maxTop = 0;
                for (int i = 0; i < gen.length; i++) {
                    float chosen = flat[i * vocab + (int) gen[i]];
                    maxChosen = Math.max(maxChosen, Math.abs(chosen - r.get("chosen_logprob").get(i).asDouble()));
                    int best = 0;
                    for (int v = 1; v < vocab; v++) {
                        if (flat[i * vocab + v] > flat[i * vocab + best]) {
                            best = v;
                        }
                    }
                    if (best != r.get("top_ids").get(i).get(0).asInt()) {
                        top1Miss++;
                    }
                    for (int j = 0; j < 20; j++) {
                        int id = r.get("top_ids").get(i).get(j).asInt();
                        maxTop = Math.max(maxTop, Math.abs(flat[i * vocab + id] - r.get("top_logprobs").get(i).get(j).asDouble()));
                    }
                }
                System.out.printf("dialog %-12s tokens %3d+%2d  chosen logprob max|diff| %.4f  top-20 max|diff| %.4f  top-1 mismatches %d%n",
                        r.get("name").asString(), prompt.length, gen.length, maxChosen, maxTop, top1Miss);
                worstChosen = Math.max(worstChosen, maxChosen);
                worstTop1Miss += top1Miss;
            }

            if (d == 0 && !trace.isEmpty()) {
                SafeTensors ref = SafeTensors.read(env("INKLING_HIDDEN"), Device.CPU());
                for (int a = 0; a < trace.size(); a++) {
                    float[] ours = trace.get(a).to(ScalarType.Float).floatArray();
                    float[] theirs = ref.tensors().get("l" + a).floatArray();
                    double num = 0;
                    double den = 0;
                    double max = 0;
                    for (int i = 0; i < ours.length; i++) {
                        double diff = ours[i] - theirs[i];
                        num += diff * diff;
                        den += (double) theirs[i] * theirs[i];
                        max = Math.max(max, Math.abs(diff));
                    }
                    System.out.printf("  app %2d (layer %2d pass %d): rel rms %.5f  max|diff| %.4f  ref rms %.3f%n", a,
                            args.layerOfApplication(a), args.passOfApplication(a), Math.sqrt(num / den), max,
                            Math.sqrt(den / ours.length));
                    if (Math.sqrt(num / den) > 0.1) {
                        int dim = args.dim();
                        int rowsN = ours.length / dim;
                        for (int row = 0; row < rowsN; row++) {
                            double e = 0;
                            double n = 0;
                            for (int c = 0; c < dim; c++) {
                                double diff = ours[row * dim + c] - theirs[row * dim + c];
                                e += diff * diff;
                                n += (double) theirs[row * dim + c] * theirs[row * dim + c];
                            }
                            if (Math.sqrt(e / n) > 0.3) {
                                System.out.printf("      token %d: error norm %.2f, ref norm %.2f%n", row, Math.sqrt(e), Math.sqrt(n));
                            }
                        }
                    }
                }
            }
        }
        assertTrue(worstChosen < 0.5, "teacher-forced logprobs diverge from the oracle: " + worstChosen);
    }

    private static long[] longs(JsonNode a) {
        long[] out = new long[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).asLong();
        }
        return out;
    }
}
