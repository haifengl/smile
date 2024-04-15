/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.vision.transform;

import java.io.File;
import java.io.IOException;
import java.awt.Image;
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
        var img = ImageIO.read(new File("deep/src/universal/data/image/Rorschach.jpg"));
        var resized = t.resize(img, 384, Image.SCALE_FAST);
        assertEquals(384, resized.getHeight());
        assertEquals(586, resized.getWidth());

        var cropped = t.crop(img, 384, true);
        assertEquals(384, cropped.getHeight());
        assertEquals(384, cropped.getWidth());

        var tensor = t.forward(cropped);
        tensor.get(Ellipsis, slice(0,5), slice(0,5)).print();
        long[] shape = {1, 3, 384, 384};
        assertArrayEquals(shape, tensor.shape());
    }
}