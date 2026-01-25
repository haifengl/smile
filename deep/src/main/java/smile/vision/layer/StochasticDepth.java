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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.vision.layer;

import java.util.Arrays;
import org.bytedeco.pytorch.Module;
import smile.deep.layer.Layer;
import smile.deep.tensor.Tensor;

/**
 * Stochastic Depth for randomly dropping residual branches of residual
 * architectures, from "Deep Networks with Stochastic Depth".
 *
 * @author Haifeng Li
 */
public class StochasticDepth implements Layer {
    private final Module module = new Module("StochasticDepth");
    private final double p;
    private final String mode;

    /**
     * Constructor.
     * @param p the number of channels in the input image.
     * @param mode "batch" or "row". "batch" randomly zeroes the entire input,
     *            "row" zeroes randomly selected rows from the batch.
     */
    public StochasticDepth(double p, String mode) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("drop probability has to be between 0 and 1, but got " + p);
        }
        if (!(mode.equalsIgnoreCase("batch") || mode.equalsIgnoreCase("row"))) {
            throw new IllegalArgumentException("mode has to be either 'batch' or 'row', but got " + mode);
        }
        this.p = p;
        this.mode = mode;
    }

    @Override
    public Module asTorch() {
        return module;
    }

    @Override
    public Tensor forward(Tensor input) {
        if (!module.is_training() || p == 0.0) {
            return input;
        }

        double survivalRate = 1.0 - p;
        long[] shape = new long[input.dim()];
        Arrays.fill(shape, 1);
        if ("row".equals(mode)) {
            shape[0] = input.size(0);
        }

        Tensor.Options options = new Tensor.Options()
                .dtype(input.dtype())
                .device(input.device());
        Tensor noise = Tensor.empty(options, shape);
        noise = noise.bernoulli_(survivalRate);
        if (survivalRate > 0.0) {
            noise.div_(survivalRate);
        }
        return input.mul(noise);
    }
}
