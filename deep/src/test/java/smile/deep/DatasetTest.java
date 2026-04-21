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
 * Unit tests for the dataset related classes: {@link Dataset},
 * {@link DatasetImpl}, and {@link SampleBatch}.
 *
 * @author Haifeng Li
 */
public class DatasetTest {

    @Test
    @Tag("integration")
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

    // -----------------------------------------------------------------------
    // DatasetImpl — validation
    // -----------------------------------------------------------------------

    @Test
    public void testGivenEmptyFloatIntDataWhenCreatingDatasetThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Dataset.of(new float[0][0], new int[0], 32));
    }

    @Test
    public void testGivenEmptyDoubleIntDataWhenCreatingDatasetThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Dataset.of(new double[0][0], new int[0], 32));
    }

    @Test
    public void testGivenZeroBatchSizeWhenCreatingDatasetThenThrows() {
        float[][] x = {{1f, 2f}, {3f, 4f}};
        int[] y = {0, 1};
        assertThrows(IllegalArgumentException.class, () -> Dataset.of(x, y, 0));
    }

    @Test
    public void testGivenNegativeBatchSizeWhenCreatingDatasetThenThrows() {
        float[][] x = {{1f, 2f}};
        int[] y = {0};
        assertThrows(IllegalArgumentException.class, () -> Dataset.of(x, y, -1));
    }

    // -----------------------------------------------------------------------
    // DatasetImpl — iteration
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFloatIntDatasetWhenIteratedThenSizeAndBatchesCorrect() throws Exception {
        float[][] x = {{1f, 2f}, {3f, 4f}, {5f, 6f}, {7f, 8f}, {9f, 10f}};
        int[] y = {0, 1, 0, 1, 0};
        try (Dataset ds = Dataset.of(x, y, 2)) {
            assertEquals(5, ds.size());
            int count = 0;
            for (SampleBatch batch : ds) {
                assertNotNull(batch.data());
                assertNotNull(batch.target());
                count += (int) batch.data().shape()[0];
                batch.close();
            }
            assertEquals(5, count, "Total samples across all batches must equal dataset size");
        }
    }

    @Test
    public void testGivenFloatFloatDatasetWhenIteratedThenAllSamplesVisited() throws Exception {
        float[][] x = {{1f}, {2f}, {3f}};
        float[] y = {0.1f, 0.2f, 0.3f};
        try (Dataset ds = Dataset.of(x, y, 10)) { // batch > size → single batch
            assertEquals(3, ds.size());
            int count = 0;
            for (SampleBatch batch : ds) {
                count += (int) batch.data().shape()[0];
                batch.close();
            }
            assertEquals(3, count);
        }
    }

    @Test
    public void testGivenDoubleDoubleDatasetWhenIteratedThenAllSamplesVisited() throws Exception {
        double[][] x = {{1.0, 2.0}, {3.0, 4.0}};
        double[] y = {0.5, 1.5};
        try (Dataset ds = Dataset.of(x, y, 1)) {
            assertEquals(2, ds.size());
            int count = 0;
            for (SampleBatch batch : ds) {
                assertEquals(1, batch.data().shape()[0]);
                count++;
                batch.close();
            }
            assertEquals(2, count);
        }
    }

    // -----------------------------------------------------------------------
    // SampleBatch
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSampleBatchWhenAccessedThenDataAndTargetAreCorrect() {
        Tensor data = Tensor.ones(3, 4);
        Tensor target = Tensor.zeros(3);
        SampleBatch batch = new SampleBatch(data, target);
        assertSame(data, batch.data());
        assertSame(target, batch.target());
        batch.close();
    }
}
