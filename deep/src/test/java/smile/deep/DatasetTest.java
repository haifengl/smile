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

import java.io.IOException;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import smile.deep.layer.Layer;
import smile.deep.layer.SequentialBlock;
import smile.deep.metric.Accuracy;
import smile.deep.metric.Averaging;
import smile.deep.metric.Precision;
import smile.deep.metric.Recall;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.io.Read;
import smile.io.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DatasetTest {

    public DatasetTest() {
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
        Model net = new Model(new SequentialBlock(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10))
        );

        System.out.println(net);
        net.to(device);

        CSVFormat format = CSVFormat.Builder.create().setDelimiter(' ').get();
        double[][] x = Read.csv(Paths.getTestData("mnist/mnist2500_X.txt"), format).toArray();
        int[] y = Read.csv(Paths.getTestData("mnist/mnist2500_labels.txt"), format).column(0).toIntArray();
        Dataset dataset = Dataset.of(x, y, 64);

        Optimizer optimizer = Optimizer.SGD(net, 0.01);
        Loss loss = Loss.nll();
        net.train(100, optimizer, loss, dataset);

        try (var guard = Tensor.noGradGuard()) {
            Map<String, Double> metrics = net.eval(dataset,
                    new Accuracy(),
                    new Precision(Averaging.Micro),
                    new Precision(Averaging.Macro),
                    new Precision(Averaging.Weighted),
                    new Recall(Averaging.Micro),
                    new Recall(Averaging.Macro),
                    new Recall(Averaging.Weighted));
            for (var entry : metrics.entrySet()) {
                System.out.format("Training %s = %.2f%%\n", entry.getKey(), 100 * entry.getValue());
            }
            assertEquals(metrics.get("Accuracy"), metrics.get("Micro-Precision"), 0.001);
            assertEquals(metrics.get("Accuracy"), metrics.get("Micro-Recall"), 0.001);
            assertEquals(metrics.get("Accuracy"), metrics.get("Weighted-Recall"), 0.001);
        }
    }
}