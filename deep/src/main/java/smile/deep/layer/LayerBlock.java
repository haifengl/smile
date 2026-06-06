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
package smile.deep.layer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Device;
import smile.torch.Native;
import smile.deep.tensor.ScalarType;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * A block is combinations of one or more layers. Blocks form the basis of
 * more complex network designs. LayerBlock allows treating the whole
 * container as a single layer, such that performing a transformation on
 * the LayerBlock applies to each of the layers it contains (which are each
 * a registered submodule of the block).
 *
 * @author Haifeng Li
 */
public abstract class LayerBlock implements Layer {
    /** The neural network module ({@code ST_Module}). */
    protected final MemorySegment module;
    /** The compute device. */
    protected Device device;
    /** The data type. */
    protected ScalarType dtype;

    /**
     * Constructor.
     */
    public LayerBlock() {
        this(null);
    }

    /**
     * Constructor.
     * @param name the module name.
     */
    public LayerBlock(String name) {
        this.module = createModule(name);
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    private static MemorySegment createModule(String name) {
        if (name == null) {
            return check(smile_module_create(MemorySegment.NULL));
        }
        try (Arena arena = Arena.ofConfined()) {
            return check(smile_module_create(arena.allocateFrom(name)));
        }
    }

    @Override
    public MemorySegment module() {
        return module;
    }

    @Override
    public String toString() {
        return moduleName();
    }

    /**
     * Adds a sub-layer.
     * @param name the name of sub-layer.
     * @param layer the sub-layer.
     * @return this object.
     */
    public LayerBlock add(String name, Layer layer) {
        return add(name, layer.module());
    }

    /**
     * Adds a sub-module.
     * @param name the name of sub-layer.
     * @param layer the native {@code ST_Module} handle of the sub-layer.
     * @return this object.
     */
    public LayerBlock add(String name, MemorySegment layer) {
        try (Arena arena = Arena.ofConfined()) {
            smile_module_register_module(module, arena.allocateFrom(name), layer);
        }
        return this;
    }

    /**
     * Returns true if the layer block is in training mode.
     * @return true if the layer block is in training mode.
     */
    public boolean isTraining() {
        return smile_module_is_training(module) != 0;
    }

    /**
     * Sets the layer block in the training mode.
     */
    public void train() {
        smile_module_train(module, 1);
    }

    /**
     * Sets the layer block in the evaluation/inference mode.
     */
    public void eval() {
        smile_module_eval(module);
    }

    /**
     * Returns the compute device of module.
     * @return the compute device of module.
     */
    public Device device() {
        return device;
    }

    /**
     * Returns the data type of module.
     * @return the data type of module.
     */
    public ScalarType dtype() {
        return dtype;
    }

    @Override
    public LayerBlock to(Device device) {
        MemorySegment d = device.toNative();
        try {
            smile_module_to_device(module, d, 1);
        } finally {
            smile_device_free(d);
        }
        this.device = device;
        return this;
    }

    @Override
    public LayerBlock to(Device device, ScalarType dtype) {
        MemorySegment d = device.toNative();
        try {
            smile_module_to_dtype(module, d, dtype.code(), 1);
        } finally {
            smile_device_free(d);
        }
        this.device = device;
        this.dtype = dtype;
        return this;
    }

    /**
     * Loads a checkpoint.
     * @param path the checkpoint file path.
     */
    public void load(String path) {
        MemorySegment archive = check(smile_input_archive_create());
        MemorySegment d = device != null ? device.toNative() : MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            smile_input_archive_load_from(archive, arena.allocateFrom(path), d);
            smile_module_load(module, archive);
        } finally {
            smile_input_archive_free(archive);
            if (d.address() != 0) smile_device_free(d);
        }
        if (device != null) device.emptyCache();
    }

    /**
     * Serialize the layer block as a checkpoint.
     * @param path the checkpoint file path.
     */
    public void save(String path) {
        MemorySegment archive = check(smile_output_archive_create());
        try (Arena arena = Arena.ofConfined()) {
            smile_module_save(module, archive);
            smile_output_archive_save_to(archive, arena.allocateFrom(path));
        } finally {
            smile_output_archive_free(archive);
        }
    }

    /**
     * Creates a sequential layer block.
     * @param layers the neural network layers.
     * @return the sequential layer block.
     */
    static SequentialBlock sequential(Layer... layers) {
        return new SequentialBlock(layers);
    }
}
