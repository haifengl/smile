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
package smile.llm.model.minimax;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the preprocessing against the reference HF processors ({@code proc_ref.py}): same grids and patch values up
 * to the rounding of the uint8 resize. Environment: {@code MINIMAX_PROC_REF} (the reference safetensors file, next to
 * {@code img_*.png} and {@code video/}).
 *
 * @author Haifeng Li
 */
public class MiniMaxVisionProcessorTest {
    private static final MiniMaxVisionArgs ARGS = new MiniMaxVisionArgs(1280, 16, 32, 5120, 14, 2, 2, 3, 1e-5,
            10000.0, 4, 6144, 6144, 200025, 200026);

    @Test
    public void testGivenAspectRatiosWhenSmartResizeThenMatchesReferenceRules() {
        // multiples of 28, area within [3136, 451584]
        assertArrayEquals(new int[]{308, 504}, MiniMaxVisionProcessor.smartResize(300, 500, 28, 3136, 451_584));
        assertArrayEquals(new int[]{56, 56}, MiniMaxVisionProcessor.smartResize(50, 60, 28, 3136, 451_584));
        assertArrayEquals(new int[]{504, 840}, MiniMaxVisionProcessor.smartResize(900, 1500, 28, 3136, 451_584));
        // Python rounds halves to even: 14 / 28 = 0.5 -> 0, so the side stays at the factor
        assertArrayEquals(new int[]{28, 56}, MiniMaxVisionProcessor.smartResize(14, 42, 28, 784, 451_584));
        assertThrows(IllegalArgumentException.class,
                () -> MiniMaxVisionProcessor.smartResize(10, 3000, 28, 3136, 451_584));
    }

    private static void compare(String what, MiniMaxVisionProcessor.Patches got, Tensor want, int[] wantGrid) {
        assertArrayEquals(wantGrid, got.grid(), what + " grid");
        assertArrayEquals(want.shape(), got.pixels().shape(), what + " shape");
        float[] a = got.pixels().floatArray();
        float[] b = want.floatArray();
        double worst = 0;
        long off = 0;
        for (int i = 0; i < a.length; i++) {
            double d = Math.abs(a[i] - b[i]);
            worst = Math.max(worst, d);
            if (d > 1e-4) {
                off++;
            }
        }
        System.out.println("PROC " + what + ": max abs diff " + worst + ", " + off + " / " + a.length
                + " values differ by more than 1e-4");
        // the reference resize is ATen's uint8 kernel, so the match is exact up to float rounding
        assertTrue(worst < 1e-3, what + " differs by " + worst);
    }

    @Test
    public void testGivenReferenceImagesWhenProcessedThenPatchesMatch() throws Exception {
        String ref = System.getenv("MINIMAX_PROC_REF");
        assumeTrue(ref != null, "MINIMAX_PROC_REF not set");
        File dir = new File(ref).getParentFile();
        SafeTensors expected = SafeTensors.read(ref, Device.CPU());
        MiniMaxVisionProcessor processor = new MiniMaxVisionProcessor(ARGS);
        for (String k : List.of("a", "b", "c", "d")) {
            BufferedImage image = ImageIO.read(new File(dir, "img_" + k + ".png"));
            int[] grid;
            try (Tensor g = expected.tensors().get("img_" + k + "_grid")) {
                grid = g.intArray();
            }
            var got = processor.image(image);
            compare("image " + k, got, expected.tensors().get("img_" + k), grid);
            got.pixels().close();
        }
    }

    @Test
    public void testGivenReferenceVideoWhenProcessedThenPatchesMatch() throws Exception {
        String ref = System.getenv("MINIMAX_PROC_REF");
        assumeTrue(ref != null, "MINIMAX_PROC_REF not set");
        File dir = new File(new File(ref).getParentFile(), "video");
        SafeTensors expected = SafeTensors.read(ref, Device.CPU());
        File[] files = dir.listFiles((d, n) -> n.endsWith(".png"));
        Arrays.sort(files);
        List<BufferedImage> frames = new ArrayList<>();
        for (File f : files) {
            frames.add(ImageIO.read(f));
        }
        var got = new MiniMaxVisionProcessor(ARGS).video(frames);
        int[] grid;
        try (Tensor g = expected.tensors().get("video_grid")) {
            grid = g.intArray();
        }
        compare("video", got, expected.tensors().get("video"), grid);
    }
}
