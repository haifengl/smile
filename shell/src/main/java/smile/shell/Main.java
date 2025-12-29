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

import java.nio.file.Path;
import java.util.Arrays;
import picocli.CommandLine;
import smile.studio.SmileStudio;

/** An object that runs Smile script or interactive shell.
  *
  * @author Haifeng Li
  */
public class Main {
    static void main(String[] args) {
        // Normalize home path
        var home = Path.of(System.getProperty("smile.home", ". ")).normalize();
        System.setProperty("smile.home", home.toString());

        var command = args.length > 0 ? args[0] : "";
        var options = args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : args;
        switch (command) {
            case "train" -> new CommandLine(new Train()).execute(options);
            case "predict" -> new CommandLine(new Predict()).execute(options);
            case "serve" -> Serve.apply(options);
            case "scala" -> ScalaREPL.apply(options);
            case "shell" -> JShell.start(options);
            default -> SmileStudio.start(args);
        }
    }
}
