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
package smile.vision;

import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import smile.deep.Loss;
import smile.deep.Optimizer;
import smile.deep.metric.Accuracy;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import org.junit.jupiter.api.*;
import smile.util.function.TimeFunction;
import smile.vision.transform.Transform;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link EfficientNet}. These tests require pre-trained
 * model weights and a local copy of ImageNet-mini; they are tagged
 * {@code @Tag("integration")} so they can be excluded from fast CI runs:
 * <pre>{@code ./gradlew :deep:test -DexcludeTags=integration}</pre>
 *
 * @author Haifeng Li
 */
@Tag("integration")
public class EfficientNetTest {

    @Test
    public void testGivenEfficientNetV2SWhenForwardOnPandaAndLennaThenTop1MatchesExpected() throws IOException {
        Device device = Device.preferredDevice();

        var model = EfficientNet.V2S();
        model.to(device);
        model.eval();

        var lenna = ImageIO.read(Path.of("deep/src/test/resources/data/image/Lenna.png").toFile());
        var panda = ImageIO.read(Path.of("deep/src/test/resources/data/image/panda.jpg").toFile());

        try (var guard = Tensor.noGradGuard()) {
            // warm-up pass
            var output = model.forward(panda);

            // timed pass with batch of 2
            output = model.forward(lenna, panda);

            var topk = output.topk(5);
            topk._2().to(Device.CPU());
            // Verify top-1 predictions
            assertEquals(515, topk._2().getInt(0, 0), "Lenna top-1 class should be 515");
            assertEquals(388, topk._2().getInt(1, 0), "Panda top-1 class should be 388");
        }
    }

    @Test
    public void testGivenEfficientNetV2SWhenTrainingOneEpochThenNoException() throws IOException {
        var model = EfficientNet.V2S();

        var transform = Transform.classification(384, 384);
        var data = new ImageDataset(64, "deep/src/test/resources/data/imagenet-mini/train", transform, ImageNet.folder2Target);
        var test = new ImageDataset(16, "deep/src/test/resources/data/imagenet-mini/val",   transform, ImageNet.folder2Target);

        var schedule = TimeFunction.piecewise(new int[] { 50000 },
                TimeFunction.linear(0.0001, 50000, 0.01),
                TimeFunction.cosine(0.0001, 50000, 0.01));
        model.setLearningRateSchedule(schedule);
        Optimizer optimizer = Optimizer.RMSprop(model, 0.0001, 0.9, 1E-07, 1E-05, 0.9, false);
        // Should complete without throwing
        assertDoesNotThrow(() -> model.train(1, optimizer, Loss.nll(), data, test, null, new Accuracy()));
    }
}
