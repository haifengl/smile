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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import smile.io.CacheFiles;
import smile.onnx.genai.GenAI;
import smile.onnx.genai.GenAIOliveTarget;

/**
 * Facade over the Olive CLI ({@code olive …}) for smile-serve chat.
 *
 * <p>Today this drives {@code olive optimize} to produce ORT GenAI model
 * directories. Olive is launched via a small Python bootstrap that only
 * registers ORT EPs Olive explicitly requested (avoids Windows TensorRT
 * registration crashes when {@code nvinfer} is not installed).
 *
 * <p>Never writes into the Hugging Face hub cache. Cache hits skip the CLI.
 *
 * @author Haifeng Li
 */
public final class Olive {
    private static final Logger logger = Logger.getLogger(Olive.class);
    private static final String BOOTSTRAP_RESOURCE = "/smile/chat/olive_cli.py";

    /** Precisions accepted by {@code olive optimize --precision}. */
    private static final Set<String> OPTIMIZE_PRECISIONS = Set.of(
            "int4", "int8", "int16", "int32",
            "uint4", "uint8", "uint16", "uint32",
            "fp16", "fp32", "bf16");

    /** Providers accepted by {@code olive optimize --provider}. */
    private static final Set<String> OPTIMIZE_PROVIDERS = Set.of(
            "CPUExecutionProvider",
            "CUDAExecutionProvider",
            "QNNExecutionProvider",
            "VitisAIExecutionProvider",
            "OpenVINOExecutionProvider",
            "WebGpuExecutionProvider",
            "NvTensorRTRTXExecutionProvider");

    private Olive() {}

    /**
     * Returns whether Olive is importable from a Python interpreter on
     * {@code PATH} (preferred), or the configured Olive executable responds.
     *
     * @param oliveCommand configured command (e.g. {@code olive}).
     * @return {@code true} when Olive can be launched.
     */
    public static boolean isAvailable(String oliveCommand) {
        if (resolvePythonWithOlive().isPresent()) {
            return true;
        }
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
     * Resolves a GenAI model directory from Olive cache or {@code optimize}.
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

        String precision = clampPrecision(resolvePrecision(oga, cascade));
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
            optimize(oliveCmd, modelSpec, outDir, precision, device, provider);
            Path finalOut = outDir;
            return findGenAiRoot(finalOut).orElseThrow(() -> new IOException(
                    "Olive finished but genai_config.json not found under " + finalOut));
        } catch (IOException e) {
            String clamped = clampProvider(provider);
            if ("CPUExecutionProvider".equals(clamped) || !isOrtEpLoadFailure(e)) {
                throw e;
            }
            logger.warnf(e,
                    "Olive with %s failed (ORT EP registration); retrying with CPUExecutionProvider",
                    clamped);
            Path cpuOut = cacheRoot.resolve(sanitize(modelSpec)).resolve("cpu-" + precision);
            Optional<Path> cpuHit = findGenAiRoot(cpuOut);
            if (cpuHit.isPresent()) {
                logger.infof("Olive CPU cache hit: %s", cpuHit.get());
                return cpuHit.get();
            }
            Files.createDirectories(cpuOut);
            optimize(oliveCmd, modelSpec, cpuOut, precision, "cpu", "CPUExecutionProvider");
            Path finalCpu = cpuOut;
            return findGenAiRoot(finalCpu).orElseThrow(() -> new IOException(
                    "Olive finished but genai_config.json not found under " + finalCpu));
        }
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

