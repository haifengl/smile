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
