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
package smile.vision.transform;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Transform} — resize, crop, and toTensor.
 *
 * @author Haifeng Li
 */
public class TransformTest {

    private static final String PANDA_IMG = "deep/src/test/resources/data/image/panda.jpg";

    // -----------------------------------------------------------------------
    // resize
    // -----------------------------------------------------------------------

    @Test
    public void testGivenLandscapeImageWhenResizingThenShorterSideMeetsTarget() throws IOException {
        // Given: panda.jpg is landscape (width > height)
        BufferedImage img = ImageIO.read(Path.of(PANDA_IMG).toFile());
        Transform t = Transform.classification(384, 384);

        // When
        BufferedImage resized = t.resize(img, 384, Image.SCALE_FAST);

        // Then: shorter side (height) == 384, width >= 384
        assertEquals(384, resized.getHeight());
        assertTrue(resized.getWidth() >= 384);
    }

    @Test
    public void testGivenImageWhenResizingToSmallerSizeThenDimensionsAreCorrect() throws IOException {
        BufferedImage img = ImageIO.read(Path.of(PANDA_IMG).toFile());
        Transform t = Transform.classification(224, 224);

        BufferedImage resized = t.resize(img, 224, Image.SCALE_FAST);

        // Shorter side must be 224
        int shorter = Math.min(resized.getWidth(), resized.getHeight());
        assertEquals(224, shorter);
    }

    // -----------------------------------------------------------------------
    // crop
    // -----------------------------------------------------------------------

    @Test
    public void testGivenImageWhenCroppingThenOutputHasRequestedDimensions() throws IOException {
        BufferedImage img = ImageIO.read(Path.of(PANDA_IMG).toFile());
        Transform t = Transform.classification(384, 384);
        BufferedImage resized = t.resize(img, 384, Image.SCALE_FAST);

        // When: shallow crop
        BufferedImage cropped = t.crop(resized, 384, false);

        assertEquals(384, cropped.getWidth());
        assertEquals(384, cropped.getHeight());
    }

    @Test
    public void testGivenImageWhenDeepCroppingThenOutputIsIndependentOfOriginal() throws IOException {
        BufferedImage img = ImageIO.read(Path.of(PANDA_IMG).toFile());
        Transform t = Transform.classification(384, 384);
        BufferedImage resized = t.resize(img, 384, Image.SCALE_FAST);

        // When: deep copy crop
        BufferedImage cropped = t.crop(resized, 300, true);

        assertEquals(300, cropped.getWidth());
        assertEquals(300, cropped.getHeight());
        // Modifying the crop should not change the source (deep copy check)
        cropped.setRGB(0, 0, Color.RED.getRGB());
        assertNotEquals(Color.RED.getRGB(), resized.getRGB(
                (resized.getWidth() - 300) / 2,
                (resized.getHeight() - 300) / 2));
    }

    // -----------------------------------------------------------------------
    // toTensor (TYPE_3BYTE_BGR fast path)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenBgrImageWhenConvertingToTensorThenOutputShapeIsNCHW() throws IOException {
        BufferedImage img = ImageIO.read(Path.of(PANDA_IMG).toFile());
        Transform t = Transform.classification(384, 384);
        BufferedImage resized = t.resize(img, 384, Image.SCALE_FAST);
        BufferedImage cropped = t.crop(resized, 384, true);

        Tensor tensor = t.toTensor(Transform.DEFAULT_MEAN, Transform.DEFAULT_STD, cropped);

        assertArrayEquals(new long[]{1, 3, 384, 384}, tensor.shape());
        tensor.close();
    }

    @Test
    public void testGivenBgrImageWhenConvertingToTensorThenPixelValuesAreNormalized() throws IOException {
        BufferedImage img = ImageIO.read(Path.of(PANDA_IMG).toFile());
        Transform t = Transform.classification(384, 384);
        BufferedImage resized = t.resize(img, 384, Image.SCALE_FAST);
        BufferedImage cropped = t.crop(resized, 384, true);

        Tensor tensor = t.toTensor(Transform.DEFAULT_MEAN, Transform.DEFAULT_STD, cropped);

        // Spot-check known pixel values (pre-computed from panda.jpg)
        assertEquals( 0.6906f, tensor.getFloat(0, 0, 0, 0), 0.0001f);
        assertEquals( 0.1426f, tensor.getFloat(0, 0, 0, 1), 0.0001f);
        assertEquals( 0.6254f, tensor.getFloat(0, 1, 0, 0), 0.0001f);
        assertEquals( 0.1476f, tensor.getFloat(0, 2, 0, 0), 0.0001f);
        tensor.close();
    }

    @Test
    public void testGivenARGBImageWhenConvertingToTensorThenOutputShapeIsNCHW() {
        // Given: a synthetic ARGB image (TYPE_INT_ARGB) — exercises the generic getRGB path
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                img.setRGB(x, y, new Color(100, 150, 200, 255).getRGB());
            }
        }
        Transform t = Transform.classification(32, 32);
        Tensor tensor = t.toTensor(Transform.DEFAULT_MEAN, Transform.DEFAULT_STD, img);

        assertArrayEquals(new long[]{1, 3, 32, 32}, tensor.shape());
        // After normalization all pixels should be finite
        Tensor cont = tensor.contiguous();
        for (float v : cont.floatArray()) {
            assertTrue(Float.isFinite(v), "Tensor element is not finite: " + v);
        }
        tensor.close(); cont.close();
    }

    @Test
    public void testGivenMultipleImagesWhenConvertingToTensorThenBatchDimensionIsCorrect() throws IOException {
        BufferedImage img1 = ImageIO.read(Path.of(PANDA_IMG).toFile());
        Transform t = Transform.classification(384, 384);
        BufferedImage r1 = t.crop(t.resize(img1, 384, Image.SCALE_FAST), 384, true);
        BufferedImage r2 = t.crop(t.resize(img1, 384, Image.SCALE_FAST), 384, true);

        Tensor tensor = t.toTensor(Transform.DEFAULT_MEAN, Transform.DEFAULT_STD, r1, r2);

        // Batch of 2
        assertArrayEquals(new long[]{2, 3, 384, 384}, tensor.shape());
        tensor.close();
    }

    // -----------------------------------------------------------------------
    // ImageClassification.forward (end-to-end)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenClassificationTransformWhenForwardingThenOutputShapeIsCorrect() throws IOException {
        BufferedImage img = ImageIO.read(Path.of(PANDA_IMG).toFile());
        Transform t = Transform.classification(224, 256);

        try (Tensor tensor = t.forward(img)) {
            assertArrayEquals(new long[]{1, 3, 224, 224}, tensor.shape());
        }
    }
}

