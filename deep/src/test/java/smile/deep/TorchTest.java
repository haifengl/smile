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
package smile.deep;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import smile.deep.layer.Layer;
import smile.deep.layer.SequentialBlock;
import smile.deep.metric.Accuracy;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the deep tensor backend.
 *
 * @author Haifeng Li
 */
public class TorchTest {
    @BeforeAll
    public static void setUpClass() {
        System.out.format("CUDA available: %s\n", CUDA.isAvailable());
        System.out.format("CUDA device count: %d\n", CUDA.deviceCount());
    }

    @Test
    @Tag("integration")
    public void testGivenMnistDatasetWhenModelTrainedThenAccuracyExceedsNinetyPercent() throws Exception {
        if (!Files.exists(Path.of("deep/src/test/resources/data/mnist"))) {
            System.out.println("MNIST dataset not found, skipping Torch training test.");
            return;
        }

        Device device = Device.preferredDevice();

        Model net = new Model(new SequentialBlock(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)),
                input -> input.reshape(input.size(0), 784)
        ).to(device);

        try (Dataset dataset = Dataset.mnist("deep/src/test/resources/data/mnist", true, 64);
             Dataset test = Dataset.mnist("deep/src/test/resources/data/mnist", false, 64)) {
            Optimizer optimizer = Optimizer.SGD(net, 0.01);
            Loss loss = Loss.nll();
            net.train(10, optimizer, loss, dataset);

            double accuracy;
            try (var __ = Tensor.noGradGuard()) {
                Map<String, Double> metrics = net.eval(test, new Accuracy());
                accuracy = metrics.get("Accuracy");
                System.out.format("Training accuracy = %.2f%%\n", 100.0 * accuracy);
            }

            net.save("net.pt");
            Model restored = new Model(new SequentialBlock(
                    Layer.relu(784, 64, 0.5),
                    Layer.relu(64, 32),
                    Layer.logSoftmax(32, 10)),
                    input -> input.reshape(input.size(0), 784)
            );

            restored.load("net.pt").to(device).eval();
            assertEquals(accuracy, restored.eval(test, new Accuracy()).get("Accuracy"), 0.01);
            assertTrue(accuracy > 0.9, "Expected training accuracy to exceed 90% on MNIST");
        }
    }
}