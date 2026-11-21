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

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;
import smile.torch.Aten;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The vision path against the transformers reference (fixtures from {@code vision_ref.py}: processor outputs and the fp32
 * features of the real {@code Glm5NextVisionModel}): the Java image processor must produce the reference patches, and the
 * Java tower the reference features. Opt-in: {@code GLM_FULL=<checkpoint dir>}, {@code GLM_FIXTURES=<fixture dir>}.
 */
public class GlmVisionTest {
    private static Tensor o(String op, Object... args) {
        return Aten.one(op, args);
    }

    private static double rel(Tensor a, Tensor b) {
        Tensor d = o("aten::sub.Tensor", a.to(ScalarType.Float), b.to(ScalarType.Float));
        return o("aten::linalg_vector_norm", d, 2.0).doubleValue() / o("aten::linalg_vector_norm", b.to(ScalarType.Float), 2.0).doubleValue();
    }

    @Test
    public void testGivenImagesThenProcessorAndTowerMatchTransformers() throws Exception {
        String dir = System.getenv("GLM_FULL");
        String fx = System.getenv("GLM_FIXTURES");
        Assumptions.assumeTrue(dir != null && fx != null && Files.exists(Path.of(fx, "vision_shapes.safetensors")),
                "set GLM_FULL=<checkpoint dir> and GLM_FIXTURES=<fixture dir>");
        boolean cuda = "true".equals(System.getenv("GLM_CUDA"));
        GlmVisionArgs args = GlmVisionArgs.load(Path.of(dir));
        assertNotNull(args);
        GlmVisionProcessor processor = new GlmVisionProcessor(args);
        if (cuda) {
            Gx.setDevice(Device.CUDA((byte) 0));
            Gx.setWeightType(ScalarType.BFloat16);
        } else {
            Gx.setDevice(Device.CPU());
            Gx.setWeightType(ScalarType.Float);
        }
        GlmVisionTower tower = new GlmVisionTower(args, new DeepSeekCheckpoint(Path.of(dir)));
        for (String name : System.getenv().getOrDefault("GLM_VISION_IMAGES", "tiny,shapes,gradient,huge").split(",")) {
            BufferedImage image = ImageIO.read(Path.of(fx, "img_" + name + ".png").toFile());
            Map<String, Tensor> want = SafeTensors.read(Path.of(fx, "vision_" + name + ".safetensors").toString(), Device.CPU()).tensors();
            var patches = processor.image(image);
            int[] grid = patches.grid();
            long[] wantGrid = want.get("grid").to(ScalarType.Int64).longArray();
            assertArrayEquals(new long[]{wantGrid[0], wantGrid[1], wantGrid[2]}, new long[]{grid[0], grid[1], grid[2]}, name + " grid");
            // the reference rows repeat the frame: (C, T, P, P); take the first frame
            Tensor ref = want.get("pixel_values");
            Tensor first = o("aten::reshape", o("aten::select.int", o("aten::reshape", ref, new long[]{ref.size(0), 3, 2, 14, 14}), 2L, 0L),
                    new long[]{ref.size(0), 588});
            double pixelErr = o("aten::max", o("aten::abs", o("aten::sub.Tensor", patches.pixels(), first))).doubleValue();
            System.out.printf("%s: grid %s, patch rows %d, max |pixel difference| %.4f%n", name, java.util.Arrays.toString(grid),
                    patches.pixels().size(0), pixelErr);
            // 0.05 in normalized units = one 8-bit level of the resize rounding (1 / (255 * 0.27) = 0.0145 per level)
            assertTrue(pixelErr < 0.07, name + " processor differs from the reference by " + pixelErr);

            // the tower on the reference's own pixels (isolates the encoder from the processor)
            Tensor feats = tower.forward(first, new int[][]{{grid[0], grid[1], grid[2]}});
            Tensor wantF = want.get("features");
            double e = rel(feats.to(Device.CPU()), wantF);
            System.out.printf("%s: features %s, relative L2 error vs the fp32 reference %.4f%n", name,
                    java.util.Arrays.toString(feats.shape()), e);
            assertEquals(wantF.size(0), feats.size(0), name + " feature rows");
            assertTrue(e < 0.03, name + " vision features differ from the reference: " + e);
        }
    }
}
