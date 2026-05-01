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
package smile.vision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import smile.vision.transform.Transform;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ImageDatasetTest {

    @Test
    @Tag("integration")
    public void test() throws IOException {
        if (!Files.exists(Path.of("deep/src/test/resources/data/imagenet-mini"))) {
            System.out.println("ImageNet-mini dataset not found, skipping ImageDataset test.");
            return;
        }

        var transform = Transform.classification(384, 384);
        try (var data = new ImageDataset(4, "deep/src/test/resources/data/imagenet-mini/train", transform, ImageNet.folder2Target)) {
            assertEquals(34745, data.size());
            var iter = data.iterator();
            assertTrue(iter.hasNext());
            try (var sample = iter.next()) {
                long[] dataShape = {4, 3, 384, 384};
                long[] targetShape = {4};
                assertArrayEquals(dataShape, sample.data().shape());
                assertArrayEquals(targetShape, sample.target().shape());
            }
        }
    }
}
