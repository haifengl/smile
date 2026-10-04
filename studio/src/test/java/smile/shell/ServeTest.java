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

import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code smile serve} CLI command options and command-line assembly.
 *
 * @author Haifeng Li
 */
public class ServeTest {

    @Test
    public void testDefaultBuildCommand() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs(); // parse options without executing call()
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("--add-opens=java.base/java.lang=ALL-UNNAMED"));
        assertTrue(command.contains("--add-opens=java.base/java.nio=ALL-UNNAMED"));
        assertTrue(command.contains("--enable-native-access=ALL-UNNAMED"));
        assertTrue(command.contains("-Dquarkus.http.host=localhost"));
        assertTrue(command.contains("-Dquarkus.http.port=8888"));
        assertTrue(command.contains("-Dquarkus.log.level=INFO"));
        assertTrue(command.contains("-Dsmile.chat.devices=0"));
        assertTrue(command.contains("-Dsmile.chat.kv-cache.dtype=auto"));
        assertTrue(command.contains("-jar"));
    }

    @Test
    public void testClassicModelOption() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs("-m", "iris.sml");
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.serve.model=iris.sml"));
        assertFalse(command.stream().anyMatch(s -> s.startsWith("-Dsmile.onnx.model=")));
        assertFalse(command.stream().anyMatch(s -> s.startsWith("-Dsmile.chat.model=")));
    }

    @Test
    public void testOnnxModelOption() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs("-m", "resnet50.onnx");
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.onnx.model=resnet50.onnx"));
        assertFalse(command.stream().anyMatch(s -> s.startsWith("-Dsmile.serve.model=")));

        Serve serve2 = new Serve();
        new CommandLine(serve2).parseArgs("--onnx-model", "my-graph.onnx");
        List<String> command2 = serve2.buildCommand();
        assertTrue(command2.contains("-Dsmile.onnx.model=my-graph.onnx"));
    }

    @Test
    public void testChatModelOption() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs("--llm", "microsoft/Phi-4-mini-instruct-onnx");
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.chat.model=microsoft/Phi-4-mini-instruct-onnx"));

        Serve serve2 = new Serve();
        new CommandLine(serve2).parseArgs("--chat-model", "Qwen/Qwen3.8-27B");
        List<String> command2 = serve2.buildCommand();
        assertTrue(command2.contains("-Dsmile.chat.model=Qwen/Qwen3.8-27B"));
    }

    @Test
    public void testChatModelViaModelOption() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs("-m", "Qwen/Qwen3.8-27B");
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.chat.model=Qwen/Qwen3.8-27B"));
    }

    @Test
    public void testServerAndNetworkOptions() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs(
                "--host", "127.0.0.1",
                "-p", "9090",
                "--log-level", "DEBUG"
        );
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dquarkus.http.host=127.0.0.1"));
        assertTrue(command.contains("-Dquarkus.http.port=9090"));
        assertTrue(command.contains("-Dquarkus.log.level=DEBUG"));
    }

    @Test
    public void testLlmRuntimeHardwareOptions() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs(
                "--llm", "Qwen/Qwen3.8-27B",
                "--devices", "0,1",
                "--tp-size", "2",
                "--max-batch-size", "32",
                "--max-decode-batch", "16",
                "--max-seq-len", "8192",
                "--prefill-budget", "4096",
                "--model-loader-threads", "4",
                "--mem-fraction-static", "0.75",
                "--attention", "flashinfer",
                "--quantization", "fp8"
        );
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.chat.model=Qwen/Qwen3.8-27B"));
        assertTrue(command.contains("-Dsmile.chat.devices=0,1"));
        assertTrue(command.contains("-Dsmile.chat.tensor-parallel-size=2"));
        assertTrue(command.contains("-Dsmile.chat.max-batch-size=32"));
        assertTrue(command.contains("-Dsmile.chat.max-decode-batch=16"));
        assertTrue(command.contains("-Dsmile.chat.max-seq-len=8192"));
        assertTrue(command.contains("-Dsmile.chat.prefill-token-budget=4096"));
        assertTrue(command.contains("-Dsmile.chat.model-loader-threads=4"));
        assertTrue(command.contains("-Dsmile.chat.mem-fraction-static=0.75"));
        assertTrue(command.contains("-Dsmile.chat.attention-backend=flashinfer"));
        assertTrue(command.contains("-Dsmile.chat.quantization=fp8"));
    }

    @Test
    public void testSpeculativeDecodingOptions() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs(
                "--speculative",
                "--speculative-tokens", "3",
                "--speculative-concurrency", "8"
        );
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.chat.speculative=true"));
        assertTrue(command.contains("-Dsmile.chat.speculative-tokens=3"));
        assertTrue(command.contains("-Dsmile.chat.speculative-max-concurrency=8"));

        Serve serveNoSpec = new Serve();
        new CommandLine(serveNoSpec).parseArgs("--no-speculative");
        List<String> commandNoSpec = serveNoSpec.buildCommand();
        assertTrue(commandNoSpec.contains("-Dsmile.chat.speculative=false"));
    }

    @Test
    public void testKvCacheOptions() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs(
                "--kv-dtype", "fp8_e4m3",
                "--kv-page-size", "32",
                "--no-prefix-reuse"
        );
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.chat.kv-cache.dtype=fp8_e4m3"));
        assertTrue(command.contains("-Dsmile.chat.kv-cache.page-size=32"));
        assertTrue(command.contains("-Dsmile.chat.kv-cache.prefix-reuse=false"));

        Serve serveAuto = new Serve();
        new CommandLine(serveAuto).parseArgs("--kv-dtype", "auto");
        List<String> commandAuto = serveAuto.buildCommand();
        assertTrue(commandAuto.contains("-Dsmile.chat.kv-cache.dtype=auto"));
    }

    @Test
    public void testOgaOptions() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs(
                "--oga",
                "--oga-provider", "dml",
                "--oga-precision", "int4"
        );
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.chat.oga.enabled=true"));
        assertTrue(command.contains("-Dsmile.chat.oga.provider=dml"));
        assertTrue(command.contains("-Dsmile.chat.oga.precision=int4"));

        Serve serveNoOga = new Serve();
        new CommandLine(serveNoOga).parseArgs("--no-oga");
        List<String> commandNoOga = serveNoOga.buildCommand();
        assertTrue(commandNoOga.contains("-Dsmile.chat.oga.enabled=false"));
    }

    @Test
    public void testDatabaseAndStorageOptions() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs(
                "--db-url", "jdbc:postgresql://localhost:5432/smile_serve",
                "--db-kind", "postgresql",
                "--blob-path", "/data/blob"
        );
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/smile_serve"));
        assertTrue(command.contains("-Dquarkus.datasource.db-kind=postgresql"));
        assertTrue(command.contains("-Dsmile.blob.local.path=/data/blob"));
    }

    @Test
    public void testCustomSystemPropertiesAndJvmArgs() {
        Serve serve = new Serve();
        new CommandLine(serve).parseArgs(
                "-Dsmile.auth.google.client-id=my-id",
                "-Dcustom.flag=hello",
                "-J-Xmx16g",
                "--jvm-arg", "-Xms4g"
        );
        List<String> command = serve.buildCommand();
        assertTrue(command.contains("-Dsmile.auth.google.client-id=my-id"));
        assertTrue(command.contains("-Dcustom.flag=hello"));
        assertTrue(command.contains("-Xmx16g"));
        assertTrue(command.contains("-Xms4g"));
    }

    @Test
    public void testMissingJarReportsError() throws Exception {
        Serve serve = new Serve();
        // Point smile.home to a temporary empty directory
        String prevHome = System.getProperty("smile.home");
        try {
            System.setProperty("smile.home", "non-existent-home-folder-12345");
            int exitCode = serve.call();
            assertEquals(1, exitCode);
        } finally {
            if (prevHome != null) {
                System.setProperty("smile.home", prevHome);
            } else {
                System.clearProperty("smile.home");
            }
        }
    }
}
