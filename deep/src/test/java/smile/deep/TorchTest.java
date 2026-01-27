/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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

import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.Module;
import org.junit.jupiter.api.*;
import static org.bytedeco.pytorch.global.torch.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class TorchTest {
    public TorchTest() {
    }

    // Reference implementation with plain PyTorch binding.
    static class Net extends Module {
        Net() {
            // Construct and register two Linear submodules.
            fc1 = register_module("Layer-1", new LinearImpl(784, 64));
            fc2 = register_module("Layer-2", new LinearImpl(64, 32));
            fc3 = register_module("Layer-3", new LinearImpl(32, 10));
        }

        // Implement the Net's algorithm.
        org.bytedeco.pytorch.Tensor forward(org.bytedeco.pytorch.Tensor x) {
            // Use one of many tensor manipulation functions.
            x = relu(fc1.forward(x.reshape(x.size(0), 784)));
            x = dropout(x, /*p=*/0.5, /*train=*/is_training());
            x = relu(fc2.forward(x));
            x = log_softmax(fc3.forward(x), /*dim=*/1);
            return x;
        }

        // Use one of many "standard library" modules.
        final LinearImpl fc1, fc2, fc3;
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        System.out.format("CUDA available: %s\n", CUDA.isAvailable());
        System.out.format("CUDA device count: %d\n", CUDA.deviceCount());

        // try to use MKL when available
        System.setProperty("org.bytedeco.openblas.load", "mkl");
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
        // Create a new Net.
        Net net = new Net();

        // Create a multithreaded data loader for the MNIST dataset.
        MNISTMapDataset dataset = new MNIST("deep/src/test/resources/data/mnist").map(new ExampleStack());
        MNISTRandomDataLoader dataLoader = new MNISTRandomDataLoader(
                dataset, new RandomSampler(dataset.size().get()),
                new DataLoaderOptions(64));

        // Instantiate an SGD optimization algorithm to update our Net's parameters.
        SGD optimizer = new SGD(net.parameters(), new SGDOptions(0.01));

        for (int epoch = 1; epoch <= 10; ++epoch) {
            int batch_index = 0;
            // Iterate the data loader to yield batches from the dataset.
            for (ExampleIterator it = dataLoader.begin(); !it.equals(dataLoader.end()); it = it.increment()) {
                Example batch = it.access();
                // Reset gradients.
                optimizer.zero_grad();
                // Execute the model on the input data.
                org.bytedeco.pytorch.Tensor prediction = net.forward(batch.data());
                // Compute a loss value to judge the prediction of our model.
                org.bytedeco.pytorch.Tensor loss = nll_loss(prediction, batch.target());
                // Compute gradients of the loss w.r.t. the parameters of our model.
                loss.backward();
                // Update the parameters based on the calculated gradients.
                optimizer.step();
                // Output the loss and checkpoint every 100 batches.
                if (++batch_index % 100 == 0) {
                    System.out.println("Epoch: " + epoch + " | Batch: " + batch_index + " | Loss: " + loss.item_float());
                    // Serialize your model periodically as a checkpoint.
                    OutputArchive archive = new OutputArchive();
                    net.save(archive);
                    archive.save_to("net.pt");
                }
            }
        }

        net.eval();
        int correct = 0;
        int size = 0;
        for (ExampleIterator it = dataLoader.begin(); !it.equals(dataLoader.end()); it = it.increment()) {
            Example batch = it.access();
            var output = net.forward(batch.data());
            var prediction = output.argmax(new LongOptional(1), false);
            correct += prediction.eq(batch.target()).sum().item_int();
            size += batch.target().size(0);
            // Explicitly free native memory
            batch.close();
        }
        double accuracy = (double) correct / size;
        System.out.format("Training accuracy = %.2f%%\n", 100.0 * accuracy);
        assertEquals(true, accuracy > 0.9);
    }
}