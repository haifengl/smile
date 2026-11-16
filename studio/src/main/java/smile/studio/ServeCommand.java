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
package smile.studio;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import smile.shell.Serve;

/**
 * Builds the command line that launches the local SMILE Serve child process.
 *
 * <p>Split out of {@link ServeManager} so the JVM flags, the native-library
 * forwarding, and the Quarkus properties live in one place with no state besides
 * its inputs.
 *
 * @author Haifeng Li
 */
final class ServeCommand {

    private ServeCommand() {
    }

    /**
     * Builds the launch command.
     *
     * <p>No {@code -Dsmile.serve.model} is passed: the service starts empty and
     * models are loaded on demand through the REST API, plus
     * {@code -Dsmile.chat.model} when an LLM is configured.
     * {@code smile.serve.allow-empty=true} is required, otherwise the service
     * exits at startup before the first load request can arrive.
     *
     * @param jar  the Quarkus runner JAR.
     * @param host the bind host.
     * @param port the HTTP port.
     * @param llm  the configured LLM, possibly empty.
     * @return the full command line.
     */
    static List<String> build(Path jar, String host, int port, String llm) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Serve.javaExecutable());
        cmd.add("--add-opens");
        cmd.add("java.base/java.lang=ALL-UNNAMED");
        cmd.add("--add-opens");
        cmd.add("java.base/java.nio=ALL-UNNAMED");
        cmd.add("--enable-native-access");
        cmd.add("ALL-UNNAMED");

        forward("onnxruntime.native.path", cmd);
        forward("onnxruntime-genai.native.path", cmd);

        cmd.add("-Dsmile.serve.allow-empty=true");
        if (llm != null && !llm.isEmpty()) {
            cmd.add("-Dsmile.chat.model=" + llm);
        }
        cmd.add("-Dquarkus.http.host=" + host);
        cmd.add("-Dquarkus.http.port=" + port);
        cmd.add("-jar");
        cmd.add(jar.toString());
        return cmd;
    }

    /**
     * Forwards a native library path from the Studio JVM to the child, so an
     * out-of-tree ORT/GenAI install keeps working.
     *
     * @param property the system property name.
     * @param cmd      the command being built.
     */
    private static void forward(String property, List<String> cmd) {
        String value = System.getProperty(property);
        if (value != null && !value.isBlank()) {
            cmd.add("-D" + property + "=" + value);
        }
    }
}
