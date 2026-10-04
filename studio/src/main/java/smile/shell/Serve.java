/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Starts web service for online prediction and LLM chat.
 *
 * @author Haifeng Li
 */
@Command(name = "smile serve", versionProvider = VersionProvider.class,
        description = "Start web service for online prediction and LLM chat.",
        mixinStandardHelpOptions = true)
public class Serve implements Callable<Integer> {

    // -------------------------------------------------------------------------
    // Model options
    // -------------------------------------------------------------------------
    @Option(names = {"-m", "--model"}, paramLabel = "<path>",
            description = "Model file or directory (loads .sml models, and .onnx if present; or HF repo ID).")
    String model;

    @Option(names = {"--sml-model"}, paramLabel = "<path>",
            description = "SMILE ML model (.sml) file or directory (smile.serve.model).")
    String smlModel;

    @Option(names = {"--onnx-model"}, paramLabel = "<path>",
            description = "ONNX model (.onnx) file or directory (smile.onnx.model).")
    String onnxModel;

    @Option(names = {"--chat-model", "--llm"}, paramLabel = "<model>",
            description = "LLM chat model directory or Hugging Face repo ID (smile.chat.model).")
    String chatModel;

    // -------------------------------------------------------------------------
    // Server & network options
    // -------------------------------------------------------------------------
    @Option(names = {"--host"}, description = "Network interface the server binds to (default: localhost).")
    String host = "localhost";

    @Option(names = {"-p", "--port"}, description = "HTTP listen port (default: 8888).")
    int port = 8888;

    @Option(names = {"--log-level"}, description = "Log level (default: INFO).")
    String logLevel = "INFO";

    // -------------------------------------------------------------------------
    // LLM / Chat execution and hardware knobs (smile.chat.*)
    // -------------------------------------------------------------------------
    @Option(names = {"--devices"}, description = "CUDA device index or comma-separated TP list (default: 0).")
    String devices = "0";

    @Option(names = {"--tp-size", "--tensor-parallel-size"},
            description = "Tensor-parallel world size (default: 1).")
    Integer tensorParallelSize;

    @Option(names = {"--max-batch-size"},
            description = "Maximum in-flight chat generations for continuous batching (default: 16).")
    Integer maxBatchSize;

    @Option(names = {"--max-decode-batch"},
            description = "Cap on requests per GPU decode step (0 = same as max-batch-size).")
    Integer maxDecodeBatch;

    @Option(names = {"--max-seq-len"},
            description = "Maximum sequence context length in tokens (0 = auto from model config).")
    Integer maxSeqLen;

    @Option(names = {"--prefill-budget"},
            description = "Max prompt tokens prefilled per scheduler tick (default: 2048).")
    Integer prefillBudget;

    @Option(names = {"--mem-fraction-static"},
            description = "Fraction of GPU memory for static weights + DeltaNet + KV cache (default: 0.85).")
    Double memFractionStatic;

    @Option(names = {"--attention", "--attention-backend"},
            description = "Attention kernel backend: flashinfer or torch_native.")
    String attentionBackend = "flashinfer";

    @Option(names = {"--quantization"},
            description = "Weight GEMM quantization: auto, dense, fp8, nvfp4, or marlin.")
    String quantization = "auto";

    @Option(names = {"--speculative"}, negatable = true,
            description = "Enable or disable native MTP speculative decoding (default: true).")
    Boolean speculative;

    @Option(names = {"--speculative-tokens"},
            description = "Draft depth for MTP speculative decoding (0 = model default).")
    Integer speculativeTokens;

    @Option(names = {"--speculative-concurrency"},
            description = "Maximum concurrent requests that speculate at once (0 = unlimited).")
    Integer speculativeConcurrency;

    // -------------------------------------------------------------------------
    // KV Cache options (smile.chat.kv-cache.*)
    // -------------------------------------------------------------------------
    @Option(names = {"--kv-dtype"},
            description = "KV-cache element dtype: auto, bfloat16, float16, float32, fp8_e4m3, fp8_e5m2 (default: auto).")
    String kvDtype = "auto";

    @Option(names = {"--kv-page-size"},
            description = "Tokens per KV pool page (default: 16).")
    Integer kvPageSize;

    @Option(names = {"--prefix-reuse"}, negatable = true,
            description = "Enable or disable radix KV prefix reuse (default: true).")
    Boolean prefixReuse;

    // -------------------------------------------------------------------------
    // ONNX Runtime GenAI (OGA) fallback (smile.chat.oga.*)
    // -------------------------------------------------------------------------
    @Option(names = {"--oga"}, negatable = true,
            description = "Enable or disable ONNX Runtime GenAI fallback (default: true).")
    Boolean oga;

