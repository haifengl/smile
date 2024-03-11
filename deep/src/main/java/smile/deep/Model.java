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

/**
 * The abstract base class of deep learning models.
 *
 * @author Haifeng Li
 */
public abstract class Model {
    /** The neural network. */
    Module net;

    /**
     * Constructor.
     * @param net the neural network module.
     */
    private Model(Module net) {
        this.net = net;
    }

    /**
     * Forward propagation (or forward pass) refers to the calculation
     * and storage of intermediate variables (including outputs) for
     * a neural network in order from the input layer to the output layer.
     *
     * @param x the input tensor.
     * @return the output tensor.
     */
    public abstract Tensor forward(Tensor x);

    /** Sets the model in the training mode. */
    public void train() {
        net.train(true);
    }

    /** Sets the model in the evaluation/inference mode. */
    public void eval() {
        net.eval();
    }

    /**
     * Moves the model to a device.
     * @param device the compute device.
     */
    public void to(Device device) {
        net.to(device.value);
    }

    /**
     * Loads a checkpoint.
     * @param path the checkpoint file path.
     */
    public void load(String path) {
        InputArchive archive = new InputArchive();
        archive.load_from(path);
        net.load(archive);
    }

    /**
     * Serialize the model as a checkpoint.
     * @param path the checkpoint file path.
     */
    public void save(String path) {
        OutputArchive archive = new OutputArchive();
        net.save(archive);
        archive.save_to(path);
    }

    /**
     * Creates a model.
     * @param layers the neural network layers.
     * @return the model.
     */
    public static Model of(Layer... layers) {
        int depth = layers.length;
        Module net = new Module();

        for (int i = 0; i < depth; i++) {
            layers[i].register("Layer-" + (i+1), net);
        }

        return new Model(net) {
            @Override
            public Tensor forward(Tensor x) {
                org.bytedeco.pytorch.Tensor tensor = x.value;
                for (Layer layer : layers) {
                    tensor = layer.forward(tensor);
                }
                return new Tensor(tensor);
            }
        };
    }
}
