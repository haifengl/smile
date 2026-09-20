/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import smile.io.CacheFiles;
import smile.onnx.genai.GenAI;
import smile.onnx.genai.GenAIOliveTarget;

/**
 * Resolves or builds an ORT GenAI model directory via Olive {@code auto-opt}.
 *
 * <p>Never writes into the Hugging Face hub cache. Cache hits skip the CLI.
 *
 * @author Haifeng Li
 */
public final class OliveAutoOpt {
    private static final Logger logger = Logger.getLogger(OliveAutoOpt.class);

    private OliveAutoOpt() {}

    /**
     * Returns whether the Olive CLI appears to be on {@code PATH}.
     *
     * @param oliveCommand configured command (e.g. {@code olive}).
     * @return {@code true} when a version probe succeeds.
     */
    public static boolean isAvailable(String oliveCommand) {
        String cmd = oliveCommand == null || oliveCommand.isBlank() ? "olive" : oliveCommand.trim();
        try {
            Process p = new ProcessBuilder(cmd, "--help")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves a GenAI model directory from Olive cache or conversion.
     *
     * @param modelSpec HF id or local HF layout path.
     * @param oga       OGA config.
     * @return directory containing {@code genai_config.json}.
     * @throws IOException if conversion fails or Olive is missing.
     */
    public static Path resolveOrConvert(String modelSpec, OgaChatConfig oga) throws IOException {
        GenAIOliveTarget cascade = GenAI.resolveOliveTarget();
        String device = oga.device().filter(s -> !s.isBlank()).orElse(cascade.device());
        String provider = oga.provider().filter(s -> !s.isBlank()).orElse(cascade.provider());
        String candidateId = cascade.candidateId();
        if (oga.device().isPresent() || oga.provider().isPresent()) {
            candidateId = candidateId + "-override";
        }

        String precision = resolvePrecision(oga, cascade);
        Path cacheRoot = cacheRoot(oga);
        Path outDir = cacheRoot.resolve(sanitize(modelSpec)).resolve(candidateId + "-" + precision);
        Optional<Path> hit = findGenAiRoot(outDir);
        if (hit.isPresent()) {
            logger.infof("Olive cache hit: %s", hit.get());
            return hit.get();
        }

        if (!oga.enabled()) {
            throw new IOException("smile.chat.oga.enabled=false; cannot convert " + modelSpec);
        }
        String oliveCmd = oga.oliveCommand();
        if (!isAvailable(oliveCmd)) {
            throw new IOException("Olive CLI not found (" + oliveCmd
                    + "). Install with: pip install olive-ai");
        }

        Files.createDirectories(outDir);
        try {
            runOlive(oliveCmd, modelSpec, outDir, precision, device, provider);
        } catch (IOException e) {
            if (cascade.prefersFp8() && "fp8".equalsIgnoreCase(precision)
                    && oga.precision().filter(s -> !s.isBlank() && !"auto".equalsIgnoreCase(s)).isEmpty()) {
                logger.warnf(e, "Olive fp8 failed for %s; retrying with int4", modelSpec);
                precision = "int4";
                outDir = cacheRoot.resolve(sanitize(modelSpec)).resolve(candidateId + "-" + precision);
                hit = findGenAiRoot(outDir);
                if (hit.isPresent()) {
                    return hit.get();
                }
                Files.createDirectories(outDir);
                runOlive(oliveCmd, modelSpec, outDir, precision, device, provider);
            } else {
                throw e;
            }
        }

        Path finalOut = outDir;
        return findGenAiRoot(finalOut).orElseThrow(() -> new IOException(
                "Olive finished but genai_config.json not found under " + finalOut));
    }

    static String resolvePrecision(OgaChatConfig oga, GenAIOliveTarget cascade) {
        Optional<String> override = oga.precision().filter(s -> !s.isBlank());
        if (override.isPresent()) {
            String p = override.get().trim().toLowerCase(Locale.ROOT);
            if ("auto".equals(p)) {
                return cascade.defaultPrecision();
            }
            return p;
        }
        return cascade.defaultPrecision();
    }

    static Path cacheRoot(OgaChatConfig oga) {
        return oga.cacheDir()
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .orElseGet(() -> Path.of(CacheFiles.dir(), "olive"));
    }

    static String sanitize(String modelSpec) {
        return modelSpec.trim()
                .replace('\\', '/')
                .replace('/', '_')
                .replace(':', '_')
                .replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    /**
     * Finds a directory containing {@code genai_config.json} under {@code root}.
     *
     * @param root cache or model root.
     * @return GenAI model directory.
     */
    public static Optional<Path> findGenAiRoot(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return Optional.empty();
        }
        Path direct = root.resolve("genai_config.json");
        if (Files.isRegularFile(direct)) {
            return Optional.of(root);
        }
        Path nested = root.resolve("model").resolve("genai_config.json");
        if (Files.isRegularFile(nested)) {
            return Optional.of(root.resolve("model"));
        }
        try (Stream<Path> walk = Files.walk(root, 4)) {
            return walk
                    .filter(p -> p.getFileName() != null
                            && "genai_config.json".equals(p.getFileName().toString()))
                    .map(Path::getParent)
                    .min(Comparator.comparingInt(p -> p.getNameCount()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void runOlive(String oliveCmd, String modelSpec, Path outDir,
                                 String precision, String device, String provider)
            throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(oliveCmd);
        cmd.add("auto-opt");
        cmd.add("--model_name_or_path");
        cmd.add(modelSpec);
        cmd.add("--output_path");
        cmd.add(outDir.toAbsolutePath().toString());
        cmd.add("--precision");
        cmd.add(precision);
        cmd.add("--device");
        cmd.add(device);
        cmd.add("--provider");
        cmd.add(provider);
        cmd.add("--use_ort_genai");
        logger.infof("Running Olive: %s", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append('\n');
                logger.info(line);
            }
        }
        int code;
        try {
            code = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Olive interrupted", e);
        }
        if (code != 0) {
            throw new IOException("Olive exited with code " + code + ":\n" + log);
        }
    }
}
