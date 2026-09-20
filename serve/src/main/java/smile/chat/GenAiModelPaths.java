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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import smile.onnx.genai.GenAI;
import smile.onnx.genai.GenAIOliveTarget;
import smile.util.HuggingFaceHub;

/**
 * Locates GenAI-ready model directories (local or Hugging Face snapshot).
 *
 * <p>Vendor ONNX GenAI packages (e.g. {@code microsoft/Phi-3-mini-4k-instruct-onnx})
 * often nest {@code genai_config.json} under provider folders such as
 * {@code cuda/cuda-int4-rtn-block-32/}. Those are opened directly — Olive is not
 * used (there are no PyTorch weights to convert).
 *
 * @author Haifeng Li
 */
public final class GenAiModelPaths {
    private static final Logger logger = Logger.getLogger(GenAiModelPaths.class);
    private static final Pattern FILENAME = Pattern.compile(
            "\"filename\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TREE_PATH = Pattern.compile(
            "\"path\"\\s*:\\s*\"([^\"]*genai_config\\.json)\"");
    private static final String[] TOKENIZER_FILES = {
            "tokenizer.json",
            "tokenizer_config.json",
            "special_tokens_map.json",
            "tokenizer.model",
            "vocab.json",
            "merges.txt",
            "added_tokens.json",
            "config.json"
    };
    /** Well-known nested layouts when the HF tree API is unavailable. */
    private static final String[] FALLBACK_GENAI_PATHS = {
            "cuda/cuda-int4-rtn-block-32/genai_config.json",
            "cuda/cuda-fp16/genai_config.json",
            "directml/directml-int4-awq-block-128/genai_config.json",
            "cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/genai_config.json",
            "cpu_and_mobile/cpu-int4-rtn-block-32/genai_config.json",
            "cpu/genai_config.json",
            "onnx/genai_config.json"
    };
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
     * <p>For HF repos that already publish GenAI assets (root or nested under
     * provider folders), downloads {@code genai_config.json} and companions into
     * the Hub cache and returns that package directory.
     *
     * @param modelSpec local dir or {@code owner/name}.
     * @return GenAI model dir, or empty when not GenAI-ready.
     */
    public static Optional<Path> resolveGenAiReady(String modelSpec) {
        if (modelSpec == null || modelSpec.isBlank()) {
            return Optional.empty();
        }
        Path local = Path.of(modelSpec);
        if (Files.isDirectory(local)) {
            if (isGenAiCheckpoint(local)) {
                return Optional.of(local.toAbsolutePath().normalize());
            }
            Optional<Path> nested = findLocalGenAiRoot(local);
            if (nested.isPresent()) {
                return nested;
            }
            return Optional.empty();
        }
        if (!ChatService.looksLikeHuggingFaceRepoId(modelSpec)) {
            return Optional.empty();
        }
        return resolveHfGenAiReady(modelSpec);
    }

