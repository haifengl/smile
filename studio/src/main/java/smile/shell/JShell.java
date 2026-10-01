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
import java.util.prefs.Preferences;
import jdk.jshell.tool.JavaShellToolBuilder;

/**
 * The JShell launcher.
 *
 * @author Haifeng Li
 */
public interface JShell {
    String version = JShell.class.getPackage().getImplementationVersion();
    String logo = """
                                                                  ..::''''::..
                                                                .;''        ``;.
                ....                                           ::    ::  ::    ::
              ,;' .;:                ()  ..:                  ::     ::  ::     ::
              ::.      ..:,:;.,:;.    .   ::   .::::.         :: .:' ::  :: `:. ::
               '''::,   ::  ::  ::  `::   ::  ;:   .::        ::  :          :  ::
             ,:';  ::;  ::  ::  ::   ::   ::  ::,::''.         :: `:.      .:' ::
             `:,,,,;;' ,;; ,;;, ;;, ,;;, ,;;, `:,,,,:'          `;..``::::''..;'
                                                                  ``::,,,,::''
          """;

    /**
     * Launch an instance of a Java shell tool.
     * @param args the command-line arguments.
     * @return the exit status with which the tool explicitly exited (if any),
     *         otherwise 0 for success or 1 for failure.
     */
    static int start(String[] args) {
        String home = System.getProperty("smile.home", ".");
        String[] startup = {
                "--class-path", System.getProperty("java.class.path"),
                "-R-XX:MaxMetaspaceSize=1024M",
                "-R-Xss4M",
                "-R-XX:MaxRAMPercentage=75",
                "-R-XX:+UseZGC",
                "-R--add-opens=java.base/java.nio=ALL-UNNAMED",
                "-R--enable-native-access=ALL-UNNAMED",
                "-R-Dsmile.home=" + home,
                "--startup", "DEFAULT",
                "--startup", "PRINTING",
                "--startup", home + "/bin/predef.jsh",
                "--feedback", "smile"
        };

        List<String> list = new ArrayList<>(startup.length + args.length);
        Collections.addAll(list, startup);
        Collections.addAll(list, args);

        try {
            return JavaShellToolBuilder.builder()
                    .interactiveTerminal(true)
                    .persistence(Preferences.userNodeForPackage(JShell.class))
                    .start(list.toArray(String[]::new));
        } catch (Exception ex) {
            System.err.println(ex.getClass().getName() + ": " + ex.getMessage());
        }
        return 1;
    }
}
