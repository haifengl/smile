/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Greedy-decodes the prompts of an oracle dump with the sharded model and writes the result in the same json format, so
 * that the oracle can teacher-force it ({@code oracle_dump_small.py --force}): used where the oracle cannot generate
 * (it needs CPU offload at full depth).
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_TP} (devices), {@code INKLING_PROMPTS} (json with
 * {@code results[].prompt_ids}), {@code INKLING_OUT}, {@code INKLING_TOKENS} (default 24), {@code INKLING_LAYERS}.
 *
 * @author Haifeng Li
 */
public class InklingTpGenDumpTest {
    @Test
    public void testGivenPromptsWhenDecodedThenAJsonForTeacherForcingIsWritten() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String tp = System.getenv("INKLING_TP");
        String prompts = System.getenv("INKLING_PROMPTS");
        String out = System.getenv("INKLING_OUT");
        Assumptions.assumeTrue(ckpt != null && tp != null && prompts != null && out != null,
                "set INKLING_CKPT, INKLING_TP, INKLING_PROMPTS and INKLING_OUT");
        int count = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "24"));
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        if (System.getenv("INKLING_LAYERS") != null) {
            args = args.withLayers(Integer.parseInt(System.getenv("INKLING_LAYERS")), 1);
        }
        String[] ids = tp.split(",");
        byte[] devices = new byte[ids.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(ids[i].trim());
        }
        long t0 = System.nanoTime();
        InklingRanks ranks = InklingRanks.load(ckpt, args, devices);
        System.out.printf("loaded in %.1f s%n", (System.nanoTime() - t0) / 1e9);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode results = mapper.readTree(new File(prompts)).get("results");
        List<Map<String, Object>> dump = new ArrayList<>();
        for (JsonNode r : results) {
            long[] prompt = new long[r.get("prompt_ids").size()];
            for (int i = 0; i < prompt.length; i++) {
                prompt[i] = r.get("prompt_ids").get(i).asLong();
            }
            long[] gen = new long[count];
            InklingState[] st = ranks.newStates(prompt.length + count + 8);
            long start = System.nanoTime();
            try {
                Tensor logits = ranks.forward(st, prompt, null, null);
                for (int i = 0; i < count; i++) {
                    float[] row = logits.to(Device.CPU()).floatArray();
                    int best = 0;
                    for (int v = 1; v < row.length; v++) {
                        if (row[v] > row[best]) {
                            best = v;
                        }
                    }
                    gen[i] = best;
                    if (i + 1 < count) {
                        logits = ranks.forward(st, new long[]{best}, null, null);
                    }
                }
            } finally {
                for (InklingState s : st) {
                    s.close();
                }
            }
            System.out.printf("%s: %d prompt tokens, %d decoded in %.1f s: %s%n", r.get("name").asString(), prompt.length,
                    count, (System.nanoTime() - start) / 1e9, java.util.Arrays.toString(gen));
            List<Long> p = new ArrayList<>();
            for (long x : prompt) {
                p.add(x);
            }
            List<Long> g = new ArrayList<>();
            for (long x : gen) {
                g.add(x);
            }
            dump.add(Map.of("name", r.get("name").asString(), "prompt_ids", p, "gen_ids", g));
        }
        mapper.writeValue(new File(out), Map.of("layers", args.numLayers(), "results", dump));
        assertTrue(new File(out).length() > 0);
    }
}
