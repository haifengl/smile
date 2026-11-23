package smile.llm.model.inkling;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;

/**
 * Prefill throughput through the executor API (reference forward in chunks).
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_DEVICE}, {@code INKLING_PROMPT} (tokens, default 2000).
 */
public class InklingPrefillBenchTest {
    @Test
    public void testPrefillSpeed() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        Assumptions.assumeTrue(ckpt != null, "set INKLING_CKPT");
        byte device = Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0"));
        int n = Integer.parseInt(System.getenv().getOrDefault("INKLING_PROMPT", "2000"));
        try (Inkling model = Inkling.build(ckpt, 0, 16384, device)) {
            int[] prompt = new int[n];
            java.util.Random r = new java.util.Random(1);
            for (int i = 0; i < n; i++) {
                prompt[i] = 1000 + r.nextInt(100000);
            }
            for (int rep = 0; rep < 3; rep++) {
                int id = model.bind(null, n + 16);
                long t0 = System.nanoTime();
                try (Tensor logits = model.prefill(id, prompt, 0)) {
                    logits.to(smile.deep.tensor.Device.CPU()).floatArray();
                }
                double s = (System.nanoTime() - t0) / 1e9;
                System.out.printf("prefill %d tokens: %.2f s = %.0f tok/s%n", n, s, n / s);
                model.evict(id);
            }
        }
    }
}
