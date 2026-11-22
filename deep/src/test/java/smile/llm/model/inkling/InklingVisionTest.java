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

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.ChatOptions;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The image path against the Hugging Face model: tiles, vision features, prompt ids and the generated description of three
 * test images ({@code /raid/haili/inkling/vision}: {@code ref.json}, {@code ref.safetensors} from {@code oracle_vision.py}).
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_VISION} (the directory with the images and the reference).
 *
 * @author Haifeng Li
 */
public class InklingVisionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    @Test
    public void testGivenImagesWhenProcessedThenTilesAndFeaturesMatchTheReference() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String dir = System.getenv("INKLING_VISION");
        Assumptions.assumeTrue(ckpt != null && dir != null && new File(dir, "ref.json").exists(), "set INKLING_CKPT and INKLING_VISION");
        InklingVisionArgs va = InklingVisionArgs.load(ckpt);
        InklingImageProcessor processor = new InklingImageProcessor(va);
        Device device = Device.CUDA(Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        InklingVisionTower tower = InklingVisionTower.load(new InklingCheckpoint(ckpt), va, device);
        SafeTensors ref = SafeTensors.read(dir + "/ref.safetensors", Device.CPU());
        for (JsonNode c : MAPPER.readTree(new File(dir, "ref.json"))) {
            String name = c.get("name").asString();
            BufferedImage image = ImageIO.read(new File(dir, name + ".png"));
            InklingImageProcessor.Patches p = processor.process(image);
            float[] pixels = p.pixels().floatArray();
            float[] want = ref.tensors().get(name + ".pixel_values").floatArray();
            assertEquals(want.length, pixels.length, name + " tile count");
            double maxDiff = 0;
            for (int i = 0; i < want.length; i++) {
                maxDiff = Math.max(maxDiff, Math.abs(want[i] - pixels[i]));
            }
            assertEquals(c.get("grid").get(0).get(1).asInt(), p.grid()[1]);
            assertEquals(c.get("grid").get(0).get(2).asInt(), p.grid()[2]);
            Tensor embeds = tower.encode(p.pixels(), new int[][]{p.grid()});
            float[] got = embeds.to(Device.CPU()).to(ScalarType.Float).floatArray();
            double rel = relRms(got, ref.tensors().get(name + ".embeds").floatArray());
            System.out.printf("%s: grid %s, tile max|diff| %.2e, vision features rel rms %.4f%n", name, java.util.Arrays.toString(p.grid()), maxDiff, rel);
            assertTrue(maxDiff < 1e-5, name + " tiles differ by " + maxDiff);
            assertTrue(rel < 0.06, name + " vision features differ by " + rel);
        }
    }

    @Test
    public void testGivenImageDialogsWhenGeneratedThenPromptsMatchAndDescriptionsAgree() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String dir = System.getenv("INKLING_VISION");
        Assumptions.assumeTrue(ckpt != null && dir != null && new File(dir, "ref.json").exists(), "set INKLING_CKPT and INKLING_VISION");
        byte device = Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0"));
        int count = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "120"));
        try (Inkling model = Inkling.build(ckpt, 0, 8192, device)) {
            assertTrue(model.isMultimodal());
            for (JsonNode c : MAPPER.readTree(new File(dir, "ref.json"))) {
                String name = c.get("name").asString();
                Message[] dialog = {new Message(Role.user, List.of(new ImageUrlPart(new File(dir, name + ".png").toURI().toString()),
                        new TextPart(c.get("question").asString())))};
                InklingMultimodal mm = model.processMultimodal(dialog, ChatOptions.NONE);
                int[] want = new int[c.get("prompt_ids").size()];
                for (int i = 0; i < want.length; i++) {
                    want[i] = c.get("prompt_ids").get(i).asInt();
                }
                assertArrayEquals(want, mm.inputIds(), name + " prompt ids");
                int id = model.bind(mm.inputIds(), mm.inputIds().length + count + 8);
                int[] gen = new int[count];
                int n = 0;
                try {
                    Tensor logits = model.prefillMultimodal(id, mm, 0, mm.inputIds().length);
                    int pos = mm.inputIds().length;
                    int next = argmax(logits);
                    logits.close();
                    while (n < count) {
                        gen[n++] = next;
                        if (next == 200006) {
                            break;
                        }
                        try (Tensor l = model.decodeStep(new int[]{id}, new int[]{next}, new int[]{pos++})) {
                            next = argmax(l);
                        }
                    }
                } finally {
                    model.evict(id);
                    mm.close();
                }
                int same = 0;
                while (same < Math.min(n, c.get("gen_ids").size()) && gen[same] == c.get("gen_ids").get(same).asInt()) {
                    same++;
                }
                String text = model.tryDecode(java.util.Arrays.copyOf(gen, n), true);
                System.out.printf("%s: %d prompt tokens, agrees with the reference for %d of %d tokens%n    %s%n", name,
                        want.length, same, n, text.replace('\n', ' '));
                assertTrue(same >= 6, name + " diverges from the reference after " + same + " tokens");
            }
        }
    }

    private static int argmax(Tensor logits) {
        float[] x = logits.to(Device.CPU()).floatArray();
        int best = 0;
        for (int i = 1; i < x.length; i++) {
            if (x[i] > x[best]) {
                best = i;
            }
        }
        return best;
    }
}
