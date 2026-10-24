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
package smile.llm.model.minimax;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

/**
 * Turns decoded images and video frames into the patch matrices of the MiniMax-M3 vision tower, following the
 * reference {@code MiniMaxM3VLImageProcessor} / {@code MiniMaxM3VLVideoProcessor}: {@code smart_resize} to a multiple
 * of {@code patch * merge} pixels under a pixel budget, bicubic antialiased resize, rescale and normalize, pad the
 * frame count to a multiple of the temporal patch size by repeating the last frame, then cut into patches ordered
 * by (time group, merge block row, merge block column, row in block, column in block), each patch flattened as
 * (channel, time, row, column).
 *
 * @author Haifeng Li
 */
public final class MiniMaxVisionProcessor {
    /** Aspect ratios beyond this are rejected (as in the reference). */
    static final int MAX_RATIO = 200;
    /** Pixel budget of one image. */
    public static final int IMAGE_MAX_PIXELS = 451_584;
    /** Pixel budget of one video frame. */
    public static final int VIDEO_MAX_PIXELS = 768 * 28 * 28;
    /** Lower pixel bound of images and video frames. */
    public static final int MIN_PIXELS = 4 * 28 * 28;
    /** Pixel budget of all frames of one video (about 64k vision tokens). */
    public static final long VIDEO_TOTAL_PIXELS = (long) (64000 * 28 * 28 * 0.9);
    /** Largest accepted source image, to bound memory (width * height). */
    public static final long MAX_SOURCE_PIXELS = 100_000_000L;

    private static final double[] MEAN = {0.48145466, 0.4578275, 0.40821073};
    private static final double[] STD = {0.26862954, 0.26130258, 0.27577711};

    /**
     * Patches of one image or video and its {@code (t, h, w)} grid.
     *
     * @param pixels flattened patch tensor {@code [N, patchDim]}.
     * @param grid   the {@code (t, h, w)} patch grid dimensions.
     */
    public record Patches(Tensor pixels, int[] grid) { }

    private final int patchSize;
    private final int temporalPatchSize;
    private final int mergeSize;

    /**
     * Constructor.
     *
     * @param args the vision hyperparameters.
     */
    public MiniMaxVisionProcessor(MiniMaxVisionArgs args) {
        this.patchSize = args.patchSize();
        this.temporalPatchSize = args.temporalPatchSize();
        this.mergeSize = args.mergeSize();
    }

    /** Python's {@code round()} (half to even) times the factor. */
    private static int roundByFactor(double number, int factor) {
        return (int) Math.rint(number / factor) * factor;
    }

    private static int ceilByFactor(double number, int factor) {
        return (int) Math.ceil(number / factor) * factor;
    }

    private static int floorByFactor(double number, int factor) {
        return (int) Math.floor(number / factor) * factor;
    }

    /**
     * The reference {@code smart_resize}: both sides become multiples of {@code factor}, the area stays within
     * {@code [minPixels, maxPixels]} and the aspect ratio is kept approximately.
     *
     * @param height    source image height in pixels.
     * @param width     source image width in pixels.
     * @param factor    divisibility factor for output dimensions.
     * @param minPixels minimum total pixel count.
     * @param maxPixels maximum total pixel count.
     * @return {@code {height, width}}.
     * @throws IllegalArgumentException if the aspect ratio exceeds 200.
     */
    public static int[] smartResize(int height, int width, int factor, int minPixels, int maxPixels) {
        if (height <= 0 || width <= 0) {
            throw new IllegalArgumentException("empty image " + width + "x" + height);
        }
        double ratio = (double) Math.max(height, width) / Math.min(height, width);
        if (ratio > MAX_RATIO) {
            throw new IllegalArgumentException("absolute aspect ratio must be smaller than " + MAX_RATIO
                    + ", got " + ratio);
        }
        int h = Math.max(factor, roundByFactor(height, factor));
        int w = Math.max(factor, roundByFactor(width, factor));
        if ((long) h * w > maxPixels) {
            double beta = Math.sqrt((double) height * width / maxPixels);
            h = floorByFactor(height / beta, factor);
            w = floorByFactor(width / beta, factor);
        } else if ((long) h * w < minPixels) {
            double beta = Math.sqrt((double) minPixels / ((double) height * width));
            h = ceilByFactor(height * beta, factor);
            w = ceilByFactor(width * beta, factor);
        }
        return new int[]{h, w};
    }

    /**
     * Preprocesses one image.
     *
     * @param image the decoded image (any type; alpha is dropped).
     * @return patches {@code [gh * gw, patchDim]} (float32, CPU) and the grid {@code (1, gh, gw)}.
     */
    public Patches image(BufferedImage image) {
        checkSize(image);
        int[] size = smartResize(image.getHeight(), image.getWidth(), patchSize * mergeSize, MIN_PIXELS,
                IMAGE_MAX_PIXELS);
        return patchify(List.of(image), size[0], size[1]);
    }

