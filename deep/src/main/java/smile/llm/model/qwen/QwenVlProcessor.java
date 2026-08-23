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
package smile.llm.model.qwen;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.TextPart;
import smile.llm.VideoUrlPart;
import smile.util.IntArrayList;

/**
 * HuggingFace-compatible image/video preprocessor for Qwen3.8 native VLM.
 *
 * <p>Produces packed patches, grid metadata, expanded chat token ids, and
 * interleaved mRoPE position planes.
 *
 * @author Haifeng Li
 */
public class QwenVlProcessor {
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(QwenVlProcessor.class);

    private final QwenVisionArgs visionArgs;
    private final Tokenizer tokenizer;
    private final int patchSize;
    private final int temporalPatchSize;
    private final int mergeSize;
    private final int minPixels;
    private final int maxPixels;
    private final double[] imageMean;
    private final double[] imageStd;
    private final double defaultVideoFps;
    private final int videoMaxPixels;

    /**
     * @param visionArgs vision / token id config.
     * @param tokenizer  chat tokenizer.
     * @param patchSize  spatial patch size.
     * @param temporalPatchSize temporal patch size.
     * @param mergeSize  spatial merge size.
     * @param minPixels  smart-resize min pixels.
     * @param maxPixels  smart-resize max pixels.
     * @param imageMean  normalize mean (RGB).
     * @param imageStd   normalize std (RGB).
     * @param defaultVideoFps default video sampling fps.
     * @param videoMaxPixels video smart-resize max pixels.
     */
    public QwenVlProcessor(QwenVisionArgs visionArgs, Tokenizer tokenizer,
                           int patchSize, int temporalPatchSize, int mergeSize,
                           int minPixels, int maxPixels,
                           double[] imageMean, double[] imageStd,
                           double defaultVideoFps, int videoMaxPixels) {
        this.visionArgs = visionArgs;
        this.tokenizer = tokenizer;
        this.patchSize = patchSize;
        this.temporalPatchSize = temporalPatchSize;
        this.mergeSize = mergeSize;
        this.minPixels = minPixels;
        this.maxPixels = maxPixels;
        this.imageMean = imageMean;
        this.imageStd = imageStd;
        this.defaultVideoFps = defaultVideoFps;
        this.videoMaxPixels = videoMaxPixels;
    }

    /**
     * Loads processor settings from a checkpoint directory.
     *
     * @param checkpointDir model directory.
     * @param visionArgs    vision args from config.json.
     * @param tokenizer     tokenizer.
     * @return processor.
     * @throws IOException if configs cannot be read.
     */
    public static QwenVlProcessor fromCheckpoint(String checkpointDir,
                                                 QwenVisionArgs visionArgs,
                                                 Tokenizer tokenizer) throws IOException {
        Path dir = Path.of(checkpointDir);
        ObjectMapper mapper = new ObjectMapper();
        int patchSize = visionArgs.patchSize();
        int temporal = visionArgs.temporalPatchSize();
        int merge = visionArgs.spatialMergeSize();
        int minPixels = 4 * 28 * 28;
        int maxPixels = 16384 * 28 * 28;
        double[] mean = new double[]{0.5, 0.5, 0.5};
        double[] std = new double[]{0.5, 0.5, 0.5};
        Path prep = dir.resolve("preprocessor_config.json");
        if (Files.exists(prep)) {
            JsonNode root = mapper.readTree(prep.toFile());
            if (root.has("patch_size")) patchSize = root.get("patch_size").asInt();
            if (root.has("temporal_patch_size")) temporal = root.get("temporal_patch_size").asInt();
            if (root.has("merge_size")) merge = root.get("merge_size").asInt();
            if (root.has("min_pixels")) minPixels = root.get("min_pixels").asInt();
            if (root.has("max_pixels")) maxPixels = root.get("max_pixels").asInt();
            if (root.has("size") && root.get("size").has("longest_edge")) {
                maxPixels = root.get("size").get("longest_edge").asInt();
            }
            if (root.has("image_mean") && root.get("image_mean").isArray()) {
                mean = new double[]{
                        root.get("image_mean").get(0).asDouble(),
                        root.get("image_mean").get(1).asDouble(),
                        root.get("image_mean").get(2).asDouble()
                };
            }
            if (root.has("image_std") && root.get("image_std").isArray()) {
                std = new double[]{
                        root.get("image_std").get(0).asDouble(),
                        root.get("image_std").get(1).asDouble(),
                        root.get("image_std").get(2).asDouble()
                };
            }
        }
        int videoMax = maxPixels;
        double fps = 2.0;
        Path vprep = dir.resolve("video_preprocessor_config.json");
        if (Files.exists(vprep)) {
            JsonNode root = mapper.readTree(vprep.toFile());
            if (root.has("size") && root.get("size").has("longest_edge")) {
                videoMax = root.get("size").get("longest_edge").asInt();
            }
            if (root.has("max_pixels")) videoMax = root.get("max_pixels").asInt();
        }
        return new QwenVlProcessor(visionArgs, tokenizer, patchSize, temporal, merge,
                minPixels, maxPixels, mean, std, fps, videoMax);
    }