    /**
     * Finds a nested GenAI package under a local directory tree.
     */
    static Optional<Path> findLocalGenAiRoot(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return Optional.empty();
        }
        GenAIOliveTarget target = GenAI.resolveOliveTarget();
        try (var walk = Files.walk(root, 5)) {
            List<Path> configs = walk
                    .filter(p -> p.getFileName() != null
                            && "genai_config.json".equals(p.getFileName().toString()))
                    .map(Path::getParent)
                    .toList();
            return pickPreferredDir(configs, target).map(p -> p.toAbsolutePath().normalize());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> resolveHfGenAiReady(String repoId) {
        // 1) Root genai_config.json
        try {
            Path cfg = HuggingFaceHub.download(repoId, "genai_config.json");
            Path root = cfg.getParent();
            downloadCompanions(repoId, "", root, Files.readString(cfg));
            if (isGenAiCheckpoint(root)) {
                logger.infof("HF GenAI-ready snapshot: %s", root);
                return Optional.of(root);
            }
        } catch (IOException e) {
            logger.debugf("No root genai_config.json in %s: %s", repoId, e.getMessage());
        }

        // 2) Nested packages (Phi ONNX, AMD OGA collections, …)
        GenAIOliveTarget target = GenAI.resolveOliveTarget();
        List<String> candidates = listHfGenAiConfigPaths(repoId);
        if (candidates.isEmpty()) {
            candidates = List.of(FALLBACK_GENAI_PATHS);
        }
        Optional<String> chosen = pickPreferredPath(candidates, target);
        if (chosen.isEmpty()) {
            logger.debugf("No nested genai_config.json found for %s", repoId);
            return Optional.empty();
        }
        String relativeCfg = chosen.get();
        String relativeDir = relativeCfg.contains("/")
                ? relativeCfg.substring(0, relativeCfg.lastIndexOf('/'))
                : "";
        try {
            Path cfg = HuggingFaceHub.download(repoId, relativeCfg);
            Path root = cfg.getParent();
            downloadCompanions(repoId, relativeDir, root, Files.readString(cfg));
            if (isGenAiCheckpoint(root)) {
                logger.infof("HF GenAI-ready nested snapshot (%s): %s", relativeDir, root);
                return Optional.of(root);
            }
        } catch (IOException e) {
            logger.warnf(e, "Failed to materialize nested GenAI package %s/%s",
                    repoId, relativeCfg);
        }
        return Optional.empty();
    }

    /**
     * Lists {@code …/genai_config.json} paths via the Hugging Face tree API.
     */
    static List<String> listHfGenAiConfigPaths(String repoId) {
        String endpoint = System.getenv().getOrDefault("HF_ENDPOINT", "https://huggingface.co");
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        String url = endpoint + "/api/models/" + repoId + "/tree/main?recursive=true";
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            String token = System.getenv("HF_TOKEN");
            if (token != null && !token.isBlank()) {
                b.header("Authorization", "Bearer " + token.trim());
            }
            HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                logger.debugf("HF tree API %s -> HTTP %d", repoId, resp.statusCode());
                return List.of();
            }
            List<String> paths = new ArrayList<>();
            Matcher m = TREE_PATH.matcher(resp.body());
            while (m.find()) {
                paths.add(m.group(1));
            }
            return paths;
        } catch (Exception e) {
            logger.debugf("HF tree API failed for %s: %s", repoId, e.getMessage());
            return List.of();
        }
    }

    static Optional<String> pickPreferredPath(List<String> configPaths, GenAIOliveTarget target) {
        if (configPaths == null || configPaths.isEmpty()) {
            return Optional.empty();
        }
        String id = target == null ? "cpu" : target.candidateId();
        return configPaths.stream()
                .min(Comparator.comparingInt(p -> scorePath(p, id)));
    }

    static Optional<Path> pickPreferredDir(List<Path> dirs, GenAIOliveTarget target) {
        if (dirs == null || dirs.isEmpty()) {
            return Optional.empty();
        }
        String id = target == null ? "cpu" : target.candidateId();
        return dirs.stream()
                .min(Comparator.comparingInt(p -> scorePath(p.toString().replace('\\', '/'), id)));
    }

    /**
     * Lower score = better match for the Olive/GenAI cascade winner.
     */
    static int scorePath(String path, String candidateId) {
        String p = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        String id = candidateId == null ? "cpu" : candidateId.toLowerCase(Locale.ROOT);
        int score = 1000;
        if ("cuda".equals(id)) {
            if (p.contains("/cuda/") || p.startsWith("cuda/")) {
                score = p.contains("int4") ? 10 : 20;
            } else if (p.contains("directml") || p.contains("/dml/")) {
                score = 80;
            } else if (p.contains("cpu")) {
                score = 90;
            }
        } else if ("dml".equals(id) || "ryzenai".equals(id)) {
            if (p.contains("directml") || p.contains("/dml/")) {
                score = 10;
            } else if (p.contains("cuda")) {
                score = 80;
            } else if (p.contains("cpu")) {
                score = 90;
            }
        } else {
            if (p.contains("cpu")) {
                score = p.contains("acc-level") ? 10 : 15;
            } else if (p.contains("cuda")) {
                score = 80;
            } else if (p.contains("directml")) {
                score = 85;
            }
        }
        // Prefer shorter paths when scores tie.
        return score * 100 + Math.min(p.length(), 99);
    }

    private static void downloadCompanions(String repoId, String relativeDir, Path root,
                                           String genaiJson) {
        Set<String> files = new LinkedHashSet<>();
        Matcher m = FILENAME.matcher(genaiJson);
        while (m.find()) {
            files.add(m.group(1));
        }
        for (String name : TOKENIZER_FILES) {
            files.add(name);
        }
        List<String> extras = new ArrayList<>();
        for (String f : files) {
            if (f.endsWith(".onnx")) {
                extras.add(f + ".data");
            }
        }
        files.addAll(extras);
        String prefix = relativeDir == null || relativeDir.isBlank() ? "" : relativeDir + "/";
        for (String file : files) {
            if (file == null || file.isBlank()) {
                continue;
            }
            String simpleName = Path.of(file).getFileName().toString();
            if (Files.isRegularFile(root.resolve(simpleName))) {
                continue;
            }
            // Filenames in genai_config are relative to the package directory.
            String repoPath = file.contains("/") ? file : prefix + file;
            try {
                HuggingFaceHub.download(repoId, repoPath);
            } catch (IOException e) {
                logger.debugf("Optional GenAI companion missing %s/%s: %s",
                        repoId, repoPath, e.getMessage());
            }
        }
    }
}