    /**
     * Runs {@code olive optimize} for an ORT GenAI-ready package.
     */
    private static void optimize(String oliveCmd, String modelSpec, Path outDir,
                                 String precision, String device, String provider)
            throws IOException {
        String oliveProvider = clampProvider(provider);
        String oliveDevice = clampDevice(device, oliveProvider);
        List<String> cmd = buildOptimizeCommand(
                oliveCmd, modelSpec, outDir, precision, oliveDevice, oliveProvider);
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

    /**
     * Builds the process argv: prefer {@code python olive_cli.py …} so EP
     * registration is patched; fall back to the raw {@code olive} executable.
     */
    static List<String> buildOptimizeCommand(String oliveCmd, String modelSpec, Path outDir,
                                             String precision, String device, String provider)
            throws IOException {
        List<String> args = new ArrayList<>();
        args.add("optimize");
        args.add("--model_name_or_path");
        args.add(modelSpec);
        args.add("--output_path");
        args.add(outDir.toAbsolutePath().toString());
        args.add("--precision");
        args.add(precision);
        args.add("--device");
        args.add(device);
        args.add("--provider");
        args.add(provider);
        args.add("--exporter");
        args.add("model_builder");

        Optional<String> python = resolvePythonWithOlive();
        if (python.isPresent()) {
            List<String> cmd = new ArrayList<>();
            String py = python.get();
            cmd.add(py);
            if ("py".equals(py)) {
                cmd.add("-3");
            }
            cmd.add(materializeBootstrap().toAbsolutePath().toString());
            cmd.addAll(args);
            return cmd;
        }
        String cmdName = oliveCmd == null || oliveCmd.isBlank() ? "olive" : oliveCmd.trim();
        List<String> cmd = new ArrayList<>();
        cmd.add(cmdName);
        cmd.addAll(args);
        return cmd;
    }

    /** Cached result of {@link #resolvePythonWithOlive()} ({@code null} until probed). */
    private static volatile Optional<String> cachedPythonWithOlive;

    /**
     * Finds a Python interpreter that can {@code import olive}.
     * Result is cached for the JVM lifetime (probes spawn processes).
     */
    static Optional<String> resolvePythonWithOlive() {
        Optional<String> cached = cachedPythonWithOlive;
        if (cached != null) {
            return cached;
        }
        synchronized (Olive.class) {
            if (cachedPythonWithOlive != null) {
                return cachedPythonWithOlive;
            }
            cachedPythonWithOlive = probePythonWithOlive();
            return cachedPythonWithOlive;
        }
    }

    private static Optional<String> probePythonWithOlive() {
        String override = System.getenv("SMILE_OLIVE_PYTHON");
        List<String> candidates = new ArrayList<>();
        if (override != null && !override.isBlank()) {
            candidates.add(override.trim());
        }
        candidates.add("python");
        candidates.add("py");
        for (String candidate : candidates) {
            try {
                ProcessBuilder pb = "py".equals(candidate)
                        ? new ProcessBuilder(candidate, "-3", "-c", "import olive")
                        : new ProcessBuilder(candidate, "-c", "import olive");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean finished = p.waitFor(20, TimeUnit.SECONDS);
                if (!finished) {
                    p.destroyForcibly();
                    continue;
                }
                if (p.exitValue() == 0) {
                    return Optional.of(candidate);
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return Optional.empty();
    }

    /**
     * Copies the bootstrap script from the classpath into the Olive cache dir.
     */
    static Path materializeBootstrap() throws IOException {
        Path dest = Path.of(CacheFiles.dir(), "olive", "olive_cli.py");
        Files.createDirectories(dest.getParent());
        try (InputStream in = Olive.class.getResourceAsStream(BOOTSTRAP_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing classpath resource " + BOOTSTRAP_RESOURCE);
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return dest;
    }

    /**
     * Returns whether Olive failed while loading an ORT execution-provider DLL
     * (e.g. TensorRT without {@code nvinfer_*.dll} on PATH).
     *
     * @param error failure from {@link #optimize}.
     * @return {@code true} when a CPU retry is appropriate.
     */
    static boolean isOrtEpLoadFailure(Throwable error) {
        if (error == null) {
            return false;
        }
        String msg = error.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("nvinfer")
                || lower.contains("tensorrt")
                || lower.contains("register_execution_provider_library")
                || lower.contains("onnxruntime_providers_tensorrt")
                || (lower.contains("error loading") && lower.contains("onnxruntime_providers_"));
    }

    /**
     * Maps cascade precision onto values accepted by {@code olive optimize}.
     * (Legacy {@code auto-opt} allowed {@code fp8}; {@code optimize} does not.)
     */
    static String clampPrecision(String precision) {
        if (precision == null || precision.isBlank()) {
            return "int4";
        }
        String p = precision.trim().toLowerCase(Locale.ROOT);
        if (OPTIMIZE_PRECISIONS.contains(p)) {
            return p;
        }
        logger.warnf("Olive optimize does not support precision '%s'; using int4", precision);
        return "int4";
    }

    /**
     * Maps cascade providers onto values accepted by {@code olive optimize}.
     * DirectML is not in the optimize EP list — fall back to CPU for conversion.
     */
    static String clampProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "CPUExecutionProvider";
        }
        String p = provider.trim();
        if (OPTIMIZE_PROVIDERS.contains(p)) {
            return p;
        }
        logger.warnf("Olive optimize does not support provider '%s'; using CPUExecutionProvider",
                provider);
        return "CPUExecutionProvider";
    }

    static String clampDevice(String device, String provider) {
        if ("CPUExecutionProvider".equals(provider)) {
            return "cpu";
        }
        if ("CUDAExecutionProvider".equals(provider)
                || "NvTensorRTRTXExecutionProvider".equals(provider)
                || "WebGpuExecutionProvider".equals(provider)) {
            return "gpu";
        }
        if ("QNNExecutionProvider".equals(provider)
                || "OpenVINOExecutionProvider".equals(provider)
                || "VitisAIExecutionProvider".equals(provider)) {
            return "npu";
        }
        if (device != null && !device.isBlank()) {
            String d = device.trim().toLowerCase(Locale.ROOT);
            if (d.equals("cpu") || d.equals("gpu") || d.equals("npu")) {
                return d;
            }
        }
        return "cpu";
    }
}
