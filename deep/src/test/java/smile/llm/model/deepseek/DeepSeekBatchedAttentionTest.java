/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */
package smile.llm.model.deepseek;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import smile.llm.Message;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in (real checkpoint, CUDA): decoding several sequences through the pointer-table attention kernels gives the
 * same logits as the per-sequence loop. {@code DEEPSEEK_DIR} is the checkpoint, {@code DEEPSEEK_CUDA} the number of
 * GPUs, {@code DEEPSEEK_LAYERS} how many layers to load (26 covers window-only, ratio 2, ratio 1 and the candidate
 * blocks).
 */
@Tag("cuda")
public class DeepSeekBatchedAttentionTest {
    private static int argmax(float[] a, int off, int n) {
        int b = 0;
        for (int i = 1; i < n; i++) {
            if (a[off + i] > a[off + b]) {
                b = i;
            }
        }
        return b;
    }

    @Test
    public void testGivenSeveralSequencesThenBatchedAttentionMatchesPerSequenceLoop() throws Exception {
        String dir = System.getenv("DEEPSEEK_DIR");
        int gpus = Integer.parseInt(System.getenv().getOrDefault("DEEPSEEK_CUDA", "0"));
        Assumptions.assumeTrue(dir != null && gpus > 0, "DEEPSEEK_DIR / DEEPSEEK_CUDA not set");
        int layers = Integer.parseInt(System.getenv().getOrDefault("DEEPSEEK_LAYERS", "26"));
        int n = 4;
        int steps = 8;
        byte[] dev = new byte[gpus];
        for (int i = 0; i < gpus; i++) {
            dev[i] = (byte) i;
        }
        String[] prompts = {"Write a Python function that reverses a string.", "What is the capital of France? Answer briefly.",
                "Explain how a hash map works in detail, with an example in Java.", "Write a haiku about GPUs."};
        try (DeepSeek m = DeepSeek.build(dir, layers, 4096, dev)) {
            int[] ia = new int[n];
            int[] ib = new int[n];
            int[] last = new int[n];
            int[] pos = new int[n];
            for (int i = 0; i < n; i++) {
                int[] p = m.encodeChat(Message.user(prompts[i]));
                ia[i] = m.bind(p, p.length + steps + 2);
                ib[i] = m.bind(p, p.length + steps + 2);
                float[] la = Dsv.host(m.prefill(ia[i], p, 0)).floatArray();
                m.prefill(ib[i], p, 0);
                last[i] = argmax(la, 0, la.length);
                pos[i] = p.length;
            }
            String saved = Native.dsOff;
            try {
                for (int s = 0; s < steps; s++) {
                    Native.dsOff = "";
                    float[] la = Dsv.host(m.decodeStep(ia, last, pos)).floatArray();
                    Native.dsOff = "batch_attn ";
                    float[] lb = Dsv.host(m.decodeStep(ib, last, pos)).floatArray();
                    int v = la.length / n;
                    for (int i = 0; i < n; i++) {
                        double num = 0;
                        double den = 0;
                        for (int j = 0; j < v; j++) {
                            double d = la[i * v + j] - lb[i * v + j];
                            num += d * d;
                            den += (double) lb[i * v + j] * lb[i * v + j];
                        }
                        assertTrue(Math.sqrt(num / den) < 1e-2, "step " + s + " row " + i + " relL2 " + Math.sqrt(num / den));
                        last[i] = argmax(lb, i * v, v);
                        pos[i]++;
                    }
                }
            } finally {
                Native.dsOff = saved;
            }
        }
    }
}
