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

import java.io.File;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in, real checkpoint: the logits after a prefix hit (replay up to where prefill resumes, as the engine does, then
 * prefill the rest) must equal those of a cold prefill, for prompts of every page alignment including a prompt that is
 * exactly one page. Environment: {@code QWEN_DIR}, {@code QWEN_REF} (JSON with {@code prompt_ids}).
 *
 * @author Haifeng Li
 */
public class QwenPrefixReuseRealModelTest {
    private static float[] logProbs(Tensor logits) {
        try (logits; Tensor cpu = logits.to(Device.CPU()).to(ScalarType.Float)) {
            float[] x = cpu.floatArray();
            double max = Double.NEGATIVE_INFINITY;
            for (float v : x) {
                max = Math.max(max, v);
            }
            double sum = 0;
            for (float v : x) {
                sum += Math.exp(v - max);
            }
            double lse = max + Math.log(sum);
            float[] out = new float[x.length];
            for (int i = 0; i < x.length; i++) {
                out[i] = (float) (x[i] - lse);
            }
            return out;
        }
    }

    @Test
    public void testGivenPrefixHitWhenContinuedThenLogitsMatchColdPrefill() throws Exception {
        String dir = System.getenv("QWEN_DIR");
        String ref = System.getenv("QWEN_REF");
        assumeTrue(dir != null && ref != null, "QWEN_* not set");
        double fraction = Double.parseDouble(System.getenv().getOrDefault("QWEN_MEM_FRACTION", "0.93"));
        JsonNode cases = new ObjectMapper().readTree(new File(ref));
        try (Qwen qwen = Qwen.build(dir, 4, 2048, (byte) 0, fraction, null)) {
            qwen.setPrefixReplayEnabled(true);
            qwen.setPrefixReuseEnabled(true);
            double worstOverall = 0;
            for (JsonNode c : cases) {
              // full ids, and the same prompt cut to exactly one page (the radix match then covers all of it)
              for (int cut : new int[]{c.get("prompt_ids").size(), 16}) {
                int[] prompt = new int[cut];
                for (int i = 0; i < prompt.length; i++) {
                    prompt[i] = c.get("prompt_ids").get(i).asInt();
                }
                int capacity = prompt.length + 16;

                int cold = qwen.bind(prompt, capacity);
                float[] coldLp = logProbs(qwen.prefillChunk(cold, prompt, 0, prompt.length));
                qwen.finish(cold, prompt);

                int hit = qwen.bind(prompt, capacity);
                int matched = qwen.prefixLen(hit);
                assertTrue(matched > 0, "expected a radix hit for " + c.get("q").asString());
                int from = Math.min(matched, prompt.length - 1);   // what the engine does
                // QWEN_OLD_WARM=1 reproduces the original engine bug (replay the whole match, restart at from)
                qwen.warmPrefix(hit, prompt, System.getenv("QWEN_OLD_WARM") != null ? matched : from);
                float[] warmLp = logProbs(qwen.prefillChunk(hit, prompt, from, prompt.length));
                qwen.evict(hit);

                double worst = 0;
                int argCold = 0;
                int argWarm = 0;
                for (int v = 0; v < coldLp.length; v++) {
                    if (coldLp[v] > coldLp[argCold]) {
                        argCold = v;
                    }
                    if (warmLp[v] > warmLp[argWarm]) {
                        argWarm = v;
                    }
                    if (coldLp[v] > -4.6f) {   // tokens with probability above 1% (logits are bf16: steps of 0.06-0.125)
                        worst = Math.max(worst, Math.abs(coldLp[v] - warmLp[v]));
                    }
                }
                worstOverall = Math.max(worstOverall, worst);
                System.out.println("PREFIX " + String.format("%-44s", "'" + c.get("q").asString().substring(0, Math.min(40,
                        c.get("q").asString().length())) + "'") + " len=" + prompt.length + " matched=" + matched
                        + " from=" + from + " max|dlogp|=" + String.format("%.4f", worst)
                        + (argCold == argWarm ? " same-argmax" : " ARGMAX-DIFFERS"));
                assertEquals(argCold, argWarm, "argmax differs for " + c.get("q").asString());
              }
            }
            assertTrue(worstOverall < 0.3, "warm path differs from cold by " + worstOverall);
        }
    }
}
