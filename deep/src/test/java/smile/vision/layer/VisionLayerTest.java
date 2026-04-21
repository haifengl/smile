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
package smile.vision.layer;

import org.junit.jupiter.api.*;
import smile.deep.layer.BatchNorm2dLayer;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for vision layer primitives: {@link MBConvConfig}, {@link Conv2dNormActivation},
 * {@link SqueezeExcitation}, {@link StochasticDepth}, {@link MBConv}, and {@link FusedMBConv}.
 * All tests run on CPU with small synthetic tensors — no GPU or dataset needed.
 *
 * @author Haifeng Li
 */
public class VisionLayerTest {

    // -----------------------------------------------------------------------
    // MBConvConfig — static helpers
    // -----------------------------------------------------------------------

    @Test
    public void testGivenMakeDivisibleWhenInputAlreadyDivisibleThenUnchanged() {
        // 8 is divisible by 8 → stays 8
        assertEquals(8, MBConvConfig.makeDivisible(8.0, 8));
    }

    @Test
    public void testGivenMakeDivisibleWhenInputNotDivisibleThenRoundsUp() {
        // 9 → next multiple of 8 is 16 (since 9+4=13 → 13/8*8=8, but 8 < 0.9*9=8.1 so +8=16)
        int result = MBConvConfig.makeDivisible(9.0, 8);
        assertEquals(0, result % 8, "Result must be divisible by 8");
        assertTrue(result >= 9 * 0.9, "Result must not drop more than 10%%");
    }

    @Test
    public void testGivenAdjustChannelsWhenScaleOnePointZeroThenUnchanged() {
        assertEquals(32, MBConvConfig.adjustChannels(32, 1.0));
    }

    @Test
    public void testGivenAdjustChannelsWhenScaleTwoThenDoubled() {
        int result = MBConvConfig.adjustChannels(32, 2.0);
        assertEquals(64, result);
        assertEquals(0, result % 8);
    }

    @Test
    public void testGivenAdjustDepthWhenScaleOnePointZeroThenUnchanged() {
        assertEquals(6, MBConvConfig.adjustDepth(6, 1.0));
    }

    @Test
    public void testGivenAdjustDepthWhenScaleTwoThenDoubled() {
        assertEquals(12, MBConvConfig.adjustDepth(6, 2.0));
    }

    @Test
    public void testGivenMBConvFactoryWhenCreatedThenBlockTypIsMBConv() {
        MBConvConfig cfg = MBConvConfig.MBConv(4, 3, 2, 64, 128, 6);
        assertEquals("MBConv", cfg.block());
        assertEquals(64,  cfg.inputChannels());
        assertEquals(128, cfg.outputChannels());
        assertEquals(6,   cfg.numLayers());
    }

    @Test
    public void testGivenFusedMBConvFactoryWhenCreatedThenBlockTypeIsFusedMBConv() {
        MBConvConfig cfg = MBConvConfig.FusedMBConv(1, 3, 1, 24, 24, 2);
        assertEquals("FusedMBConv", cfg.block());
        assertEquals(24, cfg.inputChannels());
        assertEquals(24, cfg.outputChannels());
    }

    @Test
    public void testGivenMBConvWithWidthMultiplierWhenCreatedThenChannelsAreScaled() {
        MBConvConfig cfg = MBConvConfig.MBConv(4, 3, 2, 32, 64, 3, 2.0, 1.0);
        // 32*2=64, 64*2=128, all divisible by 8
        assertEquals(64,  cfg.inputChannels());
        assertEquals(128, cfg.outputChannels());
        assertEquals(0,   cfg.inputChannels()  % 8);
        assertEquals(0,   cfg.outputChannels() % 8);
    }

    // -----------------------------------------------------------------------
    // StochasticDepth
    // -----------------------------------------------------------------------

    @Test
    public void testGivenStochasticDepthWithProbZeroWhenForwardThenReturnsSameTensor() {
        StochasticDepth sd = new StochasticDepth(0.0, "row");
        Tensor input = Tensor.rand(4, 8);
        Tensor output = sd.forward(input);
        // p=0 → identity, same object
        assertSame(input, output);
        input.close();
    }

    @Test
    public void testGivenStochasticDepthInEvalModeWhenForwardThenReturnsSameTensor() {
        StochasticDepth sd = new StochasticDepth(0.5, "row");
        sd.asTorch().eval(); // eval mode → no dropout
        Tensor input = Tensor.rand(4, 8);
        Tensor output = sd.forward(input);
        assertSame(input, output);
        input.close();
    }

