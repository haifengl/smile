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
package smile;

import java.nio.file.Path;
import java.util.Arrays;
import picocli.CommandLine;
import smile.shell.*;
import smile.studio.SmileStudio;

/**
 * The entry point of SMILE Shell or Studio.
 *
 * @author Haifeng Li
 */
public class Main {
    public static void main(String[] args) {
        var command = "";
        var options = args;
        if (args.length > 0) {
            command = args[0];
            options = Arrays.copyOfRange(args, 1, args.length);
        }

        switch (command) {
            case "train" -> new CommandLine(new Train()).execute(options);
            case "predict" -> new CommandLine(new Predict()).execute(options);
            case "serve" -> new CommandLine(new Serve()).execute(options);
            case "scala" -> ScalaREPL.start(options);
            case "shell" -> JShell.start(options);
            default -> SmileStudio.start(args);
        }
    }
}
