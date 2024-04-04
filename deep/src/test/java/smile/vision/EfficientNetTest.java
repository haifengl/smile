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

import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import smile.deep.tensor.Device;
import smile.util.CacheFiles;

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
    public void test() throws IOException, URISyntaxException {
        Device device = Device.preferredDevice();
        device.setDefaultDevice();
        var enet = new EfficientNet(EfficientNet.V2S, 0.5);
        enet.load(CacheFiles.download("https://download.pytorch.org/models/efficientnet_v2_s-dd5fe13b.pth").toString());
    }
}