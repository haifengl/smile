/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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

import java.io.IOException;
import java.awt.Image;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static smile.deep.tensor.Index.*;

/**
 *
 * @author Haifeng Li
 */
public class TransformTest {

    public TransformTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() throws IOException {
        var t = Transform.classification(384, 384);
        var img = ImageIO.read(Path.of("deep/src/test/resources/data/image/panda.jpg").toFile());

        // warm up AWT
        var resized = t.resize(img, 384, Image.SCALE_FAST);
        long startTime = System.nanoTime();
        resized = t.resize(img, 384, Image.SCALE_FAST);
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
        System.out.println("Resize time: " + duration + "ms");

        assertEquals(384, resized.getHeight());
        assertEquals(435, resized.getWidth());

        startTime = System.nanoTime();
        var cropped = t.crop(resized, 384, true);
        endTime = System.nanoTime();
        duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
        System.out.println("Crop time: " + duration + "ms");

        assertEquals(384, cropped.getHeight());
        assertEquals(384, cropped.getWidth());

        // warm up PyTorch
        var tensor = t.toTensor(Transform.DEFAULT_MEAN, Transform.DEFAULT_STD, cropped);
        startTime = System.nanoTime();
        tensor = t.toTensor(Transform.DEFAULT_MEAN, Transform.DEFAULT_STD, cropped);
        endTime = System.nanoTime();
        duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
        System.out.println("toTensor time: " + duration + "ms");

        long[] shape = {1, 3, 384, 384};
        assertArrayEquals(shape, tensor.shape());

        tensor.get(Ellipsis, slice(0,5), slice(0,5)).print();
        assertEquals( 0.6906, tensor.getFloat(0, 0, 0, 0), 0.0001);
        assertEquals( 0.1426, tensor.getFloat(0, 0, 0, 1), 0.0001);
        assertEquals( 0.1254, tensor.getFloat(0, 0, 1, 0), 0.0001);
        assertEquals(-0.2342, tensor.getFloat(0, 0, 1, 1), 0.0001);

        assertEquals( 0.6254, tensor.getFloat(0, 1, 0, 0), 0.0001);
        assertEquals( 0.1001, tensor.getFloat(0, 1, 0, 1), 0.0001);
        assertEquals( 0.0126, tensor.getFloat(0, 1, 1, 0), 0.0001);
        assertEquals(-0.3375, tensor.getFloat(0, 1, 1, 1), 0.0001);

        assertEquals( 0.1476, tensor.getFloat(0, 2, 0, 0), 0.0001);
        assertEquals(-0.3055, tensor.getFloat(0, 2, 0, 1), 0.0001);
        assertEquals(-0.4101, tensor.getFloat(0, 2, 1, 0), 0.0001);
        assertEquals(-0.7238, tensor.getFloat(0, 2, 1, 1), 0.0001);
    }
}