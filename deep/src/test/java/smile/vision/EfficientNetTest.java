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
import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;

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
        var enet = new EfficientNet(EfficientNet.V2S, 0.5);
        System.out.println(enet);

        var model = new VisionModel(enet, EfficientNet.V2STransform);
        model.load("deep/src/universal/models/efficientnet_v2_s.pt");
        model.eval();
        model.to(device);

        //var example = Tensor.rand(2, 3, 384, 384);
        //var output = model.forward(example);

        var lenna = ImageIO.read(new File("deep/src/universal/data/image/Lenna.png"));
        var rorschach = ImageIO.read(new File("deep/src/universal/data/image/Rorschach.jpg"));
        var output = model.forward(lenna, rorschach);
    }
}