    /**
     * Processes a chat dialog into tokens, vision tensors, and mRoPE planes.
     *
     * @param dialog conversation turns.
     * @return processed multimodal batch (single sequence).
     * @throws IOException if media cannot be loaded.
     */
    public ProcessedMultimodal process(Message... dialog) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<List<BufferedImage>> videos = new ArrayList<>();
        List<Double> videoFps = new ArrayList<>();

        IntArrayList tokens = new IntArrayList();
        for (Message message : dialog) {
            tokens.add(tokenizer.specialToken("<|im_start|>"));
            String role = switch (message.role()) {
                case system -> "system";
                case user -> "user";
                case assistant -> "assistant";
                default -> message.role().name();
            };
            tokens.add(tokenizer.encode(role + "\n", false, false));
            for (ContentPart part : message.parts()) {
                if (part instanceof TextPart text) {
                    tokens.add(tokenizer.encode(text.text(), false, false));
                } else if (part instanceof ImageUrlPart image) {
                    BufferedImage img = loadImage(image.url());
                    images.add(img);
                    tokens.add(tokenizer.specialToken("<|vision_start|>"));
                    tokens.add(tokenizer.specialToken("<|image_pad|>")); // expanded later
                    tokens.add(tokenizer.specialToken("<|vision_end|>"));
                } else if (part instanceof VideoUrlPart video) {
                    double fps = video.fps() != null ? video.fps() : defaultVideoFps;
                    List<BufferedImage> frames = loadVideoFrames(video.url(), fps);
                    videos.add(frames);
                    videoFps.add(fps);
                    tokens.add(tokenizer.specialToken("<|vision_start|>"));
                    tokens.add(tokenizer.specialToken("<|video_pad|>"));
                    tokens.add(tokenizer.specialToken("<|vision_end|>"));
                }
            }
            tokens.add(tokenizer.specialToken("<|im_end|>"));
            tokens.add(tokenizer.encode("\n", false, false));
        }
        tokens.add(tokenizer.specialToken("<|im_start|>"));
        tokens.add(tokenizer.encode("assistant\n", false, false));

        List<float[]> allPatches = new ArrayList<>();
        List<int[]> imageGrids = new ArrayList<>();
        List<int[]> videoGrids = new ArrayList<>();
        List<Integer> imagePadCounts = new ArrayList<>();
        List<Integer> videoPadCounts = new ArrayList<>();

        for (BufferedImage img : images) {
            PatchPack pack = packImage(img, maxPixels);
            allPatches.addAll(pack.patches);
            imageGrids.add(pack.gridThw);
            imagePadCounts.add(visionArgs.mergedTokens(pack.gridThw[0], pack.gridThw[1], pack.gridThw[2]));
        }
        for (int vi = 0; vi < videos.size(); vi++) {
            PatchPack pack = packVideo(videos.get(vi), videoMaxPixels);
            allPatches.addAll(pack.patches);
            videoGrids.add(pack.gridThw);
            videoPadCounts.add(visionArgs.mergedTokens(pack.gridThw[0], pack.gridThw[1], pack.gridThw[2]));
        }

        int[] expanded = expandPads(tokens.toArray(), imagePadCounts, videoPadCounts);
        int[] mmTypes = buildMmTokenTypes(expanded);
        int[][] imgGridArr = imageGrids.toArray(int[][]::new);
        int[][] vidGridArr = videoGrids.toArray(int[][]::new);
        var mrope = InterleavedMRope.getRopeIndex(
                mmTypes, imgGridArr, vidGridArr, mergeSize);

        Tensor pixelValues = null;
        if (!allPatches.isEmpty()) {
            int patchDim = visionArgs.patchDim();
            float[] flat = new float[allPatches.size() * patchDim];
            for (int i = 0; i < allPatches.size(); i++) {
                System.arraycopy(allPatches.get(i), 0, flat, i * patchDim, patchDim);
            }
            pixelValues = Tensor.of(flat, allPatches.size(), patchDim);
        }

