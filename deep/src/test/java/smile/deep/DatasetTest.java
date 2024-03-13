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
import org.apache.commons.csv.CSVFormat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.io.Read;
import smile.util.Paths;

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
    public void testDataset() throws IOException {
        Model net = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        );

        CSVFormat format = CSVFormat.Builder.create().setDelimiter(' ').build();
        double[][] x = Read.csv(Paths.getTestData("mnist/mnist2500_X.txt"), format).toArray();
        int[] y = Read.csv(Paths.getTestData("mnist/mnist2500_labels.txt"), format).column(0).toIntArray();

        Dataset dataset = Dataset.of(x, y, 64);
        Optimizer optimizer = Optimizer.SGD(net, 0.01);
        net.train();
        for (int epoch = 1; epoch <= 100; ++epoch) {
            int batchIndex = 0;
            // Iterate the data loader to yield batches from the dataset.
            for (Sample batch : dataset) {
                // Reset gradients.
                optimizer.reset();
                // Execute the model on the input data.
                Tensor prediction = net.forward(batch.data);
                // Compute a loss value to judge the prediction of our model.
                Tensor loss = Loss.nll(prediction, batch.target);
                // Compute gradients of the loss w.r.t. the parameters of our model.
                loss.backward();
                // Update the parameters based on the calculated gradients.
                optimizer.step();

                // Output the loss and checkpoint every 20 batches.
                if (++batchIndex % 20 == 0) {
                    System.out.println("Epoch: " + epoch + " | Batch: " + batchIndex + " | Loss: " + loss.toFloat());
                }
            }
        }

        // Inference mode
        net.eval();
        double correct = 0;
        for (Sample batch : dataset) {
            Tensor output = net.forward(batch.data);
            Tensor pred = output.argmax(1, false);  // get the index of the max log - probability
            correct += pred.eq(batch.target).sum().toInt();
        }

        double accuracy = correct / dataset.size();
        System.out.println("Training Accuracy: " + accuracy);
    }
}