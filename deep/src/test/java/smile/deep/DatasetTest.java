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

import java.io.IOException;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.deep.metric.Accuracy;
import smile.deep.tensor.Device;
import smile.io.Read;
import smile.util.Paths;
import smile.deep.layer.Layer;

/**
 *
 * @author Haifeng Li
 */
public class DatasetTest {

    public DatasetTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
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
    public void test() throws IOException {
        Model net = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        );

        if (CUDA.isAvailable()) net.to(Device.CUDA());
        CSVFormat format = CSVFormat.Builder.create().setDelimiter(' ').build();
        double[][] x = Read.csv(Paths.getTestData("mnist/mnist2500_X.txt"), format).toArray();
        int[] y = Read.csv(Paths.getTestData("mnist/mnist2500_labels.txt"), format).column(0).toIntArray();

        Dataset dataset = Dataset.of(x, y, 64);
        Optimizer optimizer = Optimizer.SGD(net, 0.01);
        Loss loss = Loss.nll();
        net.train(100, optimizer, loss, dataset, null, null, 38);
        Map<String, Double> metrics = net.eval(dataset, new Accuracy());
        System.out.format("Training Accuracy: %.2f%%\n", 100 * metrics.get("Accuracy"));
    }
}