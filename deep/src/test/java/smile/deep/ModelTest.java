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
package smile.deep;

import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.deep.metric.Accuracy;
import smile.util.Paths;
import smile.deep.layer.Layer;
import smile.deep.tensor.*;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class ModelTest {
    static String mnist = Paths.getTestData("mnist").toString();

    public ModelTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.format("CUDA available: %s\n", CUDA.isAvailable());
        System.out.format("CUDA device count: %d\n", CUDA.deviceCount());

        // try to use MKL when available
        System.setProperty("org.bytedeco.openblas.load", "mkl");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test() {
        Device device = Device.preferredDevice();

        Model net = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        ).to(device);

        Dataset train = Dataset.mnist(mnist, true, 64);
        Dataset test = Dataset.mnist(mnist, false, 64);

        // Instantiate an SGD optimization algorithm to update our Net's parameters.
        Optimizer optimizer = Optimizer.SGD(net, 0.01);
        Loss loss = Loss.nll();
        net.train(100, optimizer, loss, train, test, null);

        // Inference mode
        Map<String, Double> metrics = net.eval(test, new Accuracy());
        System.out.format("Test Accuracy: %.2f%%\n", 100 * metrics.get("Accuracy"));

        // Serialize your model periodically as a checkpoint.
        net.save("mnist.pt");

        // Loads the model from checkpoint.
        Model model = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        );

        model.load("mnist.pt").to(device).eval();
        assertEquals(metrics.get("Accuracy"), model.eval(test, new Accuracy()).get("Accuracy"), 0.01);
    }
}