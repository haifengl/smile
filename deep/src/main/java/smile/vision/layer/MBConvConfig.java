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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.vision.layer;

/**
 * EfficientNet block configuration.
 *
 * @param expandRatio the number of output channels of the first layer
 *                   in each block is input channels times expansion ratio.
 * @param kernel the window size.
 * @param stride controls the stride for the cross-correlation.
 * @param inputChannels the number of input channels.
 * @param outputChannels the number of output channels.
 * @param numLayers the number of layers.
 * @param block the block type: "FusedMBConv" or "MBConv".
 */
public record MBConvConfig(double expandRatio,
                           int kernel,
                           int stride,
                           int inputChannels,
                           int outputChannels,
                           int numLayers,
                           String block) {

    /**
     * Returns the config for MBConv block.
     * @param expandRatio the number of output channels of the first layer
     *                   in each block is input channels times expansion ratio.
     * @param kernel the window size.
     * @param stride controls the stride for the cross-correlation.
     * @param inputChannels the number of input channels.
     * @param outputChannels the number of output channels.
     * @param numLayers the number of layers.
     * @return the config for MBConv block.
     */
    public static MBConvConfig MBConv(double expandRatio,
                                      int kernel,
                                      int stride,
                                      int inputChannels,
                                      int outputChannels,
                                      int numLayers) {
        return MBConv(expandRatio, kernel, stride, inputChannels, outputChannels, numLayers, 1.0, 1.0);
    }

    /**
     * Returns the config for MBConv block.
     * @param expandRatio the number of output channels of the first layer
     *                   in each block is input channels times expansion ratio.
     * @param kernel the window size.
     * @param stride controls the stride for the cross-correlation.
     * @param inputChannels the number of input channels.
     * @param outputChannels the number of output channels.
     * @param numLayers the number of layers.
     * @param widthMultiplier the multiplier to scale input/output channels.
     * @param depthMultiplier the multiplier to scale number of layers.
     * @return the config for MBConv block.
     */
    public static MBConvConfig MBConv(double expandRatio,
                                      int kernel,
                                      int stride,
                                      int inputChannels,
                                      int outputChannels,
                                      int numLayers,
                                      double widthMultiplier,
                                      double depthMultiplier) {
        inputChannels = adjustChannels(inputChannels, widthMultiplier);
        outputChannels = adjustChannels(outputChannels, widthMultiplier);
        numLayers = adjustDepth(numLayers, depthMultiplier);
        return new MBConvConfig(expandRatio, kernel, stride, inputChannels, outputChannels, numLayers, "MBConv");
    }

    /**
     * Returns the config for Fused-MBConv block.
     * @param expandRatio the number of output channels of the first layer
     *                   in each block is input channels times expansion ratio.
     * @param kernel the window size.
     * @param stride controls the stride for the cross-correlation.
     * @param inputChannels the number of input channels.
     * @param outputChannels the number of output channels.
     * @param numLayers the number of layers.
     * @return the config for Fused-MBConv block.
     */
    public static MBConvConfig FusedMBConv(double expandRatio,
                                           int kernel,
                                           int stride,
                                           int inputChannels,
                                           int outputChannels,
                                           int numLayers) {
        return new MBConvConfig(expandRatio, kernel, stride, inputChannels, outputChannels, numLayers, "FusedMBConv");
    }

    /**
     * Adjusts the depth.
     * @param x the input value.
     * @param scale the scaling factor.
     * @return the output value.
     */
    static int adjustDepth(int x, double scale) {
        return (int) Math.ceil(x * scale);
    }

    /**
     * The building blocks of EfficientNet demands channel size
     * to be multiples of 8.
     * @param x the input value.
     * @param scale the scaling factor.
     * @return the output value divisible by 8.
     */
    static int adjustChannels(int x, double scale) {
        return makeDivisible(x * scale, 8);
    }

    /**
     * Adjusts the input to be divisible by the specified divisor.
     * @param x the input value.
     * @param divisor the divisor.
     * @return the output value divisible by divisor.
     */
    static int makeDivisible(double x, int divisor) {
        int v = Math.max(divisor, (int) (x + divisor / 2.0) / divisor * divisor);
        // Make sure that round down does not go down by more than 10%.
        if (v < 0.9 * x) {
            v += divisor;
        }
        return v;
    }
}
