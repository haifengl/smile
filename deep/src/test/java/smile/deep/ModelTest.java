/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep;

import java.util.Map;
import smile.deep.layer.SequentialBlock;
import smile.deep.metric.Accuracy;
import smile.deep.metric.Averaging;
import smile.deep.metric.Precision;
import smile.deep.metric.Recall;
import smile.deep.layer.Layer;
import smile.deep.tensor.*;
import org.junit.jupiter.api.*;
import smile.io.Paths;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ModelTest {
    public ModelTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        System.out.format("Number of threads: %d\n", Device.getNumThreads());
        System.out.format("CUDA available: %s\n", CUDA.isAvailable());
        System.out.format("CUDA device count: %d\n", CUDA.deviceCount());
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
    public void test() {
        Device device = Device.preferredDevice();

        Model net = new Model(new SequentialBlock(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)),
                input -> input.reshape(input.size(0), 784)
        ).to(device);

        Dataset data = Dataset.mnist("deep/src/test/resources/data/mnist", true, 64);
        Dataset test = Dataset.mnist("deep/src/test/resources/data/mnist", false, 64);

        // Instantiate an SGD optimization algorithm to update our Net's parameters.
        Optimizer optimizer = Optimizer.SGD(net, 0.01);
        Loss loss = Loss.nll();
        net.train(10, optimizer, loss, data, test, null, new Accuracy(),
                new Precision(Averaging.Micro),
                new Precision(Averaging.Macro),
                new Precision(Averaging.Weighted),
                new Recall(Averaging.Micro),
                new Recall(Averaging.Macro),
                new Recall(Averaging.Weighted));

        double accuracy;
        try (var guard = Tensor.noGradGuard()) {
            Map<String, Double> metrics = net.eval(test,
                    new Accuracy(),
                    new Precision(Averaging.Micro),
                    new Precision(Averaging.Macro),
                    new Precision(Averaging.Weighted),
                    new Recall(Averaging.Micro),
                    new Recall(Averaging.Macro),
                    new Recall(Averaging.Weighted));
            for (var entry : metrics.entrySet()) {
                System.out.format("Testing %s = %.2f%%\n", entry.getKey(), 100 * entry.getValue());
            }
            accuracy = metrics.get("Accuracy");
            assertEquals(metrics.get("Accuracy"), metrics.get("Micro-Precision"), 0.001);
            assertEquals(metrics.get("Accuracy"), metrics.get("Micro-Recall"), 0.001);
            assertEquals(metrics.get("Accuracy"), metrics.get("Weighted-Recall"), 0.001);
        }

        // Serialize the model as a checkpoint.
        net.save("mnist.pt");

        // Loads the model from checkpoint.
        Model model = new Model(new SequentialBlock(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)),
                input -> input.reshape(input.size(0), 784)
        );

        model.load("mnist.pt").to(device).eval();
        assertEquals(accuracy, model.eval(test, new Accuracy()).get("Accuracy"), 0.01);
    }
}