    @Option(names = {"--oga-provider"},
            description = "GenAI execution provider: auto, cuda, dml, cpu, npu, ryzenai, openvino, qnn.")
    String ogaProvider = "auto";

    @Option(names = {"--oga-precision"},
            description = "Olive/OGA precision override (e.g. int4, auto).")
    String ogaPrecision = "auto";

    // -------------------------------------------------------------------------
    // Database & storage options
    // -------------------------------------------------------------------------
    @Option(names = {"--db-url"},
            description = "JDBC connection URL for conversation history database.")
    String dbUrl;

    @Option(names = {"--db-kind"},
            description = "Database backend kind (h2 or postgresql).")
    String dbKind;

    @Option(names = {"--blob-path"},
            description = "Local directory for multimedia blob storage.")
    String blobPath;

    // -------------------------------------------------------------------------
    // Generic passthrough options
    // -------------------------------------------------------------------------
    @Option(names = {"-D"}, mapFallbackValue = "",
            description = "Set a system property in the server process (-Dkey=value).")
    Map<String, String> systemProperties = new LinkedHashMap<>();

    @Option(names = {"-J", "--jvm-arg"},
            description = "Pass an option to the JVM (e.g. -J-Xmx16g).")
    List<String> jvmArgs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        String home = System.getProperty("smile.home", ".");
        Path jar = findQuarkusJar(home);
        if (!Files.isRegularFile(jar)) {
            System.err.println("Error: SMILE Serve runner JAR not found at " + jar);
            System.err.println("Please build SMILE Serve first (e.g. ./gradlew :serve:build).");
            return 1;
        }

