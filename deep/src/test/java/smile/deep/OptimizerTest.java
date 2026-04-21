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
package smile.deep;

import smile.deep.layer.Layer;
import smile.deep.layer.SequentialBlock;
import smile.deep.tensor.Tensor;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Optimizer}.
 *
 * @author Haifeng Li
 */
public class OptimizerTest {
    @Test
    public void testGivenOptimizerWhenSetLearningRateWithZeroThenThrows() {
        Model net = new Model(new SequentialBlock(Layer.linear(2, 2)));
        Optimizer opt = Optimizer.SGD(net, 0.01);
        assertThrows(IllegalArgumentException.class, () -> opt.setLearningRate(0.0));
    }

    @Test
    public void testGivenOptimizerWhenSetLearningRateWithNegativeThenThrows() {
        Model net = new Model(new SequentialBlock(Layer.linear(2, 2)));
        Optimizer opt = Optimizer.SGD(net, 0.01);
        assertThrows(IllegalArgumentException.class, () -> opt.setLearningRate(-0.01));
    }

    @Test
    public void testGivenAdamOptimizerWhenCreatedThenStepDoesNotThrow() {
        Model net = new Model(new SequentialBlock(Layer.linear(2, 2)));
        Optimizer opt = Optimizer.Adam(net, 0.001);
        Tensor input = Tensor.ones(1, 2);
        Tensor target = Tensor.zeros(1, 2);
        net.train();
        opt.reset();
        Tensor output = net.forward(input);
        Tensor loss = Loss.mse().apply(output, target);
        assertDoesNotThrow(() -> {
            loss.backward();
            opt.step();
        });
        loss.close(); output.close(); input.close(); target.close();
    }

    @Test
    public void testGivenAdamWOptimizerWhenCreatedThenStepDoesNotThrow() {
        Model net = new Model(new SequentialBlock(Layer.linear(2, 2)));
        Optimizer opt = Optimizer.AdamW(net, 0.001);
        Tensor input = Tensor.ones(1, 2);
        Tensor target = Tensor.zeros(1, 2);
        net.train();
        opt.reset();
        Tensor output = net.forward(input);
        Tensor loss = Loss.mse().apply(output, target);
        assertDoesNotThrow(() -> {
            loss.backward();
            opt.step();
        });
        loss.close(); output.close(); input.close(); target.close();
    }

    @Test
    public void testGivenRMSpropOptimizerWhenCreatedThenStepDoesNotThrow() {
        Model net = new Model(new SequentialBlock(Layer.linear(2, 2)));
        Optimizer opt = Optimizer.RMSprop(net, 0.001);
        Tensor input = Tensor.ones(1, 2);
        Tensor target = Tensor.zeros(1, 2);
        net.train();
        opt.reset();
        Tensor output = net.forward(input);
        Tensor loss = Loss.mse().apply(output, target);
        assertDoesNotThrow(() -> {
            loss.backward();
            opt.step();
        });
        loss.close(); output.close(); input.close(); target.close();
    }
}
