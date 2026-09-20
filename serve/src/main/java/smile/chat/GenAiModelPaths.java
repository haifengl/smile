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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import smile.util.HuggingFaceHub;

/**
 * Locates GenAI-ready model directories (local or Hugging Face snapshot).
 *
 * @author Haifeng Li
 */
public final class GenAiModelPaths {
    private static final Logger logger = Logger.getLogger(GenAiModelPaths.class);
    private static final Pattern FILENAME = Pattern.compile(
            "\"filename\"\\s*:\\s*\"([^\"]+)\"");
    private static final String[] TOKENIZER_FILES = {
            "tokenizer.json",
            "tokenizer_config.json",
            "special_tokens_map.json",
            "tokenizer.model",
            "vocab.json",
            "merges.txt"
    };

    private GenAiModelPaths() {}

    /**
     * Returns {@code true} when {@code dir} contains {@code genai_config.json}.
     *
     * @param dir candidate model directory.
     * @return whether the directory looks GenAI-ready.
     */
    public static boolean isGenAiCheckpoint(Path dir) {
        return dir != null && Files.isRegularFile(dir.resolve("genai_config.json"));
    }

    /**
     * Resolves a GenAI-ready directory from a local path or HF repo id.
     *
     * <p>For HF repos that already publish GenAI assets, downloads
     * {@code genai_config.json} and companion files into the Hub cache and
     * returns the snapshot directory.
     *
     * @param modelSpec local dir or {@code owner/name}.
     * @return GenAI model dir, or empty when not GenAI-ready.
     */
    public static Optional<Path> resolveGenAiReady(String modelSpec) {
        if (modelSpec == null || modelSpec.isBlank()) {
            return Optional.empty();
        }
        Path local = Path.of(modelSpec);
        if (Files.isDirectory(local) && isGenAiCheckpoint(local)) {
            return Optional.of(local.toAbsolutePath().normalize());
        }
        if (!ChatService.looksLikeHuggingFaceRepoId(modelSpec)) {
            return Optional.empty();
        }
        try {
            Path cfg = HuggingFaceHub.download(modelSpec, "genai_config.json");
            Path root = cfg.getParent();
            downloadCompanions(modelSpec, root, Files.readString(cfg));
            if (isGenAiCheckpoint(root)) {
                logger.infof("HF GenAI-ready snapshot: %s", root);
                return Optional.of(root);
            }
        } catch (IOException e) {
            logger.debugf("Not a GenAI-ready HF repo (%s): %s", modelSpec, e.getMessage());
        }
        return Optional.empty();
    }

    private static void downloadCompanions(String repoId, Path root, String genaiJson)
            throws IOException {
        List<String> files = new ArrayList<>();
        Matcher m = FILENAME.matcher(genaiJson);
        while (m.find()) {
            files.add(m.group(1));
        }
        for (String name : TOKENIZER_FILES) {
            files.add(name);
        }
        // External data / ONNX siblings often sit next to filename entries.
        List<String> extras = new ArrayList<>();
        for (String f : files) {
            if (f.endsWith(".onnx")) {
                extras.add(f + ".data");
            }
        }
        files.addAll(extras);
        for (String file : files) {
            if (file == null || file.isBlank()) {
                continue;
            }
            Path existing = root.resolve(file);
            if (Files.isRegularFile(existing)) {
                continue;
            }
            try {
                HuggingFaceHub.download(repoId, file);
            } catch (IOException e) {
                logger.debugf("Optional GenAI companion missing %s/%s: %s",
                        repoId, file, e.getMessage());
            }
        }
    }
}
