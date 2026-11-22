package smile.llm.model.inkling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.Message;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fused decode step against the torch reference path: the same states (a snapshot restored twice), one reference
 * decode step and one fast decode step, compared layer by layer and on the next-token distribution.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code SMILE_INKLING_LIB}.
 *
 * @author Haifeng Li
 */
public class InklingDecoderTest {
    private static double relRms(float[] a, float[] b) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            num += d * d;
            den += (double) b[i] * b[i];
        }
        return Math.sqrt(num / den);
    }

    private static double tv(float[] a, float[] b) {
        double ma = Double.NEGATIVE_INFINITY;
        double mb = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            ma = Math.max(ma, a[i]);
            mb = Math.max(mb, b[i]);
        }
        double sa = 0;
        double sb = 0;
        for (int i = 0; i < a.length; i++) {
            sa += Math.exp(a[i] - ma);
            sb += Math.exp(b[i] - mb);
        }
        double tv = 0;
        for (int i = 0; i < a.length; i++) {
            tv += Math.abs(Math.exp(a[i] - ma) / sa - Math.exp(b[i] - mb) / sb);
        }
        return tv / 2;
    }

    private static int argmax(float[] x) {
        int best = 0;
        for (int i = 1; i < x.length; i++) {
            if (x[i] > x[best]) {
                best = i;
            }
        }
        return best;
    }

    @Test
    public void testGivenSameStatesWhenSteppedThenFusedDecodeMatchesReferenceUpToNoise() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        Assumptions.assumeTrue(ckpt != null && InklingDecoder.available(), "set INKLING_CKPT and SMILE_INKLING_LIB");
        byte device = Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0"));
        int batch = Integer.parseInt(System.getenv().getOrDefault("INKLING_DEC_BATCH", "4"));
        int promptLen = Integer.parseInt(System.getenv().getOrDefault("INKLING_DEC_PROMPT", "120"));
        int steps = Integer.parseInt(System.getenv().getOrDefault("INKLING_DEC_STEPS", "6"));
        Device dev = Device.CUDA(device);
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        InklingModel model = InklingModel.load(ckpt, args, dev);
        InklingTokenizer tokenizer = InklingTokenizer.of(ckpt);
        InklingDecoder decoder = new InklingDecoder(model);
        String[] texts = {"Explain in one sentence why the sky is usually blue.", "Write a haiku about autumn leaves falling on a quiet lake.",
                "List three prime numbers greater than fifty and explain how you checked them.", "What is the capital of France?"};
        List<InklingState> ref = new ArrayList<>();
        List<InklingState> ref2 = new ArrayList<>();
        List<InklingState> fast = new ArrayList<>();
        List<InklingState> fast2 = new ArrayList<>();
        long[] last = new long[batch];
        int[] positions = new int[batch];
        int apps = args.numApplications();
        int dim = args.dim();
        int vocab = args.unpaddedVocabSize();
        try {
            for (int i = 0; i < batch; i++) {
                StringBuilder sb = new StringBuilder(texts[i % texts.length]);
                for (int n = 0; sb.length() < promptLen * 4 + i * 37; n++) {
                    sb.append(" Item ").append(n).append(" is noted.");
                }
                long[] ids = Arrays.stream(tokenizer.encodeWithSpecials(new smile.llm.template.InklingChatTemplate()
                        .encode(List.of(new Message(smile.llm.Role.user, sb.toString())), null))).asLongStream().toArray();
                ids = Arrays.copyOf(ids, Math.min(ids.length, promptLen + i * 9));
                InklingState a = model.newState(promptLen + 128);
                Tensor logits = null;
                for (int from = 0; from < ids.length; from += a.maxChunk(args)) {
                    int to = Math.min(ids.length, from + a.maxChunk(args));
                    logits = model.forward(a, Arrays.copyOfRange(ids, from, to), to == ids.length ? null : new int[0], null);
                }
                last[i] = argmax(logits.to(Device.CPU()).floatArray());
                InklingState.Snapshot snap = a.snapshot(args);
                for (List<InklingState> copy : List.of(ref2, fast, fast2)) {
                    InklingState b = model.newState(promptLen + 128);
                    b.restore(snap);
                    copy.add(b);
                }
                snap.close();
                ref.add(a);
                positions[i] = (int) a.length;
            }
            long[] table = new long[apps * batch * 8];
            for (int i = 0; i < batch; i++) {
                long[] t = fast.get(i).table();
                for (int a = 0; a < apps; a++) {
                    System.arraycopy(t, a * 8, table, (a * batch + i) * 8, 8);
                }
            }
            Tensor tbl = Tensor.of(table, table.length).to(dev);
            long[] table2 = new long[apps * batch * 8];
            for (int i = 0; i < batch; i++) {
                long[] t = fast2.get(i).table();
                for (int a = 0; a < apps; a++) {
                    System.arraycopy(t, a * 8, table2, (a * batch + i) * 8, 8);
                }
            }
            Tensor tbl2 = Tensor.of(table2, table2.length).to(dev);
            List<Double> tvFast = new ArrayList<>();
            List<Double> tvBase = new ArrayList<>();
            double[][] rel = new double[apps][steps * batch];
            for (int step = 0; step < steps; step++) {
                float[][] refLogits = new float[batch][];
                float[][] ref2Logits = new float[batch][];
                List<List<float[]>> refTrace = new ArrayList<>();
                boolean stages = step == 0 && System.getenv("INKLING_DEC_STAGES") != null;
                List<java.util.Map<String, float[]>> refStages = new ArrayList<>();
                for (int i = 0; i < batch; i++) {
                    List<float[]> tr = new ArrayList<>();
                    java.util.Map<String, float[]> rs = new java.util.HashMap<>();
                    refStages.add(rs);
                    if (stages) {
                        InklingDebug.hook = (name, t) -> rs.put(name, t.to(Device.CPU()).to(ScalarType.Float).floatArray());
                    }
                    refLogits[i] = model.forward(ref.get(i), new long[]{last[i]}, null, t -> tr.add(t.to(ScalarType.Float).floatArray()))
                            .to(Device.CPU()).floatArray();
                    InklingDebug.hook = null;
                    ref2Logits[i] = model.forward(ref2.get(i), new long[]{last[i]}, null, null).to(Device.CPU()).floatArray();
                    refTrace.add(tr);
                }
                Tensor ids = Tensor.of(last, batch).to(dev);
                Tensor pos = Tensor.of(positions, batch).to(dev);
                List<float[]> fastTrace = new ArrayList<>();
                java.util.Map<String, float[]> fs = new java.util.HashMap<>();
                if (stages) {
                    InklingDebug.hook = (name, t) -> fs.put(name, t.to(Device.CPU()).to(ScalarType.Float).floatArray());
                }
                Tensor logits = decoder.forward(batch, ids, pos, tbl, t -> fastTrace.add(t.to(Device.CPU()).to(ScalarType.Float).floatArray()));
                InklingDebug.hook = null;
                if (stages) {
                    for (int a = 0; a < Math.min(apps, 24); a++) {
                        StringBuilder sb = new StringBuilder(String.format("  stages app %2d:", a));
                        for (String st : new String[]{"qin", "attn", "mlpin", "moe"}) {
                            double[] r = new double[batch];
                            for (int i = 0; i < batch; i++) {
                                float[] ff = fs.get(st + "." + a);
                                int n = ff.length / batch;
                                r[i] = relRms(Arrays.copyOfRange(ff, i * n, (i + 1) * n), refStages.get(i).get(st + "." + a));
                            }
                            Arrays.sort(r);
                            sb.append(String.format("  %s %.4f", st, r[batch / 2]));
                        }
                        System.out.println(sb);
                    }
                }
                float[] all = logits.to(Device.CPU()).floatArray();
                if (step == 0) {
                    List<float[]> again = new ArrayList<>();
                    decoder.forward(batch, ids, pos, tbl2, t -> again.add(t.to(Device.CPU()).to(ScalarType.Float).floatArray()));
                    for (int a = 0; a < apps; a += 3) {
                        System.out.printf("  determinism app %2d: fused run 1 vs run 2 rel rms %.5f%n", a, relRms(again.get(a), fastTrace.get(a)));
                    }
                }
                for (int i = 0; i < batch; i++) {
                    float[] f = Arrays.copyOfRange(all, i * vocab, (i + 1) * vocab);
                    tvFast.add(tv(f, refLogits[i]));
                    tvBase.add(tv(ref2Logits[i], refLogits[i]));
                    for (int a = 0; a < apps; a++) {
                        rel[a][step * batch + i] = relRms(Arrays.copyOfRange(fastTrace.get(a), i * dim, (i + 1) * dim), refTrace.get(i).get(a));
                    }
                    last[i] = argmax(refLogits[i]);
                    positions[i]++;
                }
            }
            Collections.sort(tvFast);
            Collections.sort(tvBase);
            System.out.printf("next-token total variation, fused vs reference: median %.4f, max %.4f; reference vs reference: median %.4f, max %.4f (%d rows)%n",
                    tvFast.get(tvFast.size() / 2), tvFast.getLast(), tvBase.get(tvBase.size() / 2), tvBase.getLast(), tvFast.size());
            double worstMedian = 0;
            for (int a = 0; a < apps; a++) {
                double[] r = rel[a].clone();
                Arrays.sort(r);
                double median = r[r.length / 2];
                worstMedian = Math.max(worstMedian, median);
                if (a >= 6) {
                    System.out.printf("  app %2d (layer %2d pass %d): rel rms fused vs reference: median %.4f, max %.4f%n", a,
                            args.layerOfApplication(a), args.passOfApplication(a), median, r[r.length - 1]);
                }
            }
            assertTrue(tvFast.get(tvFast.size() / 2) < 0.05, "median next-token variation " + tvFast.get(tvFast.size() / 2));
            assertTrue(worstMedian < (promptLen >= 1000 ? 0.6 : 0.3), "median layer output difference " + worstMedian);   // long repetitive prompts are chaotic (cold vs cold differs by 0.47)
        } finally {
            ref.forEach(InklingState::close);
            ref2.forEach(InklingState::close);
            fast.forEach(InklingState::close);
            fast2.forEach(InklingState::close);
        }
    }
}
