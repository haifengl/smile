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
public abstract class Model implements Layer {
    /** The neural network. */
    protected Module net;

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
        net.to(device.value, true);
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
