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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.util.Paths;
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
        Device device = CUDA.isAvailable() ? Device.CUDA() : Device.CPU();
        device.setDefaultDevice();

        Model net = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        ).to(device);

        Dataset trainDataset = Dataset.mnist(mnist, true, 64);
        Dataset testDataset = Dataset.mnist(mnist, false, 64);

        // Instantiate an SGD optimization algorithm to update our Net's parameters.
        Optimizer optimizer = Optimizer.SGD(net, 0.01);
        net.train();
        for (int epoch = 1; epoch <= 10; ++epoch) {
            int batchIndex = 0;
            // Iterate the data loader to yield batches from the dataset.
            for (Sample batch : trainDataset) {
                Tensor data = batch.data.to(device, ScalarType.Float32);
                Tensor target = batch.target.to(device, ScalarType.Int8);

                // Reset gradients.
                optimizer.reset();
                // Execute the model on the input data.
                Tensor prediction = net.forward(data);
                // Compute a loss value to judge the prediction of our model.
                Tensor loss = Loss.nll(prediction, target);
                // Compute gradients of the loss w.r.t. the parameters of our model.
                loss.backward();
                // Update the parameters based on the calculated gradients.
                optimizer.step();

                // Output the loss and checkpoint every 100 batches.
                if (++batchIndex % 100 == 0) {
                    System.out.println("Epoch: " + epoch + " | Batch: " + batchIndex + " | Loss: " + loss.toFloat());
                }
            }
        }

        // Serialize your model periodically as a checkpoint.
        net.save("net.pt");

        // Inference mode
        net.eval();
        double correct = 0;
        for (Sample batch : testDataset) {
            Tensor data = batch.data.to(device, ScalarType.Float32);
            Tensor target = batch.target.to(device, ScalarType.Int8);
            Tensor output = net.forward(data);
            Tensor pred = output.argmax(1, false);  // get the index of the max log - probability
            correct += pred.eq(target).sum().toInt();
        }

        double accuracy = correct / testDataset.size();
        System.out.println("Test Accuracy: " + accuracy);

        // Loads the model from checkpoint.
        Model model = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        );

        model.load("net.pt").to(device).eval();

        correct = 0;
        for (Sample batch : testDataset) {
            Tensor data = batch.data.to(device, ScalarType.Float32);
            Tensor target = batch.target.to(device, ScalarType.Int8);

            Tensor output = model.forward(data);
            Tensor pred = output.argmax(1, false);  // get the index of the max log - probability
            correct += pred.eq(target).sum().toInt();
        }

        double accuracy2 = correct / testDataset.size();
        System.out.println("Checkpoint Accuracy: " + accuracy2);
        assertEquals(accuracy, accuracy2, 0.01);
    }
}