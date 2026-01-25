/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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
                Path.of(home, "serve", "quarkus-run.jar").normalize().toString())
                .inheritIO().start();
        return process.waitFor();
    }
}