    @Test
    public void testGivenInvalidProbabilityWhenConstructingStochasticDepthThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new StochasticDepth(-0.1, "row"));
        assertThrows(IllegalArgumentException.class, () -> new StochasticDepth(1.1,  "row"));
    }

    @Test
    public void testGivenInvalidModeWhenConstructingStochasticDepthThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new StochasticDepth(0.5, "channel"));
    }

    @Test
    public void testGivenStochasticDepthInTrainModeWhenForwardThenOutputShapeIsPreserved() {
        StochasticDepth sd = new StochasticDepth(0.3, "row");
        sd.asTorch().train(true);
        Tensor input = Tensor.rand(4, 8, 8);
        Tensor output = sd.forward(input);
        assertArrayEquals(input.shape(), output.shape());
        input.close(); output.close();
    }

    // -----------------------------------------------------------------------
    // Conv2dNormActivation
    // -----------------------------------------------------------------------

    @Test
    public void testGivenConv2dNormActivationWhenForwardThenOutputShapeIsCorrect() {
        // 3-channel input → 16 channels, kernel=3, same-style padding derived automatically
        Conv2dNormActivation cna = new Conv2dNormActivation(
                new Conv2dNormActivation.Options(3, 16, 3, BatchNorm2dLayer::new,
                        new smile.deep.activation.ReLU(true)));
        cna.asTorch().train(true);
        Tensor input = Tensor.rand(2, 3, 8, 8);  // [N, C, H, W]
        Tensor output = cna.forward(input);
        assertEquals(2,  output.size(0));
        assertEquals(16, output.size(1));
        input.close(); output.close();
    }

    @Test
    public void testGivenConv2dNormActivationWithNullActivationWhenForwardThenDoesNotThrow() {
        // activation=null means no activation layer is appended
        Conv2dNormActivation cna = new Conv2dNormActivation(
                new Conv2dNormActivation.Options(8, 16, 1,
                        BatchNorm2dLayer::new, null));
        cna.asTorch().train(true);
        Tensor input = Tensor.rand(2, 8, 4, 4);
        assertDoesNotThrow(() -> {
            Tensor out = cna.forward(input);
            out.close();
        });
        input.close();
    }

    // -----------------------------------------------------------------------
    // SqueezeExcitation
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSqueezeExcitationWhenForwardThenOutputShapeMatchesInput() {
        // 8 channels in, squeeze to 2
        SqueezeExcitation se = new SqueezeExcitation(8, 2);
        Tensor input = Tensor.rand(2, 8, 4, 4);
        Tensor output = se.forward(input);
        assertEquals(2, output.size(0));
        assertEquals(8, output.size(1));
        assertEquals(4, output.size(2));
        assertEquals(4, output.size(3));
        output.close();
    }

    // -----------------------------------------------------------------------
    // MBConv (stride=1 with residual path)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenMBConvWithResidualWhenForwardThenOutputShapeIsPreserved() {
        // expandRatio=4, kernel=3, stride=1, in==out → residual path active
        MBConvConfig cfg = MBConvConfig.MBConv(4, 3, 1, 16, 16, 1);
        MBConv block = new MBConv(cfg, 0.0, BatchNorm2dLayer::new);
        block.asTorch().train(true);
        Tensor input = Tensor.rand(2, 16, 8, 8);
        Tensor output = block.forward(input);
        assertEquals(2,  output.size(0));
        assertEquals(16, output.size(1));
        output.close();
    }

    @Test
    public void testGivenMBConvWithStride2WhenForwardThenSpatialDimsHalved() {
        // stride=2 → no residual; spatial dims halved
        MBConvConfig cfg = MBConvConfig.MBConv(4, 3, 2, 16, 32, 1);
        MBConv block = new MBConv(cfg, 0.0, BatchNorm2dLayer::new);
        block.asTorch().train(true);
        Tensor input = Tensor.rand(2, 16, 8, 8);
        Tensor output = block.forward(input);
        assertEquals(2,  output.size(0));
        assertEquals(32, output.size(1));
        assertEquals(4,  output.size(2)); // H/2
        assertEquals(4,  output.size(3)); // W/2
        output.close();
    }

    @Test
    public void testGivenMBConvWithInvalidStrideThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            MBConvConfig cfg = MBConvConfig.MBConv(4, 3, 3, 16, 32, 1);
            new MBConv(cfg, 0.0, BatchNorm2dLayer::new);
        });
    }

    // -----------------------------------------------------------------------
    // FusedMBConv
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFusedMBConvWithResidualWhenForwardThenOutputShapeIsPreserved() {
        // expandRatio=1 → no intermediate expansion; in==out, stride=1 → residual
        MBConvConfig cfg = MBConvConfig.FusedMBConv(1, 3, 1, 24, 24, 2);
        FusedMBConv block = new FusedMBConv(cfg, 0.0, BatchNorm2dLayer::new);
        block.asTorch().train(true);
        Tensor input = Tensor.rand(2, 24, 8, 8);
        Tensor output = block.forward(input);
        assertEquals(2,  output.size(0));
        assertEquals(24, output.size(1));
        output.close();
    }

    @Test
    public void testGivenFusedMBConvWithExpansionWhenForwardThenOutputChannelsMatchConfig() {
        // expandRatio=4, stride=2 → expand+project path; no residual
        MBConvConfig cfg = MBConvConfig.FusedMBConv(4, 3, 2, 24, 48, 4);
        FusedMBConv block = new FusedMBConv(cfg, 0.0, BatchNorm2dLayer::new);
        block.asTorch().train(true);
        Tensor input = Tensor.rand(2, 24, 8, 8);
        Tensor output = block.forward(input);
        assertEquals(2,  output.size(0));
        assertEquals(48, output.size(1));
        output.close();
    }
}

