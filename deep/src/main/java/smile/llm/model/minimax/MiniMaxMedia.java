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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * Reads the media of a chat request: images from data URLs, http(s) URLs or files, and videos as sampled frames
 * with their timestamps (ffmpeg extracts them, or a directory of numbered frame images stands in for a video).
 *
 * @author Haifeng Li
 */
final class MiniMaxMedia {
    /** Default sampling rate of videos (frames per second). */
    static final double DEFAULT_FPS = 1.0;
    /** A video is sampled to at least this many frames when its duration is known. */
    static final int MIN_FRAMES = 4;
    /** A video never yields more frames than this. */
    static final int MAX_FRAMES = 768;
    private static final long MAX_IMAGE_BYTES = 64L << 20;
    private static final long MAX_VIDEO_BYTES = 512L << 20;

    private MiniMaxMedia() { }

    /** Sampled video frames and the time of each in seconds. */
    record Video(List<BufferedImage> frames, double[] timestamps) { }

    static BufferedImage image(String url) throws IOException {
        byte[] bytes = bytes(url, MAX_IMAGE_BYTES);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("cannot decode image " + describe(url));
            }
            return image;
        }
    }

    private static String describe(String url) {
        return url.startsWith("data:") ? "(data URL)" : url;
    }

    private static byte[] bytes(String url, long limit) throws IOException {
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma < 0) {
                throw new IOException("invalid data URL");
            }
            String payload = url.substring(comma + 1);
            byte[] data = url.substring(0, comma).endsWith(";base64")
                    ? Base64.getDecoder().decode(payload)
                    : java.net.URLDecoder.decode(payload, StandardCharsets.ISO_8859_1).getBytes(StandardCharsets.ISO_8859_1);
            if (data.length > limit) {
                throw new IOException("media larger than " + (limit >> 20) + " MiB");
            }
            return data;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try (InputStream in = URI.create(url).toURL().openStream()) {
                byte[] data = in.readNBytes((int) Math.min(limit + 1, Integer.MAX_VALUE));
                if (data.length > limit) {
                    throw new IOException("media larger than " + (limit >> 20) + " MiB: " + url);
                }
                return data;
            }
        }
        Path path = path(url);
        if (Files.size(path) > limit) {
            throw new IOException("media larger than " + (limit >> 20) + " MiB: " + url);
        }
        return Files.readAllBytes(path);
    }

    private static Path path(String url) {
        return url.startsWith("file:") ? Path.of(URI.create(url)) : Path.of(url);
    }

    /**
     * Samples a video.
     *
     * @param url a video file, an http(s) or data URL of one, or a directory of numbered frame images.
     * @param fps frames per second to sample, or {@code null} for {@link #DEFAULT_FPS}.
     */
    static Video video(String url, Double fps) throws IOException {
        double rate = fps != null && fps > 0 ? fps : DEFAULT_FPS;
        if (!url.startsWith("data:") && !url.startsWith("http://") && !url.startsWith("https://")
                && Files.isDirectory(path(url))) {
            return fromFrameDirectory(path(url), rate);
        }
        Path tmpDir = Files.createTempDirectory("smile-minimax-video-");
        try {
            Path file;
            if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
                file = tmpDir.resolve("input");
                Files.write(file, bytes(url, MAX_VIDEO_BYTES));
            } else {
                file = path(url).toAbsolutePath();
            }
            double duration = duration(file);
            double effective = rate;
            if (duration > 0) {
                effective = Math.min(effective, MAX_FRAMES / duration);
                if (duration * effective < MIN_FRAMES) {
                    effective = Math.min(MIN_FRAMES / duration, MAX_FRAMES / duration * 1.0);
                }
            }
            Path out = tmpDir.resolve("frames");
            Files.createDirectory(out);
            run(List.of("ffmpeg", "-v", "error", "-y", "-i", file.toString(), "-vf", "fps=" + effective,
                    "-frames:v", Integer.toString(MAX_FRAMES), out.resolve("frame_%06d.png").toString()), url);
            return fromFrameDirectory(out, effective);
        } finally {
            deleteTree(tmpDir);
        }
    }

    private static Video fromFrameDirectory(Path dir, double rate) throws IOException {
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
            }).sorted().toList();
        }
        if (files.isEmpty()) {
            throw new IOException("no video frames found");
        }
        // Uniformly thin out directories with more than MAX_FRAMES frames.
        int n = files.size();
        int keep = Math.min(n, MAX_FRAMES);
        List<BufferedImage> frames = new ArrayList<>(keep);
        double[] times = new double[keep];
        for (int i = 0; i < keep; i++) {
            int source = keep == n ? i : (int) ((long) i * n / keep);
            BufferedImage frame = ImageIO.read(files.get(source).toFile());
            if (frame == null) {
                throw new IOException("cannot decode frame " + files.get(source).getFileName());
            }
            frames.add(frame);
            times[i] = source / rate;
        }
        return new Video(frames, times);
    }

    /** Duration in seconds via ffprobe, or {@code -1} when it is unavailable. */
    private static double duration(Path file) {
        try {
            Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "csv=p=0", file.toString()).redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) {
                return -1;
            }
            return Double.parseDouble(text);
        } catch (IOException | NumberFormatException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static void run(List<String> command, String url) throws IOException {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(10, TimeUnit.MINUTES) || p.exitValue() != 0) {
                p.destroyForcibly();
                throw new IOException("ffmpeg failed for " + describe(url) + ": " + log.strip());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg interrupted", e);
        }
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best effort
        }
    }
}
