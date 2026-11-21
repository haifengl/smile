/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
package smile.llm.model.glm;

import java.awt.image.BufferedImage;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

/**
 * The GLM-5.3-Flash image processor (a port of {@code Glm5NextImageProcessor}): the image is resized (bicubic, antialiased,
 * never enlarged beyond its size once it is big enough) into a canvas whose sides are multiples of
 * {@code patch * merge}, padded with black to the canvas, normalized, and cut into patches ordered by (merge block
 * row, merge block column, row in block, column in block). A patch row holds the {@code C * P * P} pixel values of
 * one patch: the reference repeats the frame over the temporal patch of 2, which the patch embedding sums, so the
 * repetition is dropped here.
 *
 * @author Haifeng Li
 */
public final class GlmVisionProcessor {
    private static final long MAX_SOURCE_PIXELS = 100_000_000L;
    private static final int MAX_RATIO = 200;

    /** Patch rows {@code [N, C * P * P]} (float32, CPU) and the {@code (1, gridH, gridW)} grid in patches. */
    public record Patches(Tensor pixels, int[] grid) { }

    private final GlmVisionArgs args;

    /**
     * @param args the vision arguments.
     */
    public GlmVisionProcessor(GlmVisionArgs args) {
        this.args = args;
    }

    private static int align(double value, int factor) {
        return (int) Math.ceil(value / factor) * factor;
    }

    /**
     * The canvas of an image: {@code smart_resize} of the reference for one frame.
     *
     * @return {@code {height, width}} in pixels.
     */
    int[] canvas(int height, int width) {
        int temporal = args.temporalPatchSize();
        int factor = args.patchSize() * args.mergeSize();
        long perToken = (long) temporal * factor * factor;
        long minPixels = args.minImageTokens() * perToken;
        long maxPixels = args.maxImageTokens() * perToken;
        int frames = temporal;                                       // num_frames = temporal_factor for an image
        int alignedFrames = Math.max(temporal, (int) Math.rint((double) frames / temporal) * temporal);
        int ah = align(height, factor);
        int aw = align(width, factor);
        long budget = (long) alignedFrames * ah * aw;
        if (budget < minPixels) {
            double scale = Math.sqrt((double) minPixels / ((double) frames * height * width));
            ah = align(Math.max(1, Math.ceil(height * scale)), factor);
            aw = align(Math.max(1, Math.ceil(width * scale)), factor);
            budget = (long) alignedFrames * ah * aw;
        }
        if (budget > maxPixels) {
            if (maxPixels < (long) alignedFrames * factor * factor) {
                throw new IllegalArgumentException("max_image_tokens is too small for one patch");
            }
            int low = 1;
            int high = height;
            int bestH = factor;
            int bestW = factor;
            while (low <= high) {
                int contentH = (low + high) / 2;
                int contentW = Math.max(1, (int) Math.floor((double) width * contentH / height));
                int candH = align(contentH, factor);
                int candW = align(contentW, factor);
                if ((long) alignedFrames * candH * candW <= maxPixels) {
                    bestH = candH;
                    bestW = candW;
                    low = contentH + 1;
                } else {
                    high = contentH - 1;
                }
            }
            ah = bestH;
            aw = bestW;
        }
        return new int[]{ah, aw};
    }

    /**
     * Preprocesses one image.
     *
     * @param image the decoded image.
     * @return its patch rows and grid.
     */
    public Patches image(BufferedImage image) {
        int h = image.getHeight();
        int w = image.getWidth();
        if (h <= 0 || w <= 0 || (long) h * w > MAX_SOURCE_PIXELS) {
            throw new IllegalArgumentException("unusable image size " + w + "x" + h);
        }
        double ratio = (double) Math.max(h, w) / Math.min(h, w);
        if (ratio > MAX_RATIO) {
            throw new IllegalArgumentException("absolute aspect ratio must be smaller than " + MAX_RATIO + ", got " + ratio);
        }
        int[] target = canvas(h, w);
        int th = target[0];
        int tw = target[1];
        long perToken = (long) args.temporalPatchSize() * args.patchSize() * args.mergeSize() * args.patchSize() * args.mergeSize();
        double scale = Math.min((double) th / h, (double) tw / w);
        if ((long) args.temporalPatchSize() * h * w >= perToken * args.minImageTokens()) {
            scale = Math.min(1.0, scale);
        }
        int ch = Math.max(1, Math.min(th, (int) Math.floor(h * scale)));
        int cw = Math.max(1, Math.min(tw, (int) Math.floor(w * scale)));

        int plane = h * w;
        short[] data = new short[3 * plane];
        int[] argb = new int[plane];
        image.getRGB(0, 0, w, h, argb, 0, w);
        for (int i = 0; i < plane; i++) {
            int p = argb[i];
            data[i] = (short) ((p >> 16) & 0xFF);
            data[plane + i] = (short) ((p >> 8) & 0xFF);
            data[2 * plane + i] = (short) (p & 0xFF);
        }
        try (Tensor wide = Tensor.of(data, 1, 3, h, w);
             Tensor raw = wide.to(ScalarType.UInt8)) {
            Tensor resized = (ch != h || cw != w) ? Native.resizeBicubicAa(raw, ch, cw) : null;
            try {
                Tensor src = resized != null ? resized : raw;
                // black padding to the canvas (right and bottom), before the normalization, as in the reference
                try (Tensor padded = Aten.one("aten::constant_pad_nd", src, new long[]{0, tw - cw, 0, th - ch}, 0.0);
                     Tensor floats = padded.to(ScalarType.Float)) {
                    float[] mean = new float[3];
                    float[] std = new float[3];
                    for (int c = 0; c < 3; c++) {
                        mean[c] = (float) (args.mean()[c] * 255.0);
                        std[c] = (float) (args.std()[c] * 255.0);
                    }
                    try (Tensor m = Tensor.of(mean, 1, 3, 1, 1);
                         Tensor s = Tensor.of(std, 1, 3, 1, 1);
                         Tensor centered = floats.sub(m);
                         Tensor normalized = centered.div(s)) {
                        int p = args.patchSize();
                        int g = args.mergeSize();
                        int gh = th / p;
                        int gw = tw / p;
                        try (Tensor blocks = Aten.one("aten::reshape", normalized, new long[]{3, gh / g, g, p, gw / g, g, p});
                             Tensor order = Aten.one("aten::permute", blocks, new long[]{1, 4, 2, 5, 0, 3, 6});
                             Tensor rows = Aten.one("aten::reshape", order, new long[]{(long) gh * gw, args.patchDim()})) {
                            return new Patches(Aten.one("aten::contiguous", rows).copy(), new int[]{1, gh, gw});
                        }
                    }
                }
            } finally {
                if (resized != null) {
                    resized.close();
                }
            }
        }
    }
}
