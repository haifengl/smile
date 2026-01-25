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

    @Override
    public Integer call() throws Exception {
        var data = Read.data(file.getCanonicalPath(), format);
        var obj = Read.object(model.toPath());
        if (obj instanceof ClassificationModel box) {
            var classifier = box.classifier();
            if (probability && classifier.isSoft()) {
                var prob = new ArrayList<double[]>();
                var pred = classifier.predict(data, prob);
                for (int i = 0; i < pred.length; i++) {
                    System.out.print(pred[i]);
                    for (double p : prob.get(i)) {
                        System.out.format(" %.4f", p);
                    }
                    System.out.println();
                }
            } else {
                for (var pred : classifier.predict(data)) {
                    System.out.println(pred);
                }
            }
        } else if (obj instanceof RegressionModel box) {
            var regression = box.regression();
            for (var pred : regression.predict(data)) {
                System.out.println(Strings.format(pred));
            }
        } else {
            System.err.println(model.getName() + " doesn't contain a valid model.");
            return 1;
        }

        return 0;
    }
}
