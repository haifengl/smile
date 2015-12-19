/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.classification;

import smile.sort.QuickSort;
import smile.data.Attribute;
import smile.math.Math;
import smile.validation.LOOCV;
import smile.data.parser.ArffParser;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;

/**
 *
 * @author Haifeng Li
 */
public class Benchmark {

    public Benchmark() {
    }

    public static void main(String[] args) {
        System.out.println("Random Forest benchmark");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setDelimiter(",");
        parser.setColumnNames(true);
        parser.setResponseIndex(new NominalAttribute("class"), 8);
        try {
            AttributeDataset train = parser.parse("Benchmark Train", this.getClass().getResourceAsStream("/smile/data/benchm-ml/train-1m.csv"));
            AttributeDataset test = parser.parse("Benchmark Test", this.getClass().getResourceAsStream("/smile/data/benchm-ml/test.csv"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);

            long start = System.currentTimeMillis();
            RandomForest forest = new RandomForest(x, y, 100);
            long end = System.currentTimeMillis();
            System.out.format("Random forest 100 trees training time: %.2f%% s\n", (end-start)/1000.0);

            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (forest.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("Benchmark OOB error rate = %.2f%%\n", 100.0 * forest.error());
            System.out.format("Benchmark error rate = %.2f%%\n", 100.0 * error / testx.length);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
