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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Scala REPL launcher.
 *
 * @author Haifeng Li
 */
public interface ScalaREPL {
    /**
     * Starts a Scala REPL session.
     * @param args the command-line arguments.
     */
    static void start(String[] args) {
        String home = System.getProperty("smile.home", ".");
        String[] startup = {
                "-usejavacp",
                "-repl-init-script", ":load " + home + "/bin/predef.sc"
        };

        List<String> list = new ArrayList<>(startup.length + args.length);
        Collections.addAll(list, startup);
        Collections.addAll(list, args);

        dotty.tools.repl.Main.main(list.toArray(String[]::new));
    }
}
