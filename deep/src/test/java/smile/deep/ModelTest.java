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

import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.Module;
import static org.bytedeco.pytorch.global.torch.*;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class ModelTest {
    static String home = System.getProperty("smile.home", "shell/src/universal/");
    static String mnist = home + "data/mnist";

    public ModelTest() {
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

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.format("CUDA available: %s\n", cuda_is_available());
        System.out.format("CUDA device count: %d\n", cuda_device_count());

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
    public void testBase() {
        // Create a new Net.
        Net net = new Net();

        // Create a multi-threaded data loader for the MNIST dataset.
        MNISTMapDataset dataset = new MNIST(mnist).map(new ExampleStack());
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
    }

    @Test
    public void testModel() {
        Device device = cuda_is_available() ? DeviceType.CUDA.device() : DeviceType.CPU.device();
        device.setDefaultDevice();

        Model net = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        );
        net.to(device);

        MNISTMapDataset train = new MNIST(mnist, MNIST.Mode.kTrain.value).map(new ExampleStack());
        MNISTRandomDataLoader trainLoader = new MNISTRandomDataLoader(
                train, new RandomSampler(train.size().get()),
                new DataLoaderOptions(64));

        // Instantiate an SGD optimization algorithm to update our Net's parameters.
        Optimizer optimizer = Optimizer.sgd(net, 0.01);
        net.train();
        for (int epoch = 1; epoch <= 10; ++epoch) {
            int batch_index = 0;
            // Iterate the data loader to yield batches from the dataset.
            for (ExampleIterator it = trainLoader.begin(); !it.equals(trainLoader.end()); it = it.increment()) {
                Example batch = it.access();
                Tensor data = Tensor.of(batch.data(), device, ScalarType.Float32);
                Tensor target = Tensor.of(batch.target(), device, ScalarType.Int64);

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
                if (++batch_index % 100 == 0) {
                    System.out.println("Epoch: " + epoch + " | Batch: " + batch_index + " | Loss: " + loss.toFloat());
                }
            }
        }

        // Serialize your model periodically as a checkpoint.
        net.save("net.pt");

        // Inference mode
        net.eval();
        MNISTMapDataset test = new MNIST(mnist, MNIST.Mode.kTest.value).map(new ExampleStack());
        MNISTRandomDataLoader testLoader = new MNISTRandomDataLoader(
                test, new RandomSampler(test.size().get()),
                new DataLoaderOptions(64));
        double correct = 0;
        for (ExampleIterator it = testLoader.begin(); !it.equals(testLoader.end()); it = it.increment()) {
            Example batch = it.access();
            Tensor data = Tensor.of(batch.data(), device, ScalarType.Float32);
            Tensor target = Tensor.of(batch.target(), device, ScalarType.Int64);
            Tensor output = net.forward(data);
            Tensor pred = output.argmax(1, false);  // get the index of the max log - probability
            correct += pred.eq(target).sum().toInt();
        }

        double accuracy = correct / test.size().get();
        System.out.println("Test Accuracy: " + accuracy);

        // Loads the model from checkpoint.
        Model model = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        );

        model.load("net.pt");
        model.to(device);
        model.eval();

        correct = 0;
        for (ExampleIterator it = testLoader.begin(); !it.equals(testLoader.end()); it = it.increment()) {
            Example batch = it.access();
            Tensor data = Tensor.of(batch.data(), device, ScalarType.Float32);
            Tensor target = Tensor.of(batch.target(), device, ScalarType.Int64);

            Tensor output = model.forward(data);
            Tensor pred = output.argmax(1, false);  // get the index of the max log - probability
            correct += pred.eq(target).sum().toInt();
        }

        double accuracy2 = correct / test.size().get();
        System.out.println("Checkpoint Accuracy: " + accuracy2);
        assertEquals(accuracy, accuracy2, 0.01);
    }
}