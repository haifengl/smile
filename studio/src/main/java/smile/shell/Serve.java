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

import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Starts web service for online prediction.
 *
 * @author Haifeng Li
 */
@Command(name = "smile serve", versionProvider = VersionProvider.class,
        description = "Start web service for online prediction.",
        mixinStandardHelpOptions = true)
public class Serve implements Callable<Integer> {
    @Option(names = {"--model"}, required = true, paramLabel = "<path>", description = "The model file/folder.")
    private String model;
    @Option(names = {"--host"}, description = "The network interface the server binds to.")
    private String host = "0.0.0.0";
    @Option(names = {"--port"}, description = "The port for the HTTP server.")
    private int port = 8080;

    @Override
    public Integer call() throws Exception {
        String home = System.getProperty("smile.home", ".");
        var process = new ProcessBuilder("java",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/java.nio=ALL-UNNAMED",
                "--enable-native-access", "ALL-UNNAMED",
                "-Dsmile.serve.model=" + model,
                "-Dquarkus.http.host=" + host,
                "-Dquarkus.http.port=" + port,
                "-jar",
                Path.of(home, "serve", "quarkus-run.jar").normalize().toString()
        ).inheritIO().start();
        return process.waitFor();
    }
}
