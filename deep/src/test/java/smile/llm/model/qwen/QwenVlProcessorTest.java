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

import java.awt.image.BufferedImage;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.TextPart;
import smile.util.Bytes;

/**
 * Lightweight processor tests (no full 27B checkpoint).
 *
 * @author Haifeng Li
 */
public class QwenVlProcessorTest {

    @Test
    public void testGivenSmartResizeWhenAppliedThenDivisibleByFactor() {
        QwenVisionArgs args = new QwenVisionArgs();
        Tokenizer tok = new Tokenizer(Map.of());
        QwenVlProcessor p = new QwenVlProcessor(args, tok, 16, 2, 2,
                4 * 28 * 28, 16384 * 28 * 28,
                new double[]{0.5, 0.5, 0.5}, new double[]{0.5, 0.5, 0.5},
                2.0, 16384 * 28 * 28);
        int[] size = p.smartResize(640, 480, 4 * 28 * 28, 16384 * 28 * 28);
        int factor = 16 * 2;
        assertEquals(0, size[0] % factor);
        assertEquals(0, size[1] % factor);
        assertTrue(size[0] > 0 && size[1] > 0);
    }

    @Test
    public void testGivenSyntheticImageWhenPackedThenGridConsistent() throws Exception {
        QwenVisionArgs args = new QwenVisionArgs(
                2, 64, 128, 4, 3, 16, 2, 2, 64, 256,
                new int[0], 56, 57, 53, 54, true, new int[]{1, 1, 0});
        // Build minimal specials map for pads
        Map<Bytes, Integer> ranks = new java.util.HashMap<>();
        String[] specials = {
                "<|im_start|>", "<|im_end|>", "<|endoftext|>",
                "<|vision_start|>", "<|vision_end|>", "<|image_pad|>", "<|video_pad|>"
        };
        int id = 0;
        for (String s : specials) {
            ranks.put(new Bytes(s.getBytes()), id++);
        }
        // Add a few byte tokens so encode("user\n") works
        for (int b = 0; b < 256; b++) {
            ranks.putIfAbsent(new Bytes(new byte[]{(byte) b}), id++);
        }
        Tokenizer tok = new Tokenizer(ranks);
        QwenVlProcessor p = new QwenVlProcessor(args, tok, 16, 2, 2,
                256, 1024 * 1024,
                new double[]{0.5, 0.5, 0.5}, new double[]{0.5, 0.5, 0.5},
                2.0, 1024 * 1024);

        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        java.io.File tmp = java.io.File.createTempFile("smile-vl-", ".png");
        tmp.deleteOnExit();
        javax.imageio.ImageIO.write(img, "png", tmp);

        var mm = p.process(Message.user(
                new ImageUrlPart(tmp.getAbsolutePath()),
                new TextPart("describe")));
        assertTrue(mm.hasVision());
        assertTrue(mm.inputIds().length > 3);
        assertEquals(1, mm.imageGridThw().length);
        assertEquals(mm.imageGridThw()[0][0] * mm.imageGridThw()[0][1] * mm.imageGridThw()[0][2] / 4,
                countPads(mm.inputIds(), tok.specialToken("<|image_pad|>")));
        mm.pixelValues().close();
    }

    private static int countPads(int[] ids, int padId) {
        int n = 0;
        for (int id : ids) {
            if (id == padId) n++;
        }
        return n;
    }
}
