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

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.Module;
import smile.deep.layer.LayerBlock;
import smile.deep.metric.Metric;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.math.TimeFunction;

/**
 * The deep learning models.
 *
 * @author Haifeng Li
 */
public class Model implements Function<Tensor, Tensor> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Model.class);
    /** The neural network. */
    private LayerBlock net;
    /** The compute device on which the model is stored. */
    private Device device;
    /** The learning rate schedule. */
    private TimeFunction learningRateSchedule;

    /**
     * Constructor.
     * @param net the neural network.
     */
    public Model(LayerBlock net) {
        this.net = net;
    }

    @Override
    public String toString() {
        return net.toString();
    }

    /**
     * Returns the PyTorch Module object.
     * @return the PyTorch Module object.
     */
    public Module asTorch() {
        return net.asTorch();
    }

    /**
     * Sets the model in the training mode.
     * @return this model.
     */
    public Model train() {
        net.asTorch().train(true);
        return this;
    }

    /**
     * Sets the model in the evaluation/inference mode.
     * @return this model.
     */
    public Model eval() {
        net.asTorch().eval();
        return this;
    }

    /**
     * Returns the device on which the model is stored.
     * @return the compute device.
     */
    public Device device() {
        return device;
    }

    /**
     * Moves the model to a device.
     * @param device the compute device.
     * @return this model.
     */
    public Model to(Device device) {
        this.device = device;
        net.asTorch().to(device.asTorch(), true);
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
        net.asTorch().load(archive);
        return this;
    }

    /**
     * Serialize the model as a checkpoint.
     * @param path the checkpoint file path.
     * @return this model.
     */
    public Model save(String path) {
        OutputArchive archive = new OutputArchive();
        net.asTorch().save(archive);
        archive.save_to(path);
        return this;
    }

    @Override
    public Tensor apply(Tensor input) {
        return forward(input);
    }

    /**
     * Forward propagation (or forward pass) through the model.
     *
     * @param input the input tensor.
     * @return the output tensor.
     */
    public Tensor forward(Tensor input) {
        if (device != null && !device.equals(input.device())) {
            input = input.to(device);
        }
        return net.forward(input);
    }

    /**
     * Sets the learning rate schedule.
     * @param learningRateSchedule the learning rate schedule.
     */
    public void setLearningRateSchedule(TimeFunction learningRateSchedule) {
        this.learningRateSchedule = learningRateSchedule;
    }

    /**
     * Trains the model.
     * @param epochs the number of training epochs.
     * @param optimizer the optimization algorithm.
     * @param loss the loss function.
     * @param train the training data.
     */
    public void train(int epochs, Optimizer optimizer, Loss loss, Dataset train) {
        train(epochs, optimizer, loss, train, null, null);
    }

    /**
     * Trains the model.
     * @param epochs the number of training epochs.
     * @param optimizer the optimization algorithm.
     * @param loss the loss function.
     * @param train the training data.
     * @param val optional validation data.
     * @param checkpoint optional checkpoint file path.
     * @param metrics the evaluation metrics.
     */
    public void train(int epochs, Optimizer optimizer, Loss loss, Dataset train, Dataset val, String checkpoint, Metric... metrics) {
        train(); // training mode
        int batchIndex = 0;
        for (int epoch = 1; epoch <= epochs; ++epoch) {
            double lossValue = 0;
            // Iterate the data loader to yield batches from the dataset.
            for (SampleBatch batch : train) {
                Tensor data = device == null ? batch.data() : batch.data().to(device);
                Tensor target = device == null ? batch.target() : batch.target().to(device);
                // Reset gradients.
                optimizer.reset();
                // Execute the model on the input data.
                Tensor prediction = net.forward(data);
                // Compute a loss value to judge the prediction of our model.
                Tensor error = loss.apply(prediction, target);
                lossValue = error.floatValue();
                // Compute gradients of the loss w.r.t. the parameters of our model.
                error.backward();
                // Update the parameters based on the calculated gradients.
                optimizer.step();

                // Explicitly free native memory
                data.close();
                target.close();
                batch.close();

                if (learningRateSchedule != null) {
                    double rate = learningRateSchedule.apply(batchIndex);
                    optimizer.setLearningRate(rate);
                }

                if (++batchIndex % 100 == 0) {
                    String msg = String.format("Epoch: %d | Batch: %d | Loss: %.4f", epoch, batchIndex, lossValue);
                    if (learningRateSchedule != null) {
                        double rate = learningRateSchedule.apply(batchIndex);
                        msg += String.format(" | LR: %.4f", rate);
                    }
                    logger.info(msg);
                    free();
                }
            }

            // Output the loss and checkpoint.
            String msg = String.format("Epoch: %d | Batch: %d | Loss: %.4f", epoch, batchIndex, lossValue);
            if (val != null) {
                Map<String, Double> result = eval(val, metrics);
                StringBuilder sb = new StringBuilder(msg);
                train(); // return to training mode
                for (var metric : metrics) {
                    String name = metric.name();
                    sb.append(String.format(" | %s: %.2f%%", name, 100 * result.get(name)));
                    metric.reset();
                }
                msg = sb.toString();
            }

            logger.info(msg);
            if (checkpoint != null) {
                save(String.format("%s-%d.pt", checkpoint, epoch));
            }
            free();
        }
    }

    /** Free up memory and device cache. */
    private void free() {
        System.gc();
        if (device != null) {
            device.emptyCache();
        }
    }

    /**
     * Evaluates the model accuracy on a test dataset.
     * @param dataset the test dataset.
     * @param metrics the evaluation metrics.
     * @return the accuracy.
     */
    public Map<String, Double> eval(Dataset dataset, Metric... metrics) {
        eval(); // evaluation mode
        for (SampleBatch batch : dataset) {
            Tensor data = device == null ? batch.data() : batch.data().to(device);
            Tensor target = device == null ? batch.target() : batch.target().to(device);
            Tensor output = net.forward(data);
            for (var metric : metrics) {
                metric.update(output, target);
            }
            // Explicitly free native memory
            data.close();
            target.close();
            batch.close();
        }

        Map<String, Double> map = new TreeMap<>();
        for (var metric : metrics) {
            map.put(metric.name(), metric.compute());
        }
        return map;
    }
}
