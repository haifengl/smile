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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.shell;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import smile.io.Read;
import smile.model.*;
import smile.util.Strings;

/**
 * Batch prediction on a file.
 *
 * @author Haifeng Li
 */
@Command(name = "smile predict", versionProvider = VersionProvider.class,
         description = "Run batch prediction on a file.",
         mixinStandardHelpOptions = true)
public class Predict implements Callable<Integer> {
    @Parameters(index = "0", description = "The data file.")
    private File file;
    @Option(names = {"-m", "--model"}, required = true, paramLabel = "<file>", description = "The model file.")
    private File model;
    @Option(names = {"--format"}, description = "The data file format.")
    private String format;
    @Option(names = {"-p", "--probability"}, description = "Compute posteriori probabilities for soft classifiers.")
    private boolean probability;
    @Option(names = {"-e", "--explain"}, description = "Compute SHAP feature explanations (automatically enables JSON output).")
    private boolean explain;
    @Option(names = {"--json"}, description = "Output predictions as JSON lines.")
    private boolean json;

    @Override
    public Integer call() throws Exception {
        if (explain) {
            json = true;
        }

        var data = Read.data(file.getCanonicalPath(), format);
        var obj = Read.object(model.toPath());
        if (obj instanceof Model m) {
            if (explain && !m.supportsShap()) {
                System.err.println("Error: Model algorithm '" + m.algorithm() + "' does not support SHAP explanations (only tree-based models are currently supported).");
                return 1;
            }

            var predictions = m.infer(data, probability, explain);
            for (var prediction : predictions) {
                if (json) {
                    System.out.println(prediction.toJson());
                } else {
                    System.out.println(prediction.toString());
                }
            }
            return 0;
        } else {
            System.err.println(model.getName() + " doesn't contain a valid model.");
            return 1;
        }
    }
}
