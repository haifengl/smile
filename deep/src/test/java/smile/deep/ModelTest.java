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
    static String home = System.getProperty("smile.home", "../shell/src/universal/");
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
    public void testModel() {
        Model model = Model.of(
                Layer.relu(784, 64, 0.5),
                Layer.relu(64, 32),
                Layer.logSoftmax(32, 10)
        );
        model.load("net.pt");
    }
}