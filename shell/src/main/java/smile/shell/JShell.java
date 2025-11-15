/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.shell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jdk.jshell.tool.JavaShellToolBuilder;

/**
 * The JShell launcher.
 *
 * @author Haifeng Li
 */
public interface JShell {
  /**
   * Launch an instance of a Java shell tool.
   * @param args the command-line arguments.
   * @return the exit status with which the tool explicitly exited (if any),
   *         otherwise 0 for success or 1 for failure.
   * @throws Exception an unexpected fatal exception
   */
  static int start(String[] args) throws Exception {
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

    return JavaShellToolBuilder.builder().start(list.toArray(String[]::new));
  }
}