    /**
     * Preprocesses one video given its sampled frames.
     *
     * @param frames the frames in time order (all the same size).
     * @return patches and the grid {@code (ceil(frames / temporalPatchSize), gh, gw)}.
     */
    public Patches video(List<BufferedImage> frames) {
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("video has no frames");
        }
        checkSize(frames.get(0));
        int padded = frames.size() + Math.floorMod(-frames.size(), temporalPatchSize);
        // The frame budget shrinks the per-frame pixel bound of long videos.
        long perFrame = Math.min(VIDEO_MAX_PIXELS, VIDEO_TOTAL_PIXELS / padded);
        int maxPixels = (int) Math.max(perFrame, MIN_PIXELS);
        int[] size = smartResize(frames.get(0).getHeight(), frames.get(0).getWidth(), patchSize * mergeSize,
                MIN_PIXELS, maxPixels);
        return patchify(frames, size[0], size[1]);
    }

    private static void checkSize(BufferedImage image) {
        if ((long) image.getWidth() * image.getHeight() > MAX_SOURCE_PIXELS) {
            throw new IllegalArgumentException("image too large: " + image.getWidth() + "x" + image.getHeight());
        }
    }

    /** Decoded frames to a uint8 tensor [F, 3, H, W] (RGB, alpha dropped). */
    private static Tensor toUint8(List<BufferedImage> frames) {
        int h = frames.get(0).getHeight();
        int w = frames.get(0).getWidth();
        int plane = h * w;
        short[] data = new short[frames.size() * 3 * plane];
        int[] argb = new int[plane];
        for (int f = 0; f < frames.size(); f++) {
            BufferedImage img = frames.get(f);
            if (img.getWidth() != w || img.getHeight() != h) {
                throw new IllegalArgumentException("video frames differ in size: " + img.getWidth() + "x"
                        + img.getHeight() + " vs " + w + "x" + h);
            }
            img.getRGB(0, 0, w, h, argb, 0, w);
            int base = f * 3 * plane;
            for (int i = 0; i < plane; i++) {
                int p = argb[i];
                data[base + i] = (short) ((p >> 16) & 0xFF);
                data[base + plane + i] = (short) ((p >> 8) & 0xFF);
                data[base + 2 * plane + i] = (short) (p & 0xFF);
            }
        }
        try (Tensor wide = Tensor.of(data, frames.size(), 3, h, w)) {
            return wide.to(ScalarType.UInt8);
        }
    }

    private Patches patchify(List<BufferedImage> frames, int height, int width) {
        try (Tensor raw = toUint8(frames);
             Tensor resized = Native.resizeBicubicAa(raw, height, width);
             Tensor floats = resized.to(ScalarType.Float)) {
            float[] mean = new float[3];
            float[] std = new float[3];
            for (int c = 0; c < 3; c++) {
                // (x / 255 - mean) / std, fused as the reference does: (x - mean * 255) / (std * 255).
                mean[c] = (float) (MEAN[c] * 255.0);
                std[c] = (float) (STD[c] * 255.0);
            }
            try (Tensor m = Tensor.of(mean, 1, 3, 1, 1);
                 Tensor s = Tensor.of(std, 1, 3, 1, 1);
                 Tensor centered = floats.sub(m);
                 Tensor normalized = centered.div(s)) {
                Tensor padded = normalized;
                int missing = Math.floorMod(-frames.size(), temporalPatchSize);
                List<Tensor> owned = new ArrayList<>();
                if (missing > 0) {
                    long last = frames.size() - 1;
                    try (var tail = smile.deep.tensor.Index.slice((int) last, (int) last + 1)) {
                        Tensor lastFrame = normalized.get(tail, smile.deep.tensor.Index.Ellipsis);
                        owned.add(lastFrame);
                        Tensor[] parts = new Tensor[missing + 1];
                        parts[0] = normalized;
                        for (int i = 1; i <= missing; i++) {
                            parts[i] = lastFrame;
                        }
                        padded = Tensor.vstack(parts);
                        owned.add(padded);
                    }
                }
                try {
                    int gridT = (int) padded.shape()[0] / temporalPatchSize;
                    int gridH = height / patchSize;
                    int gridW = width / patchSize;
                    int m2 = mergeSize;
                    // [F, C, H, W] -> (gt, T, C, gh/m, m, P, gw/m, m, P) -> (gt, gh/m, gw/m, m, m, C, T, P, P)
                    try (Tensor v = padded.reshape(gridT, temporalPatchSize, 3, gridH / m2, m2, patchSize,
                                 gridW / m2, m2, patchSize);
                         Tensor p = v.permute(0, 3, 6, 4, 7, 2, 1, 5, 8);
                         Tensor flat = p.reshape((long) gridT * gridH * gridW,
                                 3L * temporalPatchSize * patchSize * patchSize)) {
                        return new Patches(flat.copy(), new int[]{gridT, gridH, gridW});
                    }
                } finally {
                    owned.forEach(Tensor::close);
                }
            }
        }
    }
}
