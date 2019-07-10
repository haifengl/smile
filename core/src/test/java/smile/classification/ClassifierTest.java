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

import smile.math.matrix.JMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.SparseMatrix;

import org.junit.Assert;
import org.junit.Test;

public class ClassifierTest {

    @Test
    public void testPredictDoubleArray() {
        double[][] x = {{0.7220180, 0.07121225, 0.6881997}, {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}};

        DummyClassifier classifier = new DummyClassifier();
        classifier.predict(x);
        Assert.assertEquals(1, classifier.count);
    }

    @Test
    public void testPredictMatrix() {
        double[][] x = {{0.7220180, 0.07121225, 0.6881997}, {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}};

        DummyClassifier classifier = new DummyClassifier();
        classifier.predict(new JMatrix(x));
        classifier.predict((Matrix) new JMatrix(x));
        Assert.assertEquals(2, classifier.count);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPredictSparseMatrix() {
        double[][] x = {{0.7220180, 0.07121225, 0.6881997}, {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}};

        DummyClassifier classifier = new DummyClassifier();
        classifier.predict(new SparseMatrix(x));
        classifier.predict((Matrix) new SparseMatrix(x));

        Assert.assertEquals(-2, classifier.count);
    }

    private static class DummyClassifier implements Classifier<double[]> {

        int count = 0;

        @Override
        public int predict(double[] x) {
            count++;

            return 0;
        }

        @Override
        public int[] predict(SparseMatrix x) {
            count--;

            return null;
        }
    }

}
