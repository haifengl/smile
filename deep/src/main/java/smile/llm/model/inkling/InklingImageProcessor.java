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
package smile.llm.model.inkling;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import smile.deep.tensor.Tensor;

/**
 * The Inkling-2 image processor: no resizing, the image is cut into tiles of 40x40 pixels (one language-model token
 * each), padded at the bottom and right with -1 on the 0-255 scale, rescaled and normalized to [-1, 1] (mean and std 0.5).
 * Images with an alpha channel are composited on an 8-pixel checkerboard of 204 and 153. Images that would need more than
 * {@link InklingVisionArgs#maxTokens()} tiles are first scaled down (area averaging), keeping the aspect ratio.
 *
 * @author Haifeng Li
 */
public final class InklingImageProcessor {
    /**
     * Preprocessed image tiles and their spatial grid.
     *
     * @param pixels tiles {@code [N, tile, tile, 3]} (float32, CPU).
     * @param grid   the {@code (1, rows, cols)} grid in tiles.
     */
    public record Patches(Tensor pixels, int[] grid) { }

    private static final float RESCALE = 0.00392156862745098f;
    private final InklingVisionArgs args;

    /**
     * Constructor.
     *
     * @param args vision arguments.
     */
    public InklingImageProcessor(InklingVisionArgs args) {
        this.args = args;
    }

    /**
     * Preprocesses one image.
     *
     * @param image the image.
     * @return the tiles and grid.
     */
    public Patches process(BufferedImage image) {
        int tile = args.tile();
        BufferedImage source = fit(image);
        int height = source.getHeight(), width = source.getWidth();
        if (height < 1 || width < 1) {
            throw new IllegalArgumentException("empty image");
        }
        int rows = (height + tile - 1) / tile, cols = (width + tile - 1) / tile;
        boolean alpha = source.getColorModel().hasAlpha();
        boolean gray = source.getType() == BufferedImage.TYPE_BYTE_GRAY || source.getType() == BufferedImage.TYPE_USHORT_GRAY;
        Raster raster = source.getRaster();
        float pad = (-1f * RESCALE - 0.5f) / 0.5f;
        float[] out = new float[rows * cols * tile * tile * 3];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int base = (r * cols + c) * tile * tile * 3;
                for (int y = 0; y < tile; y++) {
                    for (int x = 0; x < tile; x++) {
                        int py = r * tile + y, px = c * tile + x;
                        int o = base + (y * tile + x) * 3;
                        if (py >= height || px >= width) {
                            out[o] = out[o + 1] = out[o + 2] = pad;
                            continue;
                        }
                        int rr, gg, bb;
                        if (gray) {
                            int sample = raster.getSample(px, py, 0);
                            if (source.getType() == BufferedImage.TYPE_USHORT_GRAY) {
                                sample = sample >> 8;
                            }
                            rr = gg = bb = sample;
                        } else {
                            int argb = source.getRGB(px, py);
                            rr = argb >> 16 & 255;
                            gg = argb >> 8 & 255;
                            bb = argb & 255;
                            if (alpha) {
                                int a = argb >>> 24;
                                int background = ((py / 8) + (px / 8)) % 2 == 0 ? 204 : 153;
                                rr = (rr * a + background * (255 - a) + 127) / 255;
                                gg = (gg * a + background * (255 - a) + 127) / 255;
                                bb = (bb * a + background * (255 - a) + 127) / 255;
                            }
                        }
                        out[o] = ((float) rr * RESCALE - 0.5f) / 0.5f;
                        out[o + 1] = ((float) gg * RESCALE - 0.5f) / 0.5f;
                        out[o + 2] = ((float) bb * RESCALE - 0.5f) / 0.5f;
                    }
                }
            }
        }
        return new Patches(Tensor.of(out, (long) rows * cols, tile, tile, 3), new int[]{1, rows, cols});
    }

    /** Scales the image down so it needs at most {@code maxTokens} tiles. */
    private BufferedImage fit(BufferedImage image) {
        int tile = args.tile();
        long tokens = (long) ((image.getHeight() + tile - 1) / tile) * ((image.getWidth() + tile - 1) / tile);
        if (tokens <= args.maxTokens()) {
            return image;
        }
        double scale = Math.sqrt((double) args.maxTokens() * tile * tile / ((double) image.getHeight() * image.getWidth()));
        int w = Math.max(1, (int) Math.floor(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.floor(image.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(w, h, image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image.getScaledInstance(w, h, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        return scaled;
    }
}
