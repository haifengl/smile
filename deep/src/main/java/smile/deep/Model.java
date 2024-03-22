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

import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.Module;
import smile.deep.layer.Layer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;

/**
 * The abstract base class of deep learning models.
 *
 * @author Haifeng Li
 */
public abstract class Model implements Layer {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Model.class);
    /** The neural network. */
    protected Module net;
    /** The compute device on which the model is stored. */
    private Device device;

    /**
     * Constructor.
     * @param net the neural network module.
     */
    protected Model(Module net) {
        this.net = net;
    }

    @Override
    public Module asTorch() {
        return net;
    }

    @Override
    public void register(String name, Layer parent) {
        parent.asTorch().register_module(name, net);
    }

    /**
     * Sets the model in the training mode.
     * @return this model.
     */
    public Model train() {
        net.train(true);
        return this;
    }

    /**
     * Sets the model in the evaluation/inference mode.
     * @return this model.
     */
    public Model eval() {
        net.eval();
        return this;
    }

    /**
     * Moves the model to a device.
     * @param device the compute device.
     * @return this model.
     */
    public Model to(Device device) {
        this.device = device;
        net.to(device.asTorch(), true);
        return this;
    }

    /**
     * Loads a checkpoint.
     * @param path the checkpoint file path.
     * @return this model.
     */
    public Model load(String path) {
        InputArchive archive = new InputArchive();
        archive.load_from(path);
        net.load(archive);
        return this;
    }

    /**
     * Serialize the model as a checkpoint.
     * @param path the checkpoint file path.
     * @return this model.
     */
    public Model save(String path) {
        OutputArchive archive = new OutputArchive();
        net.save(archive);
        archive.save_to(path);
        return this;
    }

    /**
     * Trains the model.
     * @param epochs the number of training epochs.
     * @param optimizer the optimization algorithm.
     * @param loss the loss function.
     * @param train the training data.
     */
    public void train(int epochs, Optimizer optimizer, Loss loss, Dataset train) {
        train(epochs, optimizer, loss, train, null, null, 100);
    }

    /**
     * Trains the model.
     * @param epochs the number of training epochs.
     * @param optimizer the optimization algorithm.
     * @param loss the loss function.
     * @param train the training data.
     * @param eval optional evaluation data.
     * @param checkpoint optional checkpoint file path.
     * @param batches run evaluation and save checkpoint per this number of batches.
     */
    public void train(int epochs, Optimizer optimizer, Loss loss, Dataset train, Dataset eval, String checkpoint, int batches) {
        train(); // training mode
        for (int epoch = 1; epoch <= epochs; ++epoch) {
            int batchIndex = 0;
            // Iterate the data loader to yield batches from the dataset.
            for (Sample batch : train) {
                Tensor data = device == null ? batch.data : batch.data.to(device);
                Tensor target = device == null ? batch.target : batch.target.to(device);
                // Reset gradients.
                optimizer.reset();
                // Execute the model on the input data.
                Tensor prediction = forward(data);
                // Compute a loss value to judge the prediction of our model.
                Tensor error = loss.apply(prediction, target);
                // Compute gradients of the loss w.r.t. the parameters of our model.
                error.backward();
                // Update the parameters based on the calculated gradients.
                optimizer.step();

                // Output the loss and checkpoint every 20 batches.
                if (++batchIndex % batches == 0) {
                    if (eval != null) {
                        double accuracy = accuracy(eval);
                        logger.info("Epoch: {} | Batch: {} | Loss: {} | Eval: {}", epoch, batchIndex, error.toFloat(), accuracy);
                    } else {
                        logger.info("Epoch: {} | Batch: {} | Loss: {}", epoch, batchIndex, error.toFloat());
                    }

                    if (checkpoint != null) {
                        save(checkpoint);
                    }
                }
            }
        }
    }

    /**
     * Evaluates the model accuracy on a test dataset.
     * @param dataset the test dataset.
     * @return the accuracy.
     */
    public double accuracy(Dataset dataset) {
        eval(); // evaluation mode
        double correct = 0;
        for (Sample batch : dataset) {
            Tensor data = device == null ? batch.data : batch.data.to(device);
            Tensor target = device == null ? batch.target : batch.target.to(device);
            Tensor output = forward(data);
            Tensor pred = output.argmax(1, false);  // get the index of the max log - probability
            correct += pred.eq(target).sum().toInt();
        }

        return correct / dataset.size();
    }

    /**
     * Creates a model.
     * @param layers the neural network layers.
     * @return the model.
     */
    public static Model of(Layer... layers) {
        int depth = layers.length;
        Module net = new Module();

        return new Model(net) {
            // instance initializer
            {
                for (int i = 0; i < depth; i++) {
                    layers[i].register("Layer-" + (i+1), this);
                }
            }

            @Override
            public Tensor forward(Tensor x) {
                for (Layer layer : layers) {
                    x = layer.forward(x);
                }
                return x;
            }
        };
    }
}