        List<String> command = buildCommand();
        var process = new ProcessBuilder(command).inheritIO().start();
        return process.waitFor();
    }

    /**
     * Builds the full command line arguments list to launch the server process.
     */
    List<String> buildCommand() {
        String home = System.getProperty("smile.home", ".");
        Path quarkusJar = findQuarkusJar(home);

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());

        // Mandatory JVM flags required for Panama FFM and native access
        command.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        command.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        command.add("--enable-native-access=ALL-UNNAMED");

        // Forward native library paths if configured in parent JVM
        String ortProp = System.getProperty("onnxruntime.native.path");
        if (ortProp != null && !ortProp.isBlank() && !systemProperties.containsKey("onnxruntime.native.path")) {
            command.add("-Donnxruntime.native.path=" + ortProp);
        }
        String genaiProp = System.getProperty("onnxruntime-genai.native.path");
        if (genaiProp != null && !genaiProp.isBlank() && !systemProperties.containsKey("onnxruntime-genai.native.path")) {
            command.add("-Donnxruntime-genai.native.path=" + genaiProp);
        }

        // Custom JVM arguments (-J)
        if (jvmArgs != null) {
            for (String arg : jvmArgs) {
                command.add(arg.startsWith("-J") ? arg.substring(2) : arg);
            }
        }

        // Server & Network
        if (host != null && !host.isBlank()) {
            command.add("-Dquarkus.http.host=" + host);
        }
        if (port > 0) {
            command.add("-Dquarkus.http.port=" + port);
        }
        if (logLevel != null && !logLevel.isBlank()) {
            command.add("-Dquarkus.log.level=" + logLevel);
        }

        // Model bindings
        String resolvedSml = smlModel;
        String resolvedOnnx = onnxModel;
        String resolvedChat = chatModel;

        if (model != null && !model.isBlank()) {
            Path p = Path.of(model);
            if (model.endsWith(".onnx")) {
                if (resolvedOnnx == null) resolvedOnnx = model;
            } else if (model.endsWith(".sml")) {
                if (resolvedSml == null) resolvedSml = model;
            } else if (!Files.exists(p) && model.contains("/")) {
                // Hugging Face repository ID (e.g. microsoft/Phi-3-mini-4k-instruct-onnx)
                if (resolvedChat == null) resolvedChat = model;
            } else if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("genai_config.json"))) {
                // GenAI model directory
                if (resolvedChat == null) resolvedChat = model;
            } else {
                if (resolvedSml == null) resolvedSml = model;
                if (resolvedOnnx == null) resolvedOnnx = model;
            }
        }

        if (resolvedSml != null && !resolvedSml.isBlank()) {
            command.add("-Dsmile.serve.model=" + resolvedSml);
        }
        if (resolvedOnnx != null && !resolvedOnnx.isBlank()) {
            command.add("-Dsmile.onnx.model=" + resolvedOnnx);
        }
        if (resolvedChat != null && !resolvedChat.isBlank()) {
            command.add("-Dsmile.chat.model=" + resolvedChat);
        }

        // Chat & Hardware Execution
        if (devices != null && !devices.isBlank()) {
            command.add("-Dsmile.chat.devices=" + devices);
        }
        if (tensorParallelSize != null && tensorParallelSize > 0) {
            command.add("-Dsmile.chat.tensor-parallel-size=" + tensorParallelSize);
        }
        if (maxBatchSize != null && maxBatchSize > 0) {
            command.add("-Dsmile.chat.max-batch-size=" + maxBatchSize);
        }
        if (maxDecodeBatch != null && maxDecodeBatch >= 0) {
            command.add("-Dsmile.chat.max-decode-batch=" + maxDecodeBatch);
        }
        if (maxSeqLen != null && maxSeqLen >= 0) {
            command.add("-Dsmile.chat.max-seq-len=" + maxSeqLen);
        }
        if (prefillBudget != null && prefillBudget > 0) {
            command.add("-Dsmile.chat.prefill-token-budget=" + prefillBudget);
        }
        if (memFractionStatic != null && memFractionStatic > 0) {
            command.add("-Dsmile.chat.mem-fraction-static=" + memFractionStatic);
        }
        if (attentionBackend != null && !attentionBackend.isBlank()) {
            command.add("-Dsmile.chat.attention-backend=" + attentionBackend);
        }
        if (quantization != null && !quantization.isBlank()) {
            command.add("-Dsmile.chat.quantization=" + quantization);
        }
        if (speculative != null) {
            command.add("-Dsmile.chat.speculative=" + speculative);
        }
        if (speculativeTokens != null && speculativeTokens >= 0) {
            command.add("-Dsmile.chat.speculative-tokens=" + speculativeTokens);
        }
        if (speculativeConcurrency != null && speculativeConcurrency >= 0) {
            command.add("-Dsmile.chat.speculative-max-concurrency=" + speculativeConcurrency);
        }

        // KV Cache
        if (kvDtype != null && !kvDtype.isBlank()) {
            command.add("-Dsmile.chat.kv-cache.dtype=" + kvDtype);
        }
        if (kvPageSize != null && kvPageSize > 0) {
            command.add("-Dsmile.chat.kv-cache.page-size=" + kvPageSize);
        }
        if (prefixReuse != null) {
            command.add("-Dsmile.chat.kv-cache.prefix-reuse=" + prefixReuse);
        }

        // ONNX Runtime GenAI fallback
        if (oga != null) {
            command.add("-Dsmile.chat.oga.enabled=" + oga);
        }
        if (ogaProvider != null && !ogaProvider.isBlank()) {
            command.add("-Dsmile.chat.oga.provider=" + ogaProvider);
        }
        if (ogaPrecision != null && !ogaPrecision.isBlank()) {
            command.add("-Dsmile.chat.oga.precision=" + ogaPrecision);
        }

        // Database & Storage
        if (dbUrl != null && !dbUrl.isBlank()) {
            command.add("-Dquarkus.datasource.jdbc.url=" + dbUrl);
        }
        if (dbKind != null && !dbKind.isBlank()) {
            command.add("-Dquarkus.datasource.db-kind=" + dbKind);
        }
        if (blobPath != null && !blobPath.isBlank()) {
            command.add("-Dsmile.blob.local.path=" + blobPath);
        }

        // Custom system properties (-D)
        if (systemProperties != null) {
            for (var entry : systemProperties.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    command.add("-D" + entry.getKey());
                } else {
                    command.add("-D" + entry.getKey() + "=" + entry.getValue());
                }
            }
        }

        // Entry point JAR
        command.add("-jar");
        command.add(quarkusJar.toString());

        return command;
    }

    static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            Path javaBin = Path.of(javaHome, "bin", isWindows ? "java.exe" : "java");
            if (Files.isExecutable(javaBin)) {
                return javaBin.toString();
            }
        }
        return "java";
    }

    static Path findQuarkusJar(String home) {
        Path homePath = (home != null && !home.isBlank()) ? Path.of(home) : Path.of(".");
        Path[] candidates = {
                homePath.resolve("serve").resolve("quarkus-run.jar"),
                homePath.resolve("serve").resolve("build").resolve("quarkus-app").resolve("quarkus-run.jar"),
                homePath.resolve("build").resolve("quarkus-app").resolve("quarkus-run.jar")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        return homePath.resolve("serve").resolve("quarkus-run.jar").toAbsolutePath().normalize();
    }
}
