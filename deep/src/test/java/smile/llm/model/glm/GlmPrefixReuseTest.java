/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.glm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prefix reuse of the executor: a prompt that repeats the start of an earlier one resumes from a state snapshot and
 * must produce the same logits and greedy continuation as a cold prefill (up to bf16 noise). Opt-in:
 * {@code GLM_FULL=<checkpoint dir>}, {@code GLM_PREFIX_LAYERS} (default 8), {@code GLM_FULL_DEVICES} (default 1).
 */
public class GlmPrefixReuseTest {
    private static final int CHUNK = 512;

    private static float[] logitsOf(Glm m, int[] prompt, boolean expectHit, int expectedStart, int decode, int[] outTokens) {
        int id = m.bind(prompt, prompt.length + decode + 1);
        try (var guard = Tensor.noGradGuard()) {
            int start = m.prefixLen(id);
            if (expectHit) {
                assertEquals(expectedStart, start, "resume position");
            } else {
                assertEquals(0, start, "unexpected prefix hit");
            }
            if (start > 0) {
                m.warmPrefix(id, prompt, start);
            }
            Tensor logits = null;
            for (int from = start; from < prompt.length; from += CHUNK) {
                int to = Math.min(prompt.length, from + CHUNK);
                logits = m.prefillChunk(id, prompt, from, to);
            }
            float[] first = host(logits);
            int pos = prompt.length;
            int tok = argmax(first);
            logits.close();
            for (int i = 0; i < decode; i++) {
                outTokens[i] = tok;
                Tensor l = m.decodeStep(new int[]{id}, new int[]{tok}, new int[]{pos++});
                tok = argmax(host(l));
                l.close();
            }
            return first;
        } finally {
            m.evict(id);
        }
    }

    private static float[] host(Tensor t) {
        return t.to(smile.deep.tensor.Device.CPU(), smile.deep.tensor.ScalarType.Float).floatArray();
    }

    private static int argmax(float[] v) {
        int best = 0;
        for (int i = 1; i < v.length; i++) {
            if (v[i] > v[best]) {
                best = i;
            }
        }
        return best;
    }

    private static double relL2(float[] a, float[] b) {
        double d = 0, n = 0;
        for (int i = 0; i < a.length; i++) {
            d += (a[i] - b[i]) * (a[i] - b[i]);
            n += (double) b[i] * b[i];
        }
        return Math.sqrt(d / n);
    }

    private static int[] tokens(Random r, int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++) {
            t[i] = 1000 + r.nextInt(50000);
        }
        return t;
    }

    @Test
    public void testGivenRepeatedPrefixThenResumeFromSnapshotMatchesColdPrefill() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int layers = Integer.parseInt(System.getenv().getOrDefault("GLM_PREFIX_LAYERS", "8"));
        int devices = Integer.parseInt(System.getenv().getOrDefault("GLM_FULL_DEVICES", "1"));
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        Random rnd = new Random(7);
        int[] a = tokens(rnd, 1500);                                      // snapshot at 1499
        int[] d = Arrays.copyOf(a, 1800);                                 // a + 300 new tokens: resumes at 1499
        System.arraycopy(tokens(rnd, 300), 0, d, 1500, 300);
        int[] big = tokens(rnd, 5000);                                    // snapshots at 4096 and 4999
        int[] e = Arrays.copyOf(big, 4400);                               // big[0..4300) + 100 new: resumes at 4096
        System.arraycopy(tokens(rnd, 100), 0, e, 4300, 100);
        int[] f = Arrays.copyOf(big, 5000);                               // identical to big: resumes at 4999
        int decode = 6;

        // reference: no cache at all
        System.setProperty("smile.glm.prefix.cache.mb", "0");
        float[][] ref = new float[3][];
        int[][] refTok = new int[3][decode];
        try (Glm m = Glm.build(dir, layers, 16384, dev, 2)) {
            assertEquals("disabled", m.prefixCacheStats());
            ref[0] = logitsOf(m, d, false, 0, decode, refTok[0]);
            ref[1] = logitsOf(m, e, false, 0, decode, refTok[1]);
            ref[2] = logitsOf(m, f, false, 0, decode, refTok[2]);
        }

        System.setProperty("smile.glm.prefix.cache.mb", "2048");
        try (Glm m = Glm.build(dir, layers, 16384, dev, 2)) {
            int[] scratch = new int[decode];
            logitsOf(m, a, false, 0, decode, scratch);                    // cold, takes the snapshot
            float[] warmD = logitsOf(m, d, true, 1499, decode, scratch);
            System.out.println("a -> d: rel L2 " + relL2(warmD, ref[0]) + ", tokens " + Arrays.toString(scratch) + " vs " + Arrays.toString(refTok[0]));
            assertTrue(relL2(warmD, ref[0]) < 0.02);
            assertEquals(argmax(ref[0]), argmax(warmD));
            assertArrayEquals(refTok[0], scratch, "greedy continuation after prefix reuse");

            logitsOf(m, big, false, 0, decode, scratch);
            float[] warmE = logitsOf(m, e, true, 4096, decode, scratch);
            System.out.println("big -> e: rel L2 " + relL2(warmE, ref[1]));
            assertTrue(relL2(warmE, ref[1]) < 0.02);
            assertEquals(argmax(ref[1]), argmax(warmE));
            assertArrayEquals(refTok[1], scratch, "greedy continuation after prefix reuse (4096)");

            float[] warmF = logitsOf(m, f, true, 4999, decode, scratch);
            System.out.println("big -> big: rel L2 " + relL2(warmF, ref[2]));
            assertTrue(relL2(warmF, ref[2]) < 0.02);
            // a truncated slice is chaotic: a later near-tie may flip, the first tokens must agree
            assertArrayEquals(Arrays.copyOf(refTok[2], 3), Arrays.copyOf(scratch, 3), "greedy continuation after an identical prompt");

            logitsOf(m, tokens(rnd, 1500), false, 0, 2, new int[2]);    // unrelated prompt: no hit
            System.out.println("prefix cache: " + m.prefixCacheStats());
        }
    }
}
