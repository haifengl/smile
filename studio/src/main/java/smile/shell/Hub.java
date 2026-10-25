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
 * Starts the always-on Agent Hub &amp; Bot runtime service.
 *
 * @author Haifeng Li
 */
@Command(name = "smile hub", versionProvider = VersionProvider.class,
        description = "Start the always-on Agent Hub and Bot service.",
        mixinStandardHelpOptions = true)
public class Hub implements Callable<Integer> {

    @Option(names = {"--host"}, description = "Network interface the hub binds to (default: localhost).")
    String host = "localhost";

    @Option(names = {"-p", "--port"}, description = "HTTP listen port (default: 8890).")
    int port = 8890;

    @Option(names = {"--profile"}, description = "Quarkus runtime profile: local or server (default: local).")
    String profile = "local";

    @Option(names = {"-w", "--workspace"}, paramLabel = "<path>",
            description = "Bot workspace directory (default: $SMILE_HOME/bot).")
    String workspace;

    @Option(names = {"--admin-token"}, paramLabel = "<token>",
            description = "Admin bearer token for /v1/admin/* endpoints.")
    String adminToken;

    @Option(names = {"--log-level"}, description = "Log level (default: INFO).")
    String logLevel = "INFO";

    @Option(names = {"-D"}, mapFallbackValue = "",
            description = "Set a system property in the hub process (-Dkey=value).")
    Map<String, String> systemProperties = new LinkedHashMap<>();

    @Option(names = {"-J", "--jvm-arg"},
            description = "Pass an option to the JVM (e.g. -J-Xmx4g).")
    List<String> jvmArgs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        String home = System.getProperty("smile.home", ".");
        Path jar = findQuarkusJar(home);
        if (!Files.isRegularFile(jar)) {
            System.err.println("Error: Agent Hub runner JAR not found at " + jar);
            System.err.println("Please build Agent Hub first (e.g. ./gradlew :hub:copyHubToStudio).");
            return 1;
        }

        List<String> command = buildCommand();
        var process = new ProcessBuilder(command).inheritIO().start();
        Thread shutdownHook = new Thread(() -> {
            if (process.isAlive()) {
                try {
                    process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    process.destroy();
                }
            }
        }, "hub-launcher-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }
        try {
            return process.waitFor();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    /**
     * Builds the full command line arguments list to launch the Agent Hub process.
     *
     * @return the command arguments list.
     */
    public List<String> buildCommand() {
        String home = System.getProperty("smile.home", ".");
        Path homePath = Path.of(home).toAbsolutePath().normalize();
        Path quarkusJar = findQuarkusJar(home);

        List<String> command = new ArrayList<>();
        command.add(Serve.javaExecutable());

        command.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        command.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        command.add("--enable-native-access=ALL-UNNAMED");

        if (jvmArgs != null) {
            for (String arg : jvmArgs) {
                command.add(arg.startsWith("-J") ? arg.substring(2) : arg);
            }
        }

        command.add("-Dsmile.home=" + homePath);
        if (profile != null && !profile.isBlank()) {
            command.add("-Dquarkus.profile=" + profile.trim());
        }
        if (host != null && !host.isBlank()) {
            command.add("-Dquarkus.http.host=" + host.trim());
        }
        if (port > 0) {
            command.add("-Dquarkus.http.port=" + port);
        }
        if (logLevel != null && !logLevel.isBlank()) {
            command.add("-Dquarkus.log.level=" + logLevel.trim());
        }

        String resolvedWorkspace = (workspace != null && !workspace.isBlank())
                ? workspace.trim()
                : homePath.resolve("bot").toString();
        command.add("-Dhub.agent.workspace=" + resolvedWorkspace);

        if (adminToken != null && !adminToken.isBlank()) {
            command.add("-Dhub.admin.token=" + adminToken.trim());
        }

        if (systemProperties != null) {
            for (var entry : systemProperties.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    command.add("-D" + entry.getKey());
                } else {
                    command.add("-D" + entry.getKey() + "=" + entry.getValue());
                }
            }
        }

        command.add("-jar");
        command.add(quarkusJar.toString());
        return command;
    }

    /**
     * Locates the Agent Hub Quarkus runner JAR under {@code $SMILE_HOME} or sibling checkouts.
     *
     * @param home the SMILE home directory.
     * @return the resolved JAR path (which may not exist).
     */
    public static Path findQuarkusJar(String home) {
        Path homePath = (home != null && !home.isBlank()) ? Path.of(home) : Path.of(".");
        Path[] candidates = {
                homePath.resolve("hub").resolve("quarkus-run.jar"),
                homePath.resolve("studio").resolve("src").resolve("universal").resolve("hub").resolve("quarkus-run.jar"),
                homePath.resolve("..").resolve("ioa").resolve("hub").resolve("build").resolve("quarkus-app").resolve("quarkus-run.jar"),
                homePath.resolve("..").resolve("..").resolve("ioa").resolve("hub").resolve("build").resolve("quarkus-app").resolve("quarkus-run.jar")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        return homePath.resolve("hub").resolve("quarkus-run.jar").toAbsolutePath().normalize();
    }
}