        return new ProcessedMultimodal(
                expanded, mmTypes, pixelValues, imgGridArr, vidGridArr, mrope);
    }

    private int[] expandPads(int[] raw, List<Integer> imagePads, List<Integer> videoPads) {
        int imagePadId = tokenizer.specialToken("<|image_pad|>");
        int videoPadId = tokenizer.specialToken("<|video_pad|>");
        IntArrayList out = new IntArrayList();
        int imgPtr = 0;
        int vidPtr = 0;
        for (int id : raw) {
            if (id == imagePadId) {
                int n = imagePads.get(imgPtr++);
                for (int i = 0; i < n; i++) {
                    out.add(imagePadId);
                }
            } else if (id == videoPadId) {
                int n = videoPads.get(vidPtr++);
                for (int i = 0; i < n; i++) {
                    out.add(videoPadId);
                }
            } else {
                out.add(id);
            }
        }
        return out.toArray();
    }

    private int[] buildMmTokenTypes(int[] ids) {
        int imagePadId = tokenizer.specialToken("<|image_pad|>");
        int videoPadId = tokenizer.specialToken("<|video_pad|>");
        int[] types = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == imagePadId) {
                types[i] = 1;
            } else if (ids[i] == videoPadId) {
                types[i] = 2;
            } else {
                types[i] = 0;
            }
        }
        return types;
    }

    private PatchPack packImage(BufferedImage src, int maxPix) {
        int[] size = smartResize(src.getWidth(), src.getHeight(), minPixels, maxPix);
        BufferedImage resized = resize(src, size[0], size[1]);
        int h = size[1] / patchSize;
        int w = size[0] / patchSize;
        // T=1 for images; duplicate temporal for Conv3d receptive field inside patch vector.
        List<float[]> patches = extractPatches(List.of(resized, resized), 1, h, w);
        // After temporal pack with temporalPatchSize=2 and T_frames=2 → T_grid=1
        return new PatchPack(patches, new int[]{1, h, w});
    }

    private PatchPack packVideo(List<BufferedImage> frames, int maxPix) {
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("video has no frames");
        }
        // Pad frames to multiple of temporalPatchSize.
        List<BufferedImage> padded = new ArrayList<>(frames);
        while (padded.size() % temporalPatchSize != 0) {
            padded.add(padded.get(padded.size() - 1));
        }
        int[] size = smartResize(padded.get(0).getWidth(), padded.get(0).getHeight(),
                minPixels, maxPix);
        List<BufferedImage> resized = new ArrayList<>();
        for (BufferedImage f : padded) {
            resized.add(resize(f, size[0], size[1]));
        }
        int h = size[1] / patchSize;
        int w = size[0] / patchSize;
        int t = resized.size() / temporalPatchSize;
        List<float[]> patches = extractPatches(resized, t, h, w);
        return new PatchPack(patches, new int[]{t, h, w});
    }

    /**
     * Extract patches in merge-block-major order with temporal packing.
     */
    private List<float[]> extractPatches(List<BufferedImage> frames, int tGrid, int h, int w) {
        List<float[]> out = new ArrayList<>();
        int m = mergeSize;
        // frames layout: for each temporal group of temporalPatchSize frames
        for (int tg = 0; tg < tGrid; tg++) {
            int f0 = tg * temporalPatchSize;
            // merge-block major over spatial
            for (int hb = 0; hb < h / m; hb++) {
                for (int wb = 0; wb < w / m; wb++) {
                    for (int hi = 0; hi < m; hi++) {
                        for (int wi = 0; wi < m; wi++) {
                            int y = (hb * m + hi) * patchSize;
                            int x = (wb * m + wi) * patchSize;
                            float[] patch = new float[visionArgs.patchDim()];
                            int offset = 0;
                            for (int c = 0; c < 3; c++) {
                                for (int tf = 0; tf < temporalPatchSize; tf++) {
                                    BufferedImage frame = frames.get(Math.min(f0 + tf, frames.size() - 1));
                                    for (int py = 0; py < patchSize; py++) {
                                        for (int px = 0; px < patchSize; px++) {
                                            int rgb = frame.getRGB(x + px, y + py);
                                            double v = channel(rgb, c);
                                            v = (v - imageMean[c]) / imageStd[c];
                                            patch[offset++] = (float) v;
                                        }
                                    }
                                }
                            }
                            out.add(patch);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static double channel(int rgb, int c) {
        return switch (c) {
            case 0 -> ((rgb >> 16) & 0xFF) / 255.0;
            case 1 -> ((rgb >> 8) & 0xFF) / 255.0;
            default -> (rgb & 0xFF) / 255.0;
        };
    }

    /**
     * Qwen smart_resize: dimensions divisible by {@code factor = patch*merge}.
     */
    int[] smartResize(int width, int height, int minPix, int maxPix) {
        int factor = patchSize * mergeSize;
        double aspect = (double) width / height;
        int hBar = Math.max(factor, roundByFactor(height, factor));
        int wBar = Math.max(factor, roundByFactor(width, factor));
        if (hBar * wBar > maxPix) {
            double beta = Math.sqrt((double) (height * width) / maxPix);
            hBar = Math.max(factor, floorByFactor((int) (height / beta), factor));
            wBar = Math.max(factor, floorByFactor((int) (width / beta), factor));
        } else if (hBar * wBar < minPix) {
            double beta = Math.sqrt((double) minPix / (height * width));
            hBar = Math.max(factor, ceilByFactor((int) (height * beta), factor));
            wBar = Math.max(factor, ceilByFactor((int) (width * beta), factor));
        }
        // Preserve aspect approximately
        if (aspect > 1) {
            wBar = Math.max(factor, roundByFactor((int) (hBar * aspect), factor));
        } else {
            hBar = Math.max(factor, roundByFactor((int) (wBar / aspect), factor));
        }
        return new int[]{wBar, hBar};
    }

    private static int roundByFactor(int n, int factor) {
        return Math.round((float) n / factor) * factor;
    }

    private static int floorByFactor(int n, int factor) {
        return (n / factor) * factor;
    }

    private static int ceilByFactor(int n, int factor) {
        return ((n + factor - 1) / factor) * factor;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        Image tmp = src.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(tmp, 0, 0, null);
        g.dispose();
        return dst;
    }

    private static Path pathFromUrl(String url) {
        if (url.startsWith("file:")) {
            return Path.of(URI.create(url));
        }
        return Path.of(url);
    }

    static BufferedImage loadImage(String url) throws IOException {
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma < 0) {
                throw new IOException("invalid data URL");
            }
            byte[] bytes = Base64.getDecoder().decode(url.substring(comma + 1));
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                BufferedImage img = ImageIO.read(in);
                if (img == null) {
                    throw new IOException("cannot decode data URL image");
                }
                return img;
            }
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try (InputStream in = URI.create(url).toURL().openStream()) {
                BufferedImage img = ImageIO.read(in);
                if (img == null) {
                    throw new IOException("cannot decode image URL: " + url);
                }
                return img;
            }
        }
        Path path = pathFromUrl(url);
        BufferedImage img = ImageIO.read(path.toFile());
        if (img == null) {
            throw new IOException("cannot decode image file: " + url);
        }
        return img;
    }

    /**
     * Samples video frames via ffmpeg when available; otherwise requires a
     * directory of frame images (for tests).
     */
    List<BufferedImage> loadVideoFrames(String url, double fps) throws IOException {
        Path path = pathFromUrl(url);
        if (Files.isDirectory(path)) {
            List<BufferedImage> frames = new ArrayList<>();
            try (var stream = Files.list(path)) {
                List<Path> files = stream
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                        })
                        .sorted()
                        .toList();
                for (Path f : files) {
                    frames.add(ImageIO.read(f.toFile()));
                }
            }
            if (frames.isEmpty()) {
                throw new IOException("no frames in " + path);
            }
            return frames;
        }
        Path tmp = Files.createTempDirectory("smile-qwen-video-");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", path.toAbsolutePath().toString(),
                    "-vf", "fps=" + fps,
                    tmp.resolve("frame_%06d.png").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            if (code != 0) {
                throw new IOException("ffmpeg failed with exit " + code + " for " + url);
            }
            return loadVideoFrames(tmp.toString(), fps);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg interrupted", e);
        } finally {
            try (var stream = Files.list(tmp)) {
                stream.forEach(f -> {
                    try {
                        Files.deleteIfExists(f);
                    } catch (IOException ignored) {
                    }
                });
            }
            Files.deleteIfExists(tmp);
        }
    }

    private record PatchPack(List<float[]> patches, int[] gridThw) {}

    /**
     * Result of multimodal preprocessing for one prompt.
     *
     * @param inputIds       expanded token ids including vision pads.
     * @param mmTokenTypeIds 0=text, 1=image, 2=video.
     * @param pixelValues    packed patches, or null when text-only.
     * @param imageGridThw   image grids.
     * @param videoGridThw   video grids.
     * @param mrope          interleaved mRoPE planes.
     */
    public record ProcessedMultimodal(
            int[] inputIds,
            int[] mmTokenTypeIds,
            Tensor pixelValues,
            int[][] imageGridThw,
            int[][] videoGridThw,
            InterleavedMRope.MropePositions mrope) {

        /** @return {@code true} when vision tensors are present. */
        public boolean hasVision() {
            return pixelValues != null;
        }

        /**
         * Moves pixel values to {@code device} (no-op when null).
         *
         * @param device target device.
         * @param dtype  compute dtype.
         * @return this, with updated pixelValues ownership transferred.
         */
        public ProcessedMultimodal to(Device device, ScalarType dtype) {
            if (pixelValues == null) {
                return this;
            }
            Tensor moved = pixelValues.to(device).to(dtype);
            if (moved != pixelValues) {
                pixelValues.close();
            }
            return new ProcessedMultimodal(inputIds, mmTokenTypeIds, moved,
                    imageGridThw, videoGridThw, mrope);
        }
    }
}
