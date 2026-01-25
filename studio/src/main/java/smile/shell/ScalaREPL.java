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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The JShell launcher.
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
