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

import org.bytedeco.pytorch.SGD;
import org.bytedeco.pytorch.SGDOptions;

/**
 * Optimizer functions.
 *
 * @author Haifeng Li
 */
public class Optimizer {
    org.bytedeco.pytorch.Optimizer optimizer;

    /** Constructor. */
    Optimizer(org.bytedeco.pytorch.Optimizer optimizer) {
        this.optimizer = optimizer;
    }

    /** Resets gradients. */
    public void reset() {
        optimizer.zero_grad();
    }

    /** Updates the parameters based on the calculated gradients. */
    public void step() {
        optimizer.step();
    }

    public static Optimizer sgd(Model model, float lr) {
        return new Optimizer(new SGD(model.net.parameters(), new SGDOptions(lr)));
    }
}
