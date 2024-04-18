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
package smile.vision;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import org.junit.jupiter.api.*;
import static smile.deep.tensor.Index.Ellipsis;
import static smile.deep.tensor.Index.slice;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class EfficientNetTest {

    public EfficientNetTest() {
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
        Device device = Device.preferredDevice();
        device.setDefaultDevice();

        var model = EfficientNet.V2S();
        model.eval();
        model.to(device);

        var lenna = ImageIO.read(new File("deep/src/universal/data/image/Lenna.png"));
        var panda = ImageIO.read(new File("deep/src/universal/data/image/panda.jpg"));

        try (var guard = Tensor.noGradGuard()) {
            long startTime = System.nanoTime();
            var output = model.forward(panda);
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
            System.out.println("Elapsed time: " + duration + "ms");
            output.get(Ellipsis, slice(0,5)).print();

            var topk = output.topk(5);
            topk._2().to(Device.CPU());
            //topk._1().print();
            topk._2().print();
            System.out.println(ImageNet.labels[topk._2().getInt(0, 0)]);
            System.out.println(ImageNet.labels[topk._2().getInt(0, 1)]);
            System.out.println(ImageNet.labels[topk._2().getInt(0, 2)]);
            System.out.println(ImageNet.labels[topk._2().getInt(0, 3)]);
            System.out.println(ImageNet.labels[topk._2().getInt(0, 4)]);
            assertEquals(388, topk._2().getInt(0, 0));
        }
    }
}