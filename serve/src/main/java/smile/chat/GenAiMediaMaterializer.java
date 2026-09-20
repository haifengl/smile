/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import smile.llm.AudioUrlPart;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.TextPart;
import smile.llm.VideoUrlPart;

/**
 * Materializes data-URL / remote media to temp files for GenAI
 * {@code Images}/{@code Audios} loaders (local paths only).
 *
 * @author Haifeng Li
 */
public final class GenAiMediaMaterializer {
    private GenAiMediaMaterializer() {}

    /**
     * Rewrites image/audio parts to local file paths when needed.
     *
     * @param messages chat messages (may contain data URLs).
     * @return messages safe for {@link smile.onnx.genai.GenAiChatModel} multimodal.
     * @throws IOException if materialization fails.
     */
    public static Message[] materialize(Message[] messages) throws IOException {
        if (messages == null) {
            return null;
        }
        Message[] out = new Message[messages.length];
        Path dir = Files.createTempDirectory("smile-genai-media-");
        dir.toFile().deleteOnExit();
        for (int i = 0; i < messages.length; i++) {
            Message m = messages[i];
            if (m == null || !m.hasMedia()) {
                out[i] = m;
                continue;
            }
            List<ContentPart> parts = new ArrayList<>();
            for (ContentPart part : m.parts()) {
                parts.add(materializePart(part, dir));
            }
            out[i] = new Message(m.role(), parts, m.toolCalls(), m.toolCallId(), m.name());
        }
        return out;
    }

    private static ContentPart materializePart(ContentPart part, Path dir) throws IOException {
        return switch (part) {
            case ImageUrlPart image -> new ImageUrlPart(toLocalFile(image.url(), dir, "img"));
            case AudioUrlPart audio -> new AudioUrlPart(toLocalFile(audio.url(), dir, "aud"));
            case VideoUrlPart video -> throw new IOException(
                    "GenAI does not support video content parts");
            case TextPart text -> text;
            default -> part;
        };
    }

    private static String toLocalFile(String url, Path dir, String prefix) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("Empty media URL");
        }
        if (url.startsWith("file:")) {
            return Path.of(java.net.URI.create(url)).toString();
        }
        if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:"))) {
            return url;
        }
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma < 0) {
                throw new IOException("Invalid data URL");
            }
            String meta = url.substring(5, comma);
            String payload = url.substring(comma + 1);
            byte[] bytes = meta.toLowerCase(Locale.ROOT).contains(";base64")
                    ? Base64.getDecoder().decode(payload)
                    : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String ext = extensionFromMime(meta);
            Path file = Files.createTempFile(dir, prefix, ext);
            Files.write(file, bytes);
            file.toFile().deleteOnExit();
            return file.toAbsolutePath().toString();
        }
        // http(s): download
        try (var in = java.net.URI.create(url).toURL().openStream()) {
            Path file = Files.createTempFile(dir, prefix, ".bin");
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            return file.toAbsolutePath().toString();
        }
    }

    private static String extensionFromMime(String meta) {
        String m = meta.toLowerCase(Locale.ROOT);
        if (m.contains("png")) {
            return ".png";
        }
        if (m.contains("jpeg") || m.contains("jpg")) {
            return ".jpg";
        }
        if (m.contains("webp")) {
            return ".webp";
        }
        if (m.contains("wav")) {
            return ".wav";
        }
        if (m.contains("mpeg") || m.contains("mp3")) {
            return ".mp3";
        }
        return ".bin";
    }
